package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.message.contextCatalogEntries
import com.example.dsh.message.contextInstructions
import com.example.dsh.message.contextRecalls
import com.example.dsh.message.contextRelaySender
import com.example.dsh.message.contextSections
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.session.DshSessionCacheState
import com.example.dsh.models.DshToolCardType
import com.example.dsh.message.DshWebTimelineItem
import com.example.dsh.message.dshHistoryTailToResume
import com.example.dsh.host.dshIsTransportInterrupt
import com.example.dsh.message.dshMessagesVisuallyEqual
import com.example.dsh.message.dshSyncMessageForkAnchor
import com.example.dsh.message.isRuntimeContextSnapshot
import com.example.dsh.tool.toRemoteMessage
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.example.dsh.base.setTimeout
import kotlinx.coroutines.launch
import com.example.dsh.host.toolCardType
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout

internal fun DshHomePage.loadHistory(
    sessionId: String,
    scrollToEndAfterLoad: Boolean = true,
) {
    ++historyRequestGeneration

    // Show the selected session immediately. The Host history request is
    // remote and can take a moment, so keeping the previous list here
    // makes a session switch look stuck.
    ui.messages = sessionMessageState(
        sessionId,
        scrollToEndAfterLoad = scrollToEndAfterLoad,
    )
    ensureConversationPanel(sessionId)
    fetchHostHistory(sessionId, scrollToEndAfterLoad)
}

internal fun DshHomePage.fetchHostHistory(
    sessionId: String,
    scrollToEndAfterLoad: Boolean = true,
) {
    if (isRemoteHost) {
        loadSkills(sessionId)
        loadWebTimeline(sessionId, scrollToEndAfterLoad)
        return
    }

    val requestGeneration = historyRequestGeneration
    val hostRepository = repository ?: return
    hostRepository.loadHistory(sessionId, { loaded ->
        if (requestGeneration != historyRequestGeneration || ui.activeSessionId != sessionId) return@loadHistory
        sessionMessageReady.add(sessionId)
        sessionCacheStates[sessionId] = DshSessionCacheState.SYNCED
        replaceMessagesIfChanged(loaded)
        runCatching { localStore?.replaceMessages(activeConnectionId, sessionId, loaded) }
        completePendingSessionSelection(sessionId)
        realizeSessionAfterData(sessionId, scrollToEndAfterLoad)
    }, { error ->
        if (requestGeneration != historyRequestGeneration || ui.activeSessionId != sessionId) return@loadHistory
        if (ui.messages.isNotEmpty()) {
            if (isRemoteHost) {
                sessionCacheStates[sessionId] = DshSessionCacheState.SYNC_FAILED
                connectionLabel = "远程历史同步失败 · 已显示缓存"
            } else {
                connectionLabel = "内核连接失败 · 已显示缓存"
            }
        } else {
            ui.messages.add(DshMessage("history-error", DshMessageRole.ERROR, error))
        }
    })
}

