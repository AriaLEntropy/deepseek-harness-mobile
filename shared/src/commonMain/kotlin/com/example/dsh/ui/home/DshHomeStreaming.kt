package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
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
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.scrollToPosition
import com.tencent.kuikly.core.reactive.handler.*
import com.example.dsh.base.setTimeout
import kotlinx.coroutines.cancel
import com.example.dsh.attachment.composePromptWithFiles
import com.example.dsh.message.messageRowKey
import com.example.dsh.ui.rendering.DshProcessDisplayMode
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
    // 发送瞬间附件即离开输入区，随用户消息进入对话区并显示上传中状态。
    val userMessage = DshMessage(
        id = "user-${messages.size}",
        role = DshMessageRole.USER,
        content = prompt,
        imagePreviews = sendableImages.map { it.previewDataUrl },
        attachmentUploading = true,
        pendingFileAttachments = sendableFiles,
    )
    beginUserTurn(sessionId, userMessage)
    clearComposerAttachments(sendableImages, sendableFiles)
    // 旧 Host 无文件 content 类型：先把未落盘文件上传到会话工作目录，成功后再带上 handle 发送。
    val filesNeedingUpload = sendableFiles.filter { it.handle.isEmpty() }
    if (filesNeedingUpload.isNotEmpty()) {
        sendInFlight = true
        uploadFilesForMessage(sessionId, filesNeedingUpload) { ok, uploadedFiles ->
            sendInFlight = false
            if (!ok) {
                rollbackUserTurn(sessionId, userMessage.id, prompt, sendableImages, sendableFiles)
                return@uploadFilesForMessage
            }
            // 用首次点击时的快照发送，避免读取被编辑后的草稿；文件带回上传后的 handle。
            val byClient = uploadedFiles.associateBy { it.clientId }
            val uploaded = sendableFiles.map { byClient[it.clientId] ?: it }
            submitDraft(sessionId, prompt, sendableImages, uploaded, userMessage.id, hostRepository)
        }
        return
    }
    submitDraft(sessionId, prompt, sendableImages, sendableFiles, userMessage.id, hostRepository)
}

/** 用户消息先上屏：气泡立即出现，附件以「上传中」态留在对话区；输入框同步清空。 */
internal fun DshHomePage.beginUserTurn(sessionId: String, user: DshMessage) {
    val wasEmpty = messages.isEmpty()
    messages.add(user)
    sessionMessageStates[sessionId] = messages
    if (wasEmpty) remountConversationList(sessionId)
    // 第一条消息发出后会话即归属所选工作区，工作区配置入口（文件夹 chip）随即消失。
    if (wasEmpty && isBlankSession(sessionId)) {
        updateSessionMetadata(sessionId) { it.copy(blank = false) }
    }
    pinFollowListTail()
    scrollMessagesToMessage(user.id)
    draft = ""
    inputView?.setText("")
}

/** 附件随用户消息上屏后，从输入区移除，避免发送期间输入框仍显示「上传中」。 */
internal fun DshHomePage.clearComposerAttachments(
    images: List<DshPendingImage>,
    files: List<DshPendingFile>,
) {
    if (images.isNotEmpty()) pendingImages.removeAll { img -> images.any { it.clientId == img.clientId } }
    if (files.isNotEmpty()) pendingFiles.removeAll { f -> files.any { it.clientId == f.clientId } }
    attachmentEpoch += 1
}

/** 附件上传失败：撤回对话区占位，把正文与附件退回输入区（附件 FAILED 可重试）。 */
internal fun DshHomePage.rollbackUserTurn(
    sessionId: String,
    messageId: String,
    prompt: String,
    images: List<DshPendingImage>,
    files: List<DshPendingFile>,
) {
    removeUserMessage(sessionId, messageId)
    if (prompt.isNotEmpty()) {
        draft = prompt
        inputView?.setText(prompt)
    }
    images.forEach { image ->
        if (pendingImages.none { it.clientId == image.clientId }) {
            pendingImages.add(image.copy(state = DshImageDraftState.FAILED, error = "发送失败"))
        }
    }
    files.forEach { file ->
        if (pendingFiles.none { it.clientId == file.clientId }) {
            pendingFiles.add(file.copy(state = DshFileDraftState.FAILED, error = "上传失败"))
        }
    }
    attachmentEpoch += 1
}

internal fun DshHomePage.removeUserMessage(sessionId: String, messageId: String) {
    val state = sessionMessageStates[sessionId] ?: return
    state.removeAll { it.id == messageId }
    if (messages === state) realizeVisibleMessages()
}

internal fun DshHomePage.updateUserMessage(
    sessionId: String,
    messageId: String,
    transform: (DshMessage) -> DshMessage,
) {
    val state = sessionMessageStates[sessionId] ?: return
    val index = state.indexOfFirst { it.id == messageId }
    if (index < 0) return
    state[index] = transform(state[index])
    if (messages === state) realizeVisibleMessages()
}

