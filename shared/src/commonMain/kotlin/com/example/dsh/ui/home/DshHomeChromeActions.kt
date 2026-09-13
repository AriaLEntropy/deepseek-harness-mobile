package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.ui.chat.TURN_STATUS_CLOCK_AFTER_MS
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.example.dsh.base.setTimeout
import kotlin.time.TimeSource
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout

internal fun DshHomePage.onConnectionLabelChanged(label: String) {
    if (label == "正在生成" || label == "正在聆听") {
        if (connectionCapsuleVisible) {
            connectionCapsuleVisible = false
            connectionCapsuleFadeOut = false
        }
        return
    }
    if (!isConnectionReadyLabel(label)) {
        connectionCapsuleVisible = true
        connectionCapsuleFadeOut = false
    } else if (connectionCapsuleVisible) {
        connectionCapsuleVersion++
        val version = connectionCapsuleVersion
        setTimeout(pagerId, DshHomePage.CONNECTION_CAPSULE_HOLD_MS) {
            if (version == connectionCapsuleVersion && isConnectionReadyLabel(connectionLabel)) {
                connectionCapsuleFadeOut = true
                setTimeout(pagerId, DshHomePage.CONNECTION_CAPSULE_FADE_MS) {
                    if (version == connectionCapsuleVersion) {
                        connectionCapsuleVisible = false
                        connectionCapsuleFadeOut = false
                    }
                }
            }
        }
    }
}

internal fun DshHomePage.refreshPendingSessionIds() {
    val remote = remoteRepo
    pendingSessionIds = sessions.filter { session ->
        val pending = remote?.pendingInteractions(session.id)
        pending?.first != null || pending?.second != null
    }.map { it.id }.toSet()
}

internal fun DshHomePage.isWebDisclosureExpanded(id: String): Boolean {
    webDisclosureRevision
    return webDisclosureStates[id] == true
}

internal fun DshHomePage.toggleWebDisclosure(id: String) {
    val next = webDisclosureStates[id] != true
    webDisclosureStates[id] = next
    if (!next) {
        webJsonNodeStates.keys.filter { it.startsWith("$id:") }.toList().forEach(webJsonNodeStates::remove)
    }
    webDisclosureRevision += 1
    refreshSessionRenderTree(activeSessionId)
}

internal fun DshHomePage.isWebJsonNodeExpanded(messageId: String, nodeId: String): Boolean {
    webDisclosureRevision
    return webJsonNodeStates["$messageId:$nodeId"] == true
}

internal fun DshHomePage.toggleWebJsonNode(messageId: String, nodeId: String) {
    val key = "$messageId:$nodeId"
    webJsonNodeStates[key] = webJsonNodeStates[key] != true
    webDisclosureRevision += 1
    refreshSessionRenderTree(activeSessionId)
}

internal fun DshHomePage.isBlankSession(sessionId: String = activeSessionId): Boolean =
    sessions.firstOrNull { it.id == sessionId }?.blank == true

internal fun DshHomePage.conversationListEpochFor(sessionId: String): Int {
    conversationListEpoch
    return conversationListEpochs[sessionId] ?: 0
}

internal fun DshHomePage.remountConversationList(sessionId: String) {
    conversationListEpochs[sessionId] = (conversationListEpochs[sessionId] ?: 0) + 1
    conversationListEpoch += 1
}

internal fun DshHomePage.applyActiveSessionChrome() {
    pendingApproval = null
    pendingQuestion = null
    selectedQuestionOptions.clear()
    questionCustom = ""
    questionIndex = 0
    questionError = ""
    questionDrafts.clear()
    goalSnapshot = null
    if (!isRemoteHost) {
        queueItems = ObservableList()
        jobItems = ObservableList()
        liveJobItems = ObservableList()
        return
    }
    refreshQueueDock()
    refreshJobsPanel()
    refreshPendingInteractions()
}

internal fun DshHomePage.isTurnStatusActive(): Boolean =
    streaming || stopButtonVisible || sessionRunning

internal fun DshHomePage.syncTurnStatusTicker() {
    if (!isTurnStatusActive()) {
        turnStatusTickerGeneration += 1
        turnStatusMark = null
        turnElapsedMs = 0
        turnStatusClockBucket = -1L
        return
    }
    if (turnStatusMark == null) {
        turnStatusMark = TimeSource.Monotonic.markNow()
    }
    val token = ++turnStatusTickerGeneration
    fun tick() {
        if (token != turnStatusTickerGeneration) return
        if (!isTurnStatusActive()) {
            turnStatusMark = null
            turnElapsedMs = 0
            turnStatusClockBucket = -1L
            return
        }
        val elapsed = turnStatusMark?.elapsedNow()?.inWholeMilliseconds ?: 0L
        val showClock = elapsed >= TURN_STATUS_CLOCK_AFTER_MS
        val clockBucket = if (showClock) elapsed / 1_000L else 0L
        if (clockBucket != turnStatusClockBucket) {
            turnStatusClockBucket = clockBucket
            turnElapsedMs = elapsed
        }
        val wait = if (showClock) 1_000L else (TURN_STATUS_CLOCK_AFTER_MS - elapsed).coerceAtLeast(200L)
        setTimeout(pagerId, wait.toInt()) { tick() }
    }
    tick()
}