internal fun DshHomePage.loadWebTimeline(
    sessionId: String,
    scrollToEndAfterLoad: Boolean = true,
    forceReplace: Boolean = false,
    afterApply: () -> Unit = {},
) {
    val hostRepository = remoteRepo ?: return
    val requestVersion = ++timelineReadVersion
    val scopeId = activeConnectionId
    fun current() = pageAlive && repository === hostRepository && activeConnectionId == scopeId &&
        ui.activeSessionId == sessionId && requestVersion == timelineReadVersion
    hostRepository.loadWebTimeline(sessionId, { items ->
        if (!current()) return@loadWebTimeline
        // 先收集所有 attachmentIds，加载 dataUrl 后再创建消息（vforLazy 不响应列表变化，必须在消息入列时就有 imagePreviews）
        val allAttIds = items.flatMap { item ->
            when (item.kind) {
                DshWebTimelineItem.Kind.USER -> item.attachmentIds
                DshWebTimelineItem.Kind.IMAGE -> listOfNotNull(item.attachmentId)
                else -> emptyList()
            }
        }.distinct()
        val pendingAttIds = allAttIds.filter { cachedAttachmentDataUrls[it] == null && pendingAttachmentReads.add(it) }
        val applyTimeline = apply@{
            if (!current()) return@apply
            val projected = items.map { item ->
            when (item.kind) {
                DshWebTimelineItem.Kind.USER -> {
                    val loadedPreviews = item.attachmentIds.mapNotNull { cachedAttachmentDataUrls[it] }
                    DshMessage(item.key, DshMessageRole.USER, item.text, attachmentIds = item.attachmentIds, imagePreviews = loadedPreviews)
                }
                DshWebTimelineItem.Kind.ASSISTANT -> DshMessage(item.key, DshMessageRole.ASSISTANT, item.text)
                DshWebTimelineItem.Kind.REASONING -> DshMessage(
                    item.key,
                    DshMessageRole.ASSISTANT,
                    item.text,
                    isReasoning = true,
                )
                DshWebTimelineItem.Kind.IMAGE -> DshMessage(
                    item.key,
                    DshMessageRole.ASSISTANT,
                    "",
                    attachmentId = item.attachmentId,
                    imagePreviews = item.attachmentId?.let { cachedAttachmentDataUrls[it] }?.let { listOf(it) } ?: item.imagePreviews,
                )
                DshWebTimelineItem.Kind.UNKNOWN_BLOCK -> DshMessage(
                    item.key,
                    DshMessageRole.TOOL,
                    item.text,
                    toolName = "未知内容块",
                    toolCardType = DshToolCardType.JSON,
                )
                DshWebTimelineItem.Kind.ERROR -> DshMessage(item.key, DshMessageRole.ERROR, item.text)
                DshWebTimelineItem.Kind.CONTEXT -> DshMessage(
                    item.key,
                    DshMessageRole.TOOL,
                    item.text,
                    toolName = item.sourceLabel,
                    isContextInjection = true,
                    contextBody = item.text,
                    contextForm = item.source?.optString("form").orEmpty(),
                    contextCatalog = item.source?.let(::contextCatalogEntries).orEmpty(),
                    contextSections = item.source?.let(::contextSections).orEmpty(),
                    contextRecalls = item.source?.let(::contextRecalls).orEmpty(),
                    contextInstructions = item.source?.let(::contextInstructions).orEmpty(),
                    contextRelaySender = item.source?.let(::contextRelaySender).orEmpty(),
                )
                DshWebTimelineItem.Kind.TOOL -> item.remoteTool?.toRemoteMessage(item.key) ?: DshMessage(
                    item.key,
                    DshMessageRole.TOOL,
                    item.cardBody.ifEmpty { listOfNotNull(item.input, item.output).joinToString("\n\n") },
                    toolName = item.cardTitle.ifEmpty { item.toolName ?: "工具" },
                    toolCardType = item.cardType,
                    toolRunning = item.running,
                    toolError = item.error != null,
                    toolStopped = item.stopped,
                )
            }.copy(readableContent = item.readableContent, sourceSeq = item.sourceSeq)
            }
            sessionMessageReady.add(sessionId)
            replaceMessagesIfChanged(projected, force = forceReplace && !isLocalPromptInFlight())
            if (projected.isNotEmpty()) {
                persistMessages(sessionId)
                sessionCacheStates[sessionId] = DshSessionCacheState.SYNCED
            }
            completePendingSessionSelection(sessionId)
            realizeSessionAfterData(sessionId, scrollToEndAfterLoad)
            afterApply()
        }
        if (pendingAttIds.isEmpty()) {
            applyTimeline()
        } else {
            var remaining = pendingAttIds.size
            var applied = false
            val tryApply = {
                if (!applied && current()) {
                    applied = true
                    ui.attachmentRevision += 1
                    applyTimeline()
                }
            }
            pendingAttIds.forEach { attId ->
                hostRepository.loadAttachment(sessionId, attId) { dataUrl, error ->
                    if (!current()) return@loadAttachment
                    if (error == null && dataUrl != null) {
                        cachedAttachmentDataUrls[attId] = dataUrl
                    }
                    pendingAttachmentReads.remove(attId)
                    remaining -= 1
                    if (remaining <= 0) tryApply()
                }
            }
            // 兜底超时：15 秒后即使有回调丢失也强制 apply，避免会话一直空白
            setTimeout(pagerId, 15000) {
                if (!applied && ui.activeSessionId == sessionId) {
                    DshStreamLog.log(LogLevel.WARN, "ui.att-timeout", "attachment preload timeout, force apply session=$sessionId remaining=$remaining", sessionId, null)
                    tryApply()
                }
            }
        }
    }, { error ->
        DshStreamLog.w("ui.history-fail session=$sessionId error='${DshStreamLog.preview(error)}'")
        if (!current()) return@loadWebTimeline
        sessionCacheStates[sessionId] = DshSessionCacheState.SYNC_FAILED
        bridgeModule.toast("历史读取失败：$error，可重新进入会话重试")
        if (forceReplace && !ui.sessionRunning && (ui.streaming || ui.stopButtonVisible)) {
            finishStreamingFromHistory(sessionId)
        }
        afterApply()
    }, ::current)
}