internal fun DshHomePage.submitDraft(
    sessionId: String,
    prompt: String,
    sendableImages: List<DshPendingImage>,
    sendableFiles: List<DshPendingFile>,
    userMessageId: String,
    hostRepository: DshRemoteRepository,
) {
    val wirePrompt = composePromptWithFiles(prompt, sendableFiles)
    val assistantId = "assistant-${messages.size}"
    val reasoningId = "$assistantId-reasoning"
    // 用户消息补上文件 handle；附件保持上传中，直到本轮结束才收敛为已发送。
    updateUserMessage(sessionId, userMessageId) { message ->
        message.copy(content = wirePrompt, pendingFileAttachments = emptyList(), attachmentUploading = true)
    }
    // DSH ChatView keeps the assistant node out of the flow until the
    // first token. The turn-status row ("Deep diving...") occupies that
    // gap so LazyLoop never has to realize an empty markdown bubble.
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
            // 本轮结束：对话区附件从上传中收敛为已发送；图片预览保留，文件卡片由正文 handle 渲染。
            updateUserMessage(sessionId, userMessageId) { message ->
                message.copy(attachmentUploading = false, pendingFileAttachments = emptyList())
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
                // 传输中断由重连接管：附件保留已发送外观，等待 Host timeline 回显。
                updateUserMessage(sessionId, userMessageId) { message ->
                    message.copy(attachmentUploading = false, pendingFileAttachments = emptyList())
                }
                return@streamReplyWithImages
            }
            // 发送失败：附件退回输入区并标记 FAILED，可重试或删除；对话区不再保留上传中占位。
            updateUserMessage(sessionId, userMessageId) { message ->
                message.copy(
                    attachmentUploading = false,
                    pendingFileAttachments = emptyList(),
                    imagePreviews = emptyList(),
                )
            }
            sendableImages.forEach { image ->
                if (pendingImages.none { it.clientId == image.clientId }) {
                    pendingImages.add(image.copy(state = DshImageDraftState.FAILED, error = "发送失败：$error"))
                }
            }
            // 文件已落盘：仅回到已就绪，重发时复用同一 handle，不重复上传。
            sendableFiles.forEach { file ->
                if (pendingFiles.none { it.clientId == file.clientId }) {
                    pendingFiles.add(file.copy(state = DshFileDraftState.SELECTED))
                }
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
    // 主动停止：本轮附件收敛，不再显示上传中（已随 prompt 发出的图片/文件不回输入区）
    messages.lastOrNull { it.role == DshMessageRole.USER }?.let { user ->
        updateUserMessage(activeSessionId, user.id) {
            it.copy(attachmentUploading = false, pendingFileAttachments = emptyList())
        }
    }
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
    // 切换会话时本轮附件一并收敛，避免残留上传中遮罩。
    messages.lastOrNull { it.role == DshMessageRole.USER }?.let { user ->
        if (user.attachmentUploading) {
            updateUserMessage(activeSessionId, user.id) {
                it.copy(attachmentUploading = false, pendingFileAttachments = emptyList())
            }
        }
    }
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

/** 本轮（最近一条 user 之后）在最终回答之前是否存在过程内容（思考/工具/过渡正文）。 */
internal fun DshHomePage.hasProcessMembers(tailId: String): Boolean {
    val tailIndex = messages.indexOfFirst { it.id == tailId }
    if (tailIndex < 0) return false
    val lastUserIndex = messages.take(tailIndex).indexOfLast { it.role == DshMessageRole.USER }
    if (lastUserIndex < 0) return false
    return (lastUserIndex + 1 until tailIndex).any { i ->
        val m = messages[i]
        !m.hidden && (
            m.role == DshMessageRole.TOOL || m.isReasoning || m.isContextInjection ||
                (m.role == DshMessageRole.ASSISTANT && !m.isReasoning)
            )
    }
}

internal fun DshHomePage.settleStreamingMessage(role: DshMessageRole, content: String) {
    val id = streamingAssistantId
    if (id.isNotEmpty()) {
        val sessionId = activeSessionId
        val finalContent = content.ifEmpty { streamingAssistantContent }
        // 本轮结束：对话区附件从上传中收敛（各 settle 路径幂等兜底）。
        messages.lastOrNull { it.role == DshMessageRole.USER && it.attachmentUploading }?.let { user ->
            updateUserMessage(sessionId, user.id) {
                it.copy(attachmentUploading = false, pendingFileAttachments = emptyList())
            }
        }
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
        // 结算当帧触发消息行就地重算过程分组（折叠态立刻生效），不用等下一次布局/历史重挂。
        // 仅当本轮确有「过程内容」且处于统一折叠模式时才重建，避免普通问答白白重挂所有行。
        if (chatProcessMode == DshProcessDisplayMode.UNIFIED && hasProcessMembers(id)) {
            messageRenderEpoch += 1
        }
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
