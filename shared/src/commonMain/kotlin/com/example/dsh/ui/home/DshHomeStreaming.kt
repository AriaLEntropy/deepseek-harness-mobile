package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.ui.message.DshMessageRow
import com.example.dsh.attachment.DshFileDraftState
import com.example.dsh.attachment.DshImageDraftState
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.attachment.DshPendingFile
import com.example.dsh.attachment.DshPendingImage
import com.example.dsh.host.DshRemoteRepository
import com.example.dsh.session.DshSession
import com.example.dsh.message.dshIsLiveAssistantText
import com.example.dsh.host.dshIsTransportInterrupt
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.example.dsh.ui.rendering.DshMarkdown
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.scrollToPosition
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.example.dsh.base.setTimeout
import kotlinx.coroutines.cancel
import com.example.dsh.attachment.composePromptWithFiles
import com.example.dsh.message.messageRowKey
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout

internal fun DshHomePage.sendDraft() {
    if (sendInFlight) return
    dismissKeyboard()
    val prompt = draft.trim()
    val sendableImages = pendingImages.filter { it.state != DshImageDraftState.INVALID && it.dataBase64.isNotEmpty() }
    val sendableFiles = pendingFiles.filter { it.state != DshFileDraftState.FAILED && it.dataBase64.isNotEmpty() }
    if ((prompt.isEmpty() && sendableImages.isEmpty() && sendableFiles.isEmpty()) || streaming) return
    val hostRepository = remoteRepo
    if (hostRepository == null) {
        connectionLabel = "本地内核尚未连接"
        messages.add(DshMessage(
            "send-engine-error-${messages.size}",
            DshMessageRole.ERROR,
            "本地 Harness 尚未连接，请稍候再试。",
        ))
        return
    }
    if (!hostRepository.isProductReady()) {
        connectionLabel = syncBusyLabel()
        return
    }
    if (sessions.isEmpty() || activeSessionId.isEmpty()) {
        connectionLabel = "正在创建会话"
        hostRepository.createSession(null, { sessionId ->
            sessions = sessions + DshSession(sessionId, "新会话", "Host", "", blank = true, permission = permissionValue, agentPreset = agentModeValue)

            touchSessionActivity(sessionId)
            activeSessionId = sessionId
            loadModels(sessionId)
            sendDraft()
        }, { error ->
            connectionLabel = "会话创建失败"
            messages.add(DshMessage(
                "send-session-error-${messages.size}",
                DshMessageRole.ERROR,
                "无法创建会话：$error",
            ))
        }, permission = permissionValue, agentPreset = agentModeValue)
        return
    }
    val sessionId = activeSessionId
    // 旧 Host 无文件 content 类型：先把未落盘文件上传到会话工作目录，成功后再带上 handle 发送。
    val filesNeedingUpload = sendableFiles.filter { it.handle.isEmpty() }
    if (filesNeedingUpload.isNotEmpty()) {
        if (sendInFlight) return
        sendInFlight = true
        uploadPendingFiles(sessionId, filesNeedingUpload) { ok ->
            sendInFlight = false
            if (!ok) return@uploadPendingFiles
            // 用首次点击时的快照发送，避免读取被编辑后的草稿；文件带回上传后的 handle。
            val uploaded = sendableFiles.map { file ->
                pendingFiles.firstOrNull { it.clientId == file.clientId } ?: file
            }
            submitDraft(sessionId, prompt, sendableImages, uploaded, hostRepository)
        }
        return
    }
    submitDraft(sessionId, prompt, sendableImages, sendableFiles, hostRepository)
}