internal fun DshHomePage.resyncStreamingWithHost(sessionId: String, reason: String) {
    if (!isRemoteHost || sessionId != ui.activeSessionId) return
    // A local prompt is already painting this turn. Reloading the web
    // timeline remounts every markdown bubble and delays the first token.
    if (reason == "host-session-running" && isLocalPromptInFlight()) {
        return
    }
    DshStreamLog.i("ui.resync.begin reason=$reason session=$sessionId running=$ui.sessionRunning")
    if (ui.sessionRunning) {
        loadWebTimeline(sessionId, scrollToEndAfterLoad = true, forceReplace = true) {
            resumeStreamingFromHistory(sessionId, reason)
        }
    } else {
        val forceReplace = ui.streaming || ui.stopButtonVisible
        loadWebTimeline(sessionId, scrollToEndAfterLoad = true, forceReplace = forceReplace) {
            finishStreamingFromHistory(sessionId)
            connectionLabel = "已连接"
            DshStreamLog.i("ui.resync.settled reason=$reason session=$sessionId ui.messages=${ui.messages.size}")
        }
    }
}

internal fun DshHomePage.isLocalPromptInFlight(): Boolean =
    ui.streaming && streamingAssistantRootId.isNotEmpty()

internal fun DshHomePage.rebindStreamingToHistoryTail(): Boolean {
    val live = dshHistoryTailToResume(ui.messages.toList(), streamingTurnAnchorAssistantId)
        ?: return false
    ui.streamingAssistantId = live.id
    streamingAssistantRootId = live.id
    streamingAssistantSegment = 0
    streamingSourceSeq = live.sourceSeq
    ui.streamingAssistantContent = live.content
    return true
}

internal fun DshHomePage.finishStreamingFromHistory(sessionId: String) {
    if (!(ui.streaming || ui.stopButtonVisible)) return
    flushAssistantDelta()
    if (rebindStreamingToHistoryTail()) {
        settleStreamingMessage(DshMessageRole.ASSISTANT, ui.streamingAssistantContent)
    } else {
        releaseStreamingUi()
    }
    persistMessages(sessionId)
    (remoteRepo)?.detachLiveStreams(sessionId)
    streamHandle = null
}

internal fun DshHomePage.resumeStreamingFromHistory(sessionId: String, reason: String) {
    if (sessionId != ui.activeSessionId) return
    val rebound = rebindStreamingToHistoryTail()
    if (rebound) {
        ui.streaming = true
        ui.stopButtonVisible = true
        connectionLabel = "正在生成"
        val index = ui.messages.indexOfFirst { it.id == ui.streamingAssistantId }
        if (index >= 0) {
            ui.messages[index] = ui.messages[index].copy(streaming = true)
        }
    } else {
        if (streamingAssistantRootId.isEmpty()) {
            streamingAssistantRootId = "assistant-adopted-${ui.messages.size}"
        }
        val liveStillPresent = ui.streamingAssistantId.isNotEmpty() &&
            ui.messages.any { it.id == ui.streamingAssistantId }
        if (!liveStillPresent) {
            val kept = ui.streamingAssistantContent + pendingAssistantDelta.toString()
            pendingAssistantDelta.setLength(0)
            ui.streamingAssistantId = ""
            streamingAssistantSegment = 0
            ui.streamingAssistantContent = ""
            if (kept.isNotEmpty()) {
                ensureStreamingAssistantSegment()
                ui.streamingAssistantContent = kept
                updateStreamingMessage(kept, streaming = true)
            }
        }
        ui.streaming = true
        ui.stopButtonVisible = true
        connectionLabel = "正在生成"
    }
    attachAdoptedLiveStream(sessionId)
    syncTurnStatusTicker()
    DshStreamLog.i(
        "ui.resync.resume reason=$reason rebound=$rebound id=${ui.streamingAssistantId.ifEmpty { streamingAssistantRootId }} chars=${ui.streamingAssistantContent.length}",
    )
}

