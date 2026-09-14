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
        if (ui.connectionCapsuleVisible) {
            ui.connectionCapsuleVisible = false
            ui.connectionCapsuleFadeOut = false
        }
        return
    }
    if (!isConnectionReadyLabel(label)) {
        ui.connectionCapsuleVisible = true
        ui.connectionCapsuleFadeOut = false
    } else if (ui.connectionCapsuleVisible) {
        connectionCapsuleVersion++
        val version = connectionCapsuleVersion
        setTimeout(pagerId, DshHomePage.CONNECTION_CAPSULE_HOLD_MS) {
            if (version == connectionCapsuleVersion && isConnectionReadyLabel(connectionLabel)) {
                ui.connectionCapsuleFadeOut = true
                setTimeout(pagerId, DshHomePage.CONNECTION_CAPSULE_FADE_MS) {
                    if (version == connectionCapsuleVersion) {
                        ui.connectionCapsuleVisible = false
                        ui.connectionCapsuleFadeOut = false
                    }
                }
            }
        }
    }
}

internal fun DshHomePage.refreshPendingSessionIds() {
    val remote = remoteRepo
    ui.pendingSessionIds = ui.sessions.filter { session ->
        val pending = remote?.pendingInteractions(session.id)
        pending?.first != null || pending?.second != null
    }.map { it.id }.toSet()
}

internal fun DshHomePage.isWebDisclosureExpanded(id: String): Boolean {
    ui.webDisclosureRevision
    return webDisclosureStates[id] == true
}

internal fun DshHomePage.toggleWebDisclosure(id: String) {
    val next = webDisclosureStates[id] != true
    webDisclosureStates[id] = next
    if (!next) {
        webJsonNodeStates.keys.filter { it.startsWith("$id:") }.toList().forEach(webJsonNodeStates::remove)
    }
    ui.webDisclosureRevision += 1
    refreshSessionRenderTree(ui.activeSessionId)
}

internal fun DshHomePage.isWebJsonNodeExpanded(messageId: String, nodeId: String): Boolean {
    ui.webDisclosureRevision
    return webJsonNodeStates["$messageId:$nodeId"] == true
}

internal fun DshHomePage.toggleWebJsonNode(messageId: String, nodeId: String) {
    val key = "$messageId:$nodeId"
    webJsonNodeStates[key] = webJsonNodeStates[key] != true
    ui.webDisclosureRevision += 1
    refreshSessionRenderTree(ui.activeSessionId)
}

internal fun DshHomePage.isBlankSession(sessionId: String = ui.activeSessionId): Boolean =
    ui.sessions.firstOrNull { it.id == sessionId }?.blank == true

internal fun DshHomePage.conversationListEpochFor(sessionId: String): Int {
    ui.conversationListEpoch
    return conversationListEpochs[sessionId] ?: 0
}

internal fun DshHomePage.remountConversationList(sessionId: String) {
    conversationListEpochs[sessionId] = (conversationListEpochs[sessionId] ?: 0) + 1
    ui.conversationListEpoch += 1
}

internal fun DshHomePage.applyActiveSessionChrome() {
    ui.pendingApproval = null
    ui.pendingQuestion = null
    ui.selectedQuestionOptions.clear()
    ui.questionCustom = ""
    ui.questionIndex = 0
    ui.questionError = ""
    questionDrafts.clear()
    ui.goalSnapshot = null
    if (!isRemoteHost) {
        ui.queueItems = ObservableList()
        ui.jobItems = ObservableList()
        ui.liveJobItems = ObservableList()
        return
    }
    refreshQueueDock()
    refreshJobsPanel()
    refreshPendingInteractions()
}

internal fun DshHomePage.isTurnStatusActive(): Boolean =
    ui.streaming || ui.stopButtonVisible || ui.sessionRunning

internal fun DshHomePage.syncTurnStatusTicker() {
    if (!isTurnStatusActive()) {
        turnStatusTickerGeneration += 1
        turnStatusMark = null
        ui.turnElapsedMs = 0
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
            ui.turnElapsedMs = 0
            turnStatusClockBucket = -1L
            return
        }
        val elapsed = turnStatusMark?.elapsedNow()?.inWholeMilliseconds ?: 0L
        val showClock = elapsed >= TURN_STATUS_CLOCK_AFTER_MS
        val clockBucket = if (showClock) elapsed / 1_000L else 0L
        if (clockBucket != turnStatusClockBucket) {
            turnStatusClockBucket = clockBucket
            ui.turnElapsedMs = elapsed
        }
        val wait = if (showClock) 1_000L else (TURN_STATUS_CLOCK_AFTER_MS - elapsed).coerceAtLeast(200L)
        setTimeout(pagerId, wait.toInt()) { tick() }
    }
    tick()
}