internal fun DshHomePage.submitDraft(
    sessionId: String,
    prompt: String,
    sendableImages: List<DshPendingImage>,
    sendableFiles: List<DshPendingFile>,
    hostRepository: DshRemoteRepository,
) {
    val wirePrompt = composePromptWithFiles(prompt, sendableFiles)
    val user = DshMessage(
        "user-${messages.size}",
        DshMessageRole.USER,
        wirePrompt,
    )
    val assistantId = "assistant-${messages.size}"
    val reasoningId = "$assistantId-reasoning"
    val wasEmpty = messages.isEmpty()
    messages.add(user)
    // DSH ChatView keeps the assistant node out of the flow until the
    // first token. The turn-status row ("Deep diving...") occupies that
    // gap so LazyLoop never has to realize an empty markdown bubble.
    sessionMessageStates[sessionId] = messages
    if (wasEmpty) remountConversationList(sessionId)
    // 第一条消息发出后会话即归属所选工作区，工作区配置入口（文件夹 chip）随即消失。
    if (wasEmpty && isBlankSession(sessionId)) {
        updateSessionMetadata(sessionId) { it.copy(blank = false) }
    }
    pinFollowListTail()
    scrollMessagesToMessage(user.id)
    streamingTurnAnchorAssistantId = messages.lastOrNull(::dshIsLiveAssistantText)?.id.orEmpty()
    streamingAssistantId = ""
    streamingAssistantRootId = assistantId
    streamingAssistantSegment = 0
    streamingReasoningId = reasoningId
    streamingSourceSeq = null
    streamingReasoningContent = ""
    streamingAssistantContent = ""
    pendingAssistantDelta.setLength(0)
    assistantFlushScheduled = false
    draft = ""
    inputView?.setText("")
    // 进入发送中：输入区草稿立即反映 UPLOADING 状态
    sendableImages.forEach { image ->
        val idx = pendingImages.indexOfFirst { it.clientId == image.clientId }
        if (idx >= 0) {
            pendingImages[idx] = image.copy(state = DshImageDraftState.UPLOADING)
            attachmentEpoch += 1
        }
    }
    sendableFiles.forEach { file ->
        val idx = pendingFiles.indexOfFirst { it.clientId == file.clientId }
        if (idx >= 0) {
            pendingFiles[idx] = file.copy(state = DshFileDraftState.UPLOADING)
            attachmentEpoch += 1
        }
    }
    streaming = true
    stopButtonVisible = true
    connectionLabel = "正在生成"
    syncTurnStatusTicker()
    touchSessionActivity(sessionId)
    streamHandle = hostRepository.streamReplyWithImages(
        pagerId = pagerId,
        sessionId = sessionId,
        prompt = wirePrompt,
        images = sendableImages,
        onDelta = { delta, isReasoning ->
            if (isReasoning) queueReasoningDelta(reasoningId, delta)
            else queueAssistantDelta(assistantId, delta)
        },
        onComplete = { result ->
            if (!connectionCoordinator.isActive(connectionMode)) return@streamReplyWithImages
            // Host 已接受该轮（含图片），输入区草稿收敛为空；图片由 Host timeline 以 attachmentId 呈现
            val completedIds = sendableImages.map { it.clientId }.toSet()
            pendingImages.removeAll { it.clientId in completedIds }
            pendingFiles.removeAll { it.clientId in sendableFiles.map { f -> f.clientId }.toSet() }
            attachmentEpoch += 1
            // 发送中的图片此时才移入用户气泡；整轮发送期间输入区保留“发送中”状态
            val previews = sendableImages.map { it.previewDataUrl }
            sessionMessageStates[sessionId]?.let { state ->
                val index = state.indexOfFirst { it.id == user.id }
                if (index >= 0 && previews.isNotEmpty()) {
                    state[index] = state[index].copy(imagePreviews = previews)
                }
            }
            flushAssistantDelta()
            if (streamingAssistantId.isEmpty() && result.isNotEmpty()) {
                ensureStreamingAssistantSegment()
            }
            // A turn may contain several assistant text blocks separated by
            // tool calls. The current segment already contains the final
            // block; using the turn-wide accumulator here would move all
            // earlier text back into this last row.
            val completedContent = streamingAssistantContent.ifEmpty { result }
            settleStreamingMessage(DshMessageRole.ASSISTANT, completedContent)
            persistMessages(sessionId)
            connectionLabel = "已连接"
            streamHandle = null
        },
        onError = { error ->
            if (!connectionCoordinator.isActive(connectionMode)) return@streamReplyWithImages
            if (dshIsTransportInterrupt("", error)) {
                DshStreamLog.i("ui.prompt-interrupt session=$sessionId message='${DshStreamLog.preview(error)}'")
                // 传输中断由重连接管，输入区不再挂“发送中”；图片由 Host timeline 以 attachmentId 回显
                pendingImages.removeAll { it.clientId in sendableImages.map { it.clientId }.toSet() }
                pendingFiles.removeAll { it.clientId in sendableFiles.map { f -> f.clientId }.toSet() }
                attachmentEpoch += 1
                return@streamReplyWithImages
            }
            // 发送失败：图片重新加入输入区并标记 FAILED，可重试或删除
            sendableImages.forEach { image ->
                val idx = pendingImages.indexOfFirst { it.clientId == image.clientId }
                val failed = image.copy(
                    state = DshImageDraftState.FAILED,
                    error = "发送失败：$error",
                )
                if (idx >= 0) {
                    pendingImages[idx] = failed
                } else {
                    pendingImages.add(failed)
                }
            }
            // 文件已落盘：仅回到已就绪，重发时复用同一 handle，不重复上传。
            sendableFiles.forEach { file ->
                val idx = pendingFiles.indexOfFirst { it.clientId == file.clientId }
                val reset = file.copy(state = DshFileDraftState.SELECTED)
                if (idx >= 0) pendingFiles[idx] = reset else pendingFiles.add(reset)
            }
            attachmentEpoch += 1
            flushAssistantDelta()
            ensureStreamingAssistantSegment()
            DshStreamLog.log(LogLevel.ERROR, "ui.error", "ui.error session=$sessionId message='${DshStreamLog.preview(error)}'", sessionId, null)
            settleStreamingMessage(DshMessageRole.ERROR, error)
            persistMessages(sessionId)
            connectionLabel = "已连接"
            streamHandle = null
        },
    )
}