internal fun DshHomePage.attachAdoptedLiveStream(sessionId: String) {
    val hostRepository = remoteRepo ?: return
    streamHandle = hostRepository.adoptLiveStream(
        sessionId = sessionId,
        onDelta = { delta, isReasoning ->
            if (!connectionCoordinator.isActive(ui.connectionMode) || ui.activeSessionId != sessionId) return@adoptLiveStream
            if (isReasoning) {
                val reasoningId = streamingReasoningId.ifEmpty { "$streamingAssistantRootId-reasoning" }
                if (streamingReasoningId.isEmpty()) streamingReasoningId = reasoningId
                queueReasoningDelta(reasoningId, delta)
            } else {
                if (streamingAssistantRootId.isEmpty()) {
                    streamingAssistantRootId = "assistant-adopted-${ui.messages.size}"
                }
                queueAssistantDelta(streamingAssistantRootId, delta)
            }
        },
        onComplete = { result ->
            if (!connectionCoordinator.isActive(ui.connectionMode)) return@adoptLiveStream
            flushAssistantDelta()
            if (ui.streamingAssistantId.isEmpty() && result.isNotEmpty()) {
                ensureStreamingAssistantSegment()
            }
            val completedContent = ui.streamingAssistantContent.ifEmpty { result }
            settleStreamingMessage(DshMessageRole.ASSISTANT, completedContent)
            persistMessages(sessionId)
            connectionLabel = "已连接"
            streamHandle = null
        },
        onError = { error ->
            if (!connectionCoordinator.isActive(ui.connectionMode)) return@adoptLiveStream
            if (dshIsTransportInterrupt("", error)) {
                DshStreamLog.i("ui.adopt-interrupt session=$sessionId message='${DshStreamLog.preview(error)}'")
                return@adoptLiveStream
            }
            flushAssistantDelta()
            ensureStreamingAssistantSegment()
            DshStreamLog.log(LogLevel.ERROR, "error", "ui.error session=$sessionId message='${DshStreamLog.preview(error)}'", sessionId, null)
            settleStreamingMessage(DshMessageRole.ERROR, error)
            persistMessages(sessionId)
            connectionLabel = "已连接"
            streamHandle = null
        },
    )
}

internal fun DshHomePage.loadSkills(sessionId: String) {
    if (!isRemoteHost) {
        ui.skills.clear()
        return
    }
    val remote = remoteRepo ?: return
    ui.skills.clear()
    remote.loadSkills(sessionId, onSuccess = { loaded ->
        if (!isRemoteHost || ui.activeSessionId != sessionId) return@loadSkills
        ui.skills.clear()
        ui.skills.addAll(loaded)
    })
}

internal fun DshHomePage.loadAttachment(sessionId: String, attachmentId: String) {
    if (attachmentDataUrl(attachmentId) != null || !pendingAttachmentReads.add(attachmentId)) return
    val hostRepository = remoteRepo ?: return
    hostRepository.loadAttachment(sessionId, attachmentId) { dataUrl, error ->
        if (error != null || dataUrl == null) {
            pendingAttachmentReads.remove(attachmentId)
            return@loadAttachment
        }
        cachedAttachmentDataUrls[attachmentId] = dataUrl
        ui.attachmentRevision += 1
    }
}

internal fun DshHomePage.restoreCachedSessions() {
    val store = localStore ?: return
    val cached = runCatching { store.loadSessions(activeConnectionId) }.getOrDefault(emptyList())
    if (cached.isEmpty()) return
    ui.sessions = cached.toList()
    refreshVisibleSessions()
    val homeId = cached.firstOrNull { it.blank }?.id
    if (homeId != null) {
        ui.activeSessionId = homeId
        val state = sessionMessageStates[homeId] ?: ObservableList()
        state.clear()
        sessionMessageStates[homeId] = state
        sessionMessageReady.add(homeId)
        ui.messages = state
        ensureConversationPanel(homeId)
        return
    }
    val state = ObservableList<DshMessage>()
    ui.messages = state
    sessionMessageStates[ui.activeSessionId] = state
    sessionMessageReady.add(ui.activeSessionId)
    ensureConversationPanel(ui.activeSessionId)
}