internal fun DshHomePage.stopStream() {
    if (!stopButtonVisible) return
    dismissKeyboard()
    streamHandle?.cancel()
    streamHandle = null
    // 主动停止时，已随 prompt 发出的图片/文件不再留在输入区
    pendingImages.removeAll { it.state == DshImageDraftState.UPLOADING }
    pendingFiles.removeAll { it.state == DshFileDraftState.UPLOADING }
    attachmentEpoch += 1
    flushAssistantDelta()
    ensureStreamingAssistantSegment()
    val stoppedContent = streamingAssistantContent + "\n\n*已停止*"
    settleStreamingMessage(DshMessageRole.ASSISTANT, stoppedContent)
    persistMessages(activeSessionId)
    connectionLabel = "已连接"
}

internal fun DshHomePage.cancelStreamingForSessionSwitch() {
    if (!streaming && !stopButtonVisible) return
    streamHandle?.cancel()
    streamHandle = null
    val partial = streamingAssistantContent + pendingAssistantDelta.toString()
    if (streamingAssistantId.isNotEmpty()) {
        updateStreamingMessage(partial, streaming = false)
    }
    finalizeStreamingReasoning()
    streamingAssistantId = ""
    streamingAssistantRootId = ""
    streamingAssistantSegment = 0
    streamingReasoningId = ""
    streamingReasoningContent = ""
    pendingAssistantDelta.setLength(0)
    streamingAssistantContent = ""
    assistantFlushScheduled = false
    streamingTurnAnchorAssistantId = ""
    streaming = false
    stopButtonVisible = false
    syncTurnStatusTicker()
}

internal fun DshHomePage.queueAssistantDelta(id: String, delta: String) {
    if (delta.isEmpty()) return
    if (!streaming || streamingAssistantRootId != id) return
    ensureStreamingAssistantSegment()
    pendingAssistantDelta.append(delta)
    val firstPaint = streamingAssistantContent.isEmpty()
    if (assistantFlushScheduled && !firstPaint) return
    assistantFlushScheduled = true
    setTimeout(pagerId, if (firstPaint) 0 else DshHomePage.STREAM_FLUSH_INTERVAL_MS) {
        assistantFlushScheduled = false
        flushAssistantDelta()
    }
}

internal fun DshHomePage.queueReasoningDelta(id: String, delta: String) {
    if (delta.isEmpty() || streamingReasoningId != id) return
    streamingReasoningContent += delta
    val index = messages.indexOfFirst { it.id == id }
    if (index >= 0) {
        messages[index] = messages[index].copy(
            content = streamingReasoningContent,
            streaming = true,
            isReasoning = true,
        )
    } else {
        messages.add(DshMessage(id, DshMessageRole.ASSISTANT, streamingReasoningContent, streaming = true, isReasoning = true, sourceSeq = streamingSourceSeq))
    }
    realizeVisibleMessages()
    if (followListTail) scrollMessagesToEnd()
}

internal fun DshHomePage.flushAssistantDelta() {
    if (streamingAssistantId.isEmpty() || pendingAssistantDelta.isEmpty()) return
    streamingAssistantContent += pendingAssistantDelta.toString()
    pendingAssistantDelta.setLength(0)
    // Keep the ObservableList row stable while tokens arrive. `messages[i] =
    // copy()` is remove+add; LazyLoop treats an append at currentEnd as
    // "behind the visible range" and will not build the cell until scroll.
    // DshMarkdown already reads `streamingAssistantContent` via liveContent.
    insertLiveAssistantRow()
    ensureLiveMessageCell()
    refreshSessionRenderTree(activeSessionId)
    scrollMessagesToEnd()
}

/**
 * A live assistant response is an ordered sequence of text segments and
 * tool cards. Start a new row lazily after a tool card so the next delta is
 * placed after that card instead of being appended to the old row.
 */

internal fun DshHomePage.ensureStreamingAssistantSegment() {
    if (streamingAssistantId.isNotEmpty()) return
    if (streamingAssistantRootId.isEmpty()) return
    val id = if (streamingAssistantSegment == 0) {
        streamingAssistantRootId
    } else {
        "$streamingAssistantRootId-segment-${streamingAssistantSegment}"
    }
    streamingAssistantId = id
    if (streamingAssistantContent.isEmpty() && pendingAssistantDelta.isEmpty()) {
        // Inserting an empty assistant into a brand-new List (only the user
        // bubble) is "add behind currentEnd". LazyLoop will not build that
        // cell until a real scroll, and DshMessageRow also skips mounting
        // Markdown when the first paint is empty. Wait for the first flush.
        return
    }
    insertLiveAssistantRow()
}

internal fun DshHomePage.insertLiveAssistantRow() {
    val id = streamingAssistantId
    if (id.isEmpty() || messages.any { it.id == id }) return
    // Keep content empty until settle. The first-flush snapshot must not
    // become the display source; DshMarkdown reads the live buffer.
    messages.add(DshMessage(id, DshMessageRole.ASSISTANT, "", streaming = true, sourceSeq = streamingSourceSeq))
    ensureLiveMessageCell()
}

/**
 * vforLazy only creates items inside `[currentStart, currentEnd)`. Appending
 * the first assistant after the list was mounted with a single user bubble
 * lands at `currentEnd`. `setContentOffset` is a no-op when content is
 * shorter than the viewport (new session, first turn), so the cell never
 * appears until the user drags. `scrollToPosition` is what actually builds it.
 */

internal fun DshHomePage.ensureLiveMessageCell() {
    if (!followListTail) return
    val id = streamingAssistantId
    if (id.isEmpty()) return
    if (messageRowRefs[messageRowKey(activeSessionId, id)]?.view != null) return
    val list = messageScrollerRefs[activeSessionId]?.view ?: return
    val index = messages.indexOfFirst { it.id == id }
    if (index < 0) return
    list.scrollToPosition(index, 0f, false)
}