internal fun DshHomePage.loadCachedHistory(sessionId: String) {
    ui.messages = sessionMessageState(sessionId, loadFromDisk = false)
    ensureConversationPanel(sessionId)
    loadMessagesFromDisk(sessionId)
}

internal fun DshHomePage.sessionMessageState(
    sessionId: String,
    loadFromDisk: Boolean = true,
    scrollToEndAfterLoad: Boolean = true,
): ObservableList<DshMessage> {
    sessionMessageStates[sessionId]?.let { return it }
    val state = ObservableList<DshMessage>()
    sessionMessageStates[sessionId] = state
    if (loadFromDisk) loadMessagesFromDisk(sessionId, scrollToEndAfterLoad)
    return state
}

/**
 * Warm every known conversation after the session index is available.
 * Reads are serialized through one background coroutine because the local
 * SQLite driver is shared by the page and should not be queried concurrently.
 */

internal fun DshHomePage.preloadAllSessionMessages() {
    val sessionIds = ui.sessions.toList().map { it.id }
    // Load data first. Do not mount empty ListViews: LazyLoop initializes
    // its visible range from the initial list and may not realize the
    // first items when the list is populated later.
    sessionIds.forEach { sessionMessageState(it, loadFromDisk = false) }
    val store = localStore ?: run {
        sessionIds.forEach {
            sessionMessageReady.add(it)
            completePendingSessionSelection(it)
        }
        return
    }
    val pending = sessionIds
        .filterNot { sessionMessageReady.contains(it) }
        .filter { pendingLocalMessageReads.add(it) }
    if (pending.isEmpty()) {
        return
    }
    localReadScope.launch {
        pending.forEach { sessionId ->
            val loaded = runCatching { store.loadMessages(activeConnectionId, sessionId) }
                .getOrDefault(emptyList())
                .filterNot { it.isRuntimeContextSnapshot() }
            postToUi {
                pendingLocalMessageReads.remove(sessionId)
                val state = sessionMessageStates[sessionId] ?: return@postToUi
                sessionMessageReady.add(sessionId)
                if (state.isEmpty() && loaded.isNotEmpty() &&
                    ui.sessions.firstOrNull { it.id == sessionId }?.blank != true
                ) {
                    state.addAll(loaded)
                    remountConversationList(sessionId)
                }
                if (ui.conversationPanelIds.size < CONVERSATION_PANEL_CACHE_LIMIT) {
                    ensureConversationPanel(sessionId)
                }
                realizeSessionAfterData(sessionId, scrollToEndAfterLoad = false)
                completePendingSessionSelection(sessionId)
            }
        }
    }
}

internal fun DshHomePage.loadMessagesFromDisk(
    sessionId: String,
    scrollToEndAfterLoad: Boolean = true,
) {
    if (localStore == null || !pendingLocalMessageReads.add(sessionId)) return
    localReadScope.launch {
        val loaded = runCatching { localStore?.loadMessages(activeConnectionId, sessionId).orEmpty() }
            .getOrDefault(emptyList())
            .filterNot { it.isRuntimeContextSnapshot() }
        postToUi {
            pendingLocalMessageReads.remove(sessionId)
            val state = sessionMessageStates[sessionId] ?: return@postToUi
            sessionMessageReady.add(sessionId)
            // A remote history response or a new local prompt wins over
            // a disk snapshot that finishes later. The state is keyed by
            // session ID, so an inactive session can be updated safely.
            if (state.isEmpty() && loaded.isNotEmpty() &&
                ui.sessions.firstOrNull { it.id == sessionId }?.blank != true
            ) {
                state.addAll(loaded)
                remountConversationList(sessionId)
            }
            ensureConversationPanel(sessionId)
            realizeSessionAfterData(sessionId, scrollToEndAfterLoad)
            completePendingSessionSelection(sessionId)
        }
    }
}