/** Close the current text row immediately before the next tool card. */

internal fun DshHomePage.splitStreamingAssistantBeforeTool() {
    if (!streaming || streamingAssistantRootId.isEmpty()) return
    flushAssistantDelta()
    val id = streamingAssistantId
    if (id.isNotEmpty()) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = messages[index]
            val text = current.content.ifEmpty { streamingAssistantContent }
            if (text.isEmpty()) {
                messages.removeAt(index)
            } else {
                messages[index] = current.copy(content = text, streaming = false)
                realizeVisibleMessages()
            }
        }
    }
    streamingAssistantId = ""
    streamingAssistantContent = ""
    streamingAssistantSegment += 1
    pendingAssistantDelta.setLength(0)
    assistantFlushScheduled = false
}

internal fun DshHomePage.updateStreamingMessage(content: String, streaming: Boolean, isReasoning: Boolean = false) {
    val index = messages.indexOfFirst { it.id == streamingAssistantId }
    if (index < 0) return
    messages[index] = messages[index].copy(
        content = content,
        streaming = streaming,
        isReasoning = isReasoning,
    )
    if (index >= messages.size - 1) realizeVisibleMessages()
}

internal fun DshHomePage.finalizeStreamingReasoning() {
    if (streamingReasoningId.isEmpty()) return
    val index = messages.indexOfFirst { it.id == streamingReasoningId }
    if (index >= 0) {
        messages[index] = messages[index].copy(streaming = false, isReasoning = true)
    }
}

internal fun DshHomePage.settleStreamingMessage(role: DshMessageRole, content: String) {
    val id = streamingAssistantId
    if (id.isNotEmpty()) {
        val sessionId = activeSessionId
        val finalContent = content.ifEmpty { streamingAssistantContent }
        finalizeStreamingReasoning()
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = messages[index].copy(
                role = role,
                content = finalContent,
                streaming = false,
                sourceSeq = streamingSourceSeq ?: messages[index].sourceSeq,
            )
        } else {
            messages.add(DshMessage(id, role, finalContent, streaming = false, sourceSeq = streamingSourceSeq))
        }
        realizeVisibleMessages()
        streamingReasoningId = ""
        streamingReasoningContent = ""
        pendingAssistantDelta.setLength(0)
        stopButtonVisible = false
        streaming = false
        streamingAssistantContent = finalContent
        syncTurnStatusTicker()
        addTaskWhenPagerUpdateLayoutFinish {
            if (activeSessionId != sessionId) return@addTaskWhenPagerUpdateLayoutFinish
            if (!streaming && streamingAssistantId == id) {
                val stored = messages.firstOrNull { it.id == id }?.content.orEmpty()
                if (stored.length >= finalContent.length) {
                    streamingAssistantId = ""
                    streamingAssistantRootId = ""
                    streamingAssistantSegment = 0
                    streamingTurnAnchorAssistantId = ""
                    if (streamingAssistantContent == finalContent) {
                        streamingAssistantContent = ""
                    }
                }
            }
            refreshSessionRenderTree(sessionId)
            setTimeout(pagerId, 16) {
                if (activeSessionId != sessionId) return@setTimeout
                addTaskWhenPagerUpdateLayoutFinish {
                    if (activeSessionId != sessionId) return@addTaskWhenPagerUpdateLayoutFinish
                    refreshSessionRenderTree(sessionId)
                }
            }
        }
        return
    }
    releaseStreamingUi()
}

internal fun DshHomePage.releaseStreamingUi() {
    streamingSourceSeq = null
    streamingAssistantId = ""
    streamingAssistantRootId = ""
    streamingAssistantSegment = 0
    streamingTurnAnchorAssistantId = ""
    streamingReasoningId = ""
    streamingReasoningContent = ""
    pendingAssistantDelta.setLength(0)
    streaming = false
    stopButtonVisible = false
    streamingAssistantContent = ""
    syncTurnStatusTicker()
}