internal fun DshHomePage.completePendingSessionSelection(sessionId: String) {
    if (!pendingSessionSelections.remove(sessionId)) return
    postToUi {
        if (ui.activeSessionId != sessionId) selectSession(sessionId)
    }
}

internal fun DshHomePage.warmRecentSessionCache(
    sessionIds: kotlin.collections.List<String> = ui.sessions.asSequence()
        .map { it.id }
        .filter { it != ui.activeSessionId && !ui.conversationPanelIds.contains(it) }
        .take(SESSION_CACHE_WARM_LIMIT)
        .toList(),
    index: Int = 0,
    scrollToEndAfterLoad: Boolean = true,
) {
    if (index >= sessionIds.size) return
    sessionMessageState(
        sessionIds[index],
        loadFromDisk = true,
        scrollToEndAfterLoad = scrollToEndAfterLoad,
    )
    if (sessionMessageReady.contains(sessionIds[index])) {
        ensureConversationPanel(sessionIds[index])
    }
    setTimeout(pagerId, SESSION_CACHE_WARM_INTERVAL_MS) {
        warmRecentSessionCache(sessionIds, index + 1, scrollToEndAfterLoad)
    }
}

internal fun DshHomePage.ensureConversationPanel(sessionId: String) {
    if (ui.conversationPanelIds.contains(sessionId)) return
    try {
        if (ui.conversationPanelIds.size >= CONVERSATION_PANEL_CACHE_LIMIT) {
            val evictIndex = ui.conversationPanelIds.indexOfFirst { it != ui.activeSessionId }
            if (evictIndex >= 0) {
                val evictedId = ui.conversationPanelIds.removeAt(evictIndex)
                messageScrollerRefs.remove(evictedId)
            }
        }
        ui.conversationPanelIds.add(sessionId)
    } catch (e: ConcurrentModificationException) {
        // 渲染该列表期间新增 panel 会触发 Kuikly 对同一响应式列表的自注册，
        // 迭代中修改被绑定的 observers 集合导致 CME。此时元素通常已入列，
        // 下一消息幂等兜底收敛，避免拖垮整个页面。
        postToUi {
            runCatching {
                if (!ui.conversationPanelIds.contains(sessionId) &&
                    ui.conversationPanelIds.size < CONVERSATION_PANEL_CACHE_LIMIT
                ) {
                    ui.conversationPanelIds.add(sessionId)
                }
            }
        }
    }
}

internal fun DshHomePage.persistMessages(sessionId: String) {
    val snapshot = ui.messages.toList()
    sessionMessageStates[sessionId] = ui.messages
    runCatching { localStore?.replaceMessages(activeConnectionId, sessionId, snapshot) }
}

internal fun DshHomePage.replaceMessagesIfChanged(next: List<DshMessage>, force: Boolean = false) {
    val filtered = next.filterNot { it.isRuntimeContextSnapshot() }
    if (ui.streaming && isRemoteHost && !force) {
        // History is a snapshot that can arrive while the current turn is
        // still being projected. Replacing the observable list here drops
        // optimistic text segments and their in-order tool cards.
        return
    }
    val current = ui.messages.toList()
    if (current == filtered) return
    if (dshMessagesVisuallyEqual(current, filtered)) {
        // Preserve stable UI ids, but still hydrate anchors in pre-upgrade/live caches.
        for (index in current.indices) {
            val synced = dshSyncMessageForkAnchor(current[index], filtered[index])
            if (synced != current[index]) ui.messages[index] = synced
        }
        return
    }
    val remount = force || current.isEmpty() && filtered.isNotEmpty()
    applyMessagesInPlace(filtered)
    sessionMessageStates[ui.activeSessionId] = ui.messages
    if (remount) remountConversationList(ui.activeSessionId)
}

internal fun DshHomePage.applyMessagesInPlace(next: List<DshMessage>) {
    val shared = minOf(ui.messages.size, next.size)
    for (index in 0 until shared) {
        if (ui.messages[index] != next[index]) ui.messages[index] = next[index]
    }
    when {
        next.size < ui.messages.size -> {
            for (index in ui.messages.lastIndex downTo next.size) {
                ui.messages.removeAt(index)
            }
        }
        next.size > ui.messages.size -> {
            ui.messages.addAll(next.subList(ui.messages.size, next.size))
        }
    }
}
