package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.ui.chat.DshConversation
import com.example.dsh.ui.voice.dshVoiceStrings
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.View
import com.example.dsh.base.setTimeout
import com.example.dsh.message.messageRowKey
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.ui.export.DshExportSelectionTopBar
import com.example.dsh.ui.search.DshSessionDrawer

internal fun DshHomePage.bodyTopBar(): ViewBuilder {
    val ctx = this
    return {
        // ===== 顶部栏 =====
        // 58dp 高的标题栏容器（zIndex 置顶），内部是 DshTopBar：
        // 左侧菜单/标题点击打开会话抽屉，右上角 overflow menu 打开会话管理菜单。
        View {
            attr {
                height(58f)
                zIndex(3)
            }
            vif({ ctx.ui.exportSelectMode }) {
                DshExportSelectionTopBar(
                    totalCount = { ctx.exportTotalCount() },
                    allSelected = { ctx.exportAllSelected() },
                    onToggleAll = { ctx.toggleExportSelectAll() },
                    onClose = { ctx.cancelExportSelection() },
                    colors = { ctx.themeColors },
                )
            }
            velse {
                DshTopBar(
                    title = { ctx.ui.sessions.firstOrNull { it.id == ctx.ui.activeSessionId }?.title ?: "DeepSeek Harness" },
                    onOpenDrawer = {
                        ctx.dismissKeyboard()
                        ctx.openSessionDrawer()
                    },
                    onNewSession = { ctx.createSession() },
                    colors = { ctx.themeColors },
                )
            }
        }

    }
}

internal fun DshHomePage.bodyMainContent(): ViewBuilder {
    val ctx = this
    val wide = pagerData.pageViewWidth >= 720f
    return {
        // ===== 主内容容器 =====
        // 撑满剩余空间，容纳下方的会话栏/对话区/详情面板与抽屉遮罩。
        // 会话抽屉打开时整体右移（translate），露出右侧被半透明遮罩覆盖的内容边缘，带位移动画。
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                // Push the conversation with the drawer, leaving the
                // dimmed right edge visible like the reference UI.
                transform(Translate(
                    0f,
                    offsetX = if (ctx.ui.sessionDrawerAnimated) {
                        (pagerData.pageViewWidth - 44f).coerceAtMost(340f)
                    } else {
                        0f
                    },
                ))
                    animation(Animation.easeOut(DshHomePage.ANIMATION_DURATION_S), ctx.ui.sessionDrawerAnimated)
            }
            if (wide) {
                // ==== 宽屏（平板/桌面）三栏布局容器 ====
                View {
                    attr {
                        flex(1f)
                        flexDirectionRow()
                        backgroundColor(ctx.themeColors.bgBase)
                    }
                    // -- 左侧「会话栏」--：仅远程（扫码/SSH）模式显示，列出所有会话，点击切换。
                    vif({ ctx.isRemoteHost }) {
                        DshSessionRail(
                            sessions = { ctx.ui.visibleSessions },
                            activeId = { ctx.ui.activeSessionId },
                            compact = false,
                            onSelect = { id ->
                                ctx.closeSessionDrawer()
                                setTimeout(ctx.pagerId, 0) { ctx.selectSession(id) }
                            },
                            colors = { ctx.themeColors },
                        )
                    }
                    // centerWidth：中间对话区可用宽度 = 总宽 - 左会话栏(236) - 右详情面板(280)，最小 360。
                    val centerWidth = if (ctx.isRemoteHost) {
                        (ctx.pagerData.pageViewWidth - 236f - 280f).coerceAtLeast(360f)
                    } else {
                        ctx.pagerData.pageViewWidth
                    }
                    // -- 中间「对话区」--：核心聊天界面 = 消息列表 + 底部输入区。
                    //    输入区内含技能、模型选择、附件、语音、停止按钮，
                    //    以及远程模式下的任务队列/作业面板/目标/审批与提问等。
                    DshConversation(
                        conversationIds = { ctx.ui.conversationPanelIds },
                        activeConversationId = { ctx.ui.activeSessionId },
                        messagesForSession = { ctx.sessionMessageState(it) },
                        streaming = { ctx.ui.streaming },
                        streamingMessageId = { ctx.ui.streamingAssistantId },
                        streamingContent = { ctx.ui.streamingAssistantContent },
                        scrollerRef = { id, ref -> ctx.messageScrollerRefs[id] = ref },
                        messageRef = { sessionId, messageId, ref ->
                            ctx.messageRowRefs[messageRowKey(sessionId, messageId)] = ref
                        },
                        draft = { ctx.ui.draft },
                        skills = { ctx.ui.skills },
                        onPickSkill = { ctx.insertDraftText("/$it ") },
                        keyboardHeight = { ctx.ui.keyboardHeight },
                        stopButtonVisible = { ctx.ui.stopButtonVisible },
                        inputRef = { ctx.onInputViewCreated(it.view) },
                        onInputFocusChange = { ctx.inputFocused = it },
                        onDraftChange = { ctx.ui.draft = it },
                        keyboardAnimation = { ctx.ui.keyboardAnimation },
                        onKeyboardHeightChange = { ctx.updateKeyboard(it) },
                        onSend = { ctx.sendDraft() },
                        onStop = { ctx.stopStream() },
                        onDismissKeyboard = { ctx.dismissKeyboard() },
                        onUserListScroll = { ctx.onConversationUserScroll(it) },
                        modelLabel = { if (ctx.ui.selectedEffortLabel.isEmpty()) ctx.ui.selectedModelLabel else "${ctx.ui.selectedModelLabel} · ${ctx.ui.selectedEffortLabel}" },
                        commandSheetVisible = { ctx.ui.commandSheetVisible },
                        voiceActive = { ctx.ui.voiceUi.active },
                        onOpenModels = { ctx.openModelPicker() },
                        onToggleCommandSheet = { ctx.toggleCommandSheet() },
                        onPickCommand = { ctx.insertCommand(it) },
                        onAttachmentTile = { ctx.onAttachmentTile(it) },
                    attachmentEpoch = { ctx.ui.attachmentEpoch },
                    attachmentRevision = { ctx.ui.attachmentRevision },
                    pendingImages = { ctx.ui.pendingImages },
                    onRemovePendingImage = { ctx.removePendingImage(it) },
                    onRetryPendingImage = { ctx.retryPendingImage(it) },
                    pendingFiles = { ctx.ui.pendingFiles },
                    onRemovePendingFile = { ctx.removePendingFile(it) },
                    onRetryPendingFile = { ctx.retryPendingFile(it) },
                        onToggleVoice = { ctx.toggleVoice() },
                        voiceRecording = { ctx.ui.voiceUi.recording },
                        voiceCancelArmed = { ctx.ui.voiceUi.cancelArmed },
                        voicePartialText = { ctx.ui.voiceUi.partialText },
                        voiceWaveform = { ctx.voiceWaveformModel.levels },
                        voiceWaveformRevision = { ctx.ui.voiceUi.waveformRevision },
                        voiceStrings = { dshVoiceStrings(ctx.ui.settingsSnapshot.localeValue) },
                        onVoiceHoldStart = { ctx.beginVoiceRecord(it) },
                        onVoiceHoldMove = { ctx.updateVoiceHold(it) },
                        onVoiceHoldEnd = { ctx.endVoiceRecord() },
                        folderLabel = { ctx.composerFolderLabel() },
                        onOpenWorkspacePicker = { ctx.openWorkspacePicker() },
                        permissionValue = { ctx.ui.permissionValue },
                        permissionLabel = { ctx.ui.permissionLabel },
                        onOpenPermissions = { ctx.openPermissionPicker() },
                        agentModeLabel = { ctx.ui.agentModeLabel },
                        onOpenAgentModes = { ctx.openAgentModePicker() },
                        isWebTimeline = { ctx.isRemoteHost },
                        processDisplayMode = { ctx.ui.chatProcessMode },
                        showConnectors = { ctx.ui.chatShowConnectors },
                        showResultCards = { ctx.ui.chatShowResultCards },
                        expandInModal = { ctx.ui.chatExpandInModal },
                        onOpenExpandedModal = { ctx.openExpandedModal(it) },
                        isDisclosureExpanded = { ctx.isWebDisclosureExpanded(it) },
                        onToggleDisclosure = { ctx.toggleWebDisclosure(it) },
                        isJsonNodeExpanded = { messageId, nodeId ->
                            ctx.isWebJsonNodeExpanded(messageId, nodeId)
                        },
                        onToggleJsonNode = { messageId, nodeId ->
                            ctx.toggleWebJsonNode(messageId, nodeId)
                        },
                        onCopyToolContent = {
                            ctx.bridgeModule.copyToPasteboard(it)
                            ctx.bridgeModule.toast("已复制")
                        },
                        onCopyMessageContent = { msg -> ctx.copyMessageBody(msg) },
                        copiedMessageId = { ctx.ui.copiedMessageId },
                        colors = { ctx.themeColors },
                        onMessageLongPress = { msg, content, px, py ->
                            ctx.openMessageActions(msg, content, px, py)
                        },
                        onFooterAction = { msg, action -> ctx.onMessageFooterAction(msg, action) },
                         attachmentDataUrl = { ctx.attachmentDataUrl(it) },
                         queueItems = { ctx.ui.queueItems },
                         jobItems = { ctx.ui.jobItems },
                         liveJobItems = { ctx.ui.liveJobItems },
                         goal = { ctx.ui.goalSnapshot },
                        goalActionBusy = { ctx.ui.goalActionBusy },
                        goalActionError = { ctx.ui.goalActionError },
                        onPauseGoal = { ctx.pauseGoal() },
                        onResumeGoal = { ctx.resumeGoal() },
                        onEditGoal = { text, done -> ctx.editGoal(text, done) },
                        onClearGoal = { ctx.clearGoal() },
                        jobsPanelExpanded = { ctx.ui.jobsPanelExpanded },
                        jobsNow = { ctx.ui.jobsNow },
                        onToggleJobsPanel = { ctx.toggleJobsPanel() },
                        queueExpanded = { ctx.ui.queueDockExpanded },
                        queueEditingId = { ctx.ui.queueEditingId },
                        queueActionBusy = { ctx.ui.queueActionBusy },
                        queueEditingText = { ctx.ui.queueEditingText },
                        sessionRunning = { ctx.ui.sessionRunning },
                        isBlankConversation = { ctx.isBlankSession() },
                        conversationListEpoch = { ctx.conversationListEpochFor(it) },
                        messageRenderEpoch = { ctx.ui.messageRenderEpoch },
                        turnReconnecting = { isReconnectLabel(ctx.connectionLabel) },
                        turnElapsedMs = { ctx.ui.turnElapsedMs },
                        onToggleQueue = { ctx.ui.queueDockExpanded = !ctx.ui.queueDockExpanded },
                        onEditQueueItem = { ctx.editQueueItem(it) },
                        onQueueEditingTextChange = { ctx.ui.queueEditingText = it },
                        onSaveQueueItem = { ctx.saveQueueItem(it) },
                        onCancelQueueItemEdit = { ctx.cancelQueueItemEdit() },
                        onRemoveQueueItem = { ctx.removeQueueItem(it) },
                        onSteerQueueItem = { ctx.steerQueueItem(it) },
                        pendingApproval = { ctx.ui.pendingApproval },
                        pendingQuestion = { ctx.ui.pendingQuestion },
                        interactionBusy = { ctx.ui.interactionBusy },
                        selectedQuestionOptions = { ctx.ui.selectedQuestionOptions },
                        questionCustom = { ctx.ui.questionCustom },
                        questionIndex = { ctx.ui.questionIndex },
                        questionError = { ctx.ui.questionError },
                        questionHasSelection = { ctx.ui.questionHasSelection },
                        onAnswerApproval = { ctx.answerApproval(it) },
                        onToggleQuestionOption = { ctx.toggleQuestionOption(it) },
                        onQuestionCustomChange = { ctx.updateQuestionCustom(it) },
                        onQuestionNavigate = { ctx.navigateQuestion(it) },
                        onQuestionSkip = { ctx.skipQuestion() },
                        onSubmitQuestion = { ctx.submitQuestion() },
                        onDismissQuestion = { ctx.cancelQuestion() },
                        availableWidth = centerWidth,
                        connectionLabel = { ctx.connectionLabel },
                        connectionCapsuleVisible = { ctx.ui.connectionCapsuleVisible },
                        connectionCapsuleFadeOut = { ctx.ui.connectionCapsuleFadeOut },
                        connectionCapsuleFadeOutAnimation = { ctx.ui.connectionCapsuleFadeOutAnimation },
                        onPreviewImage = { ctx.ui.previewImageUrl = it },
                        previewImageUrl = { ctx.ui.previewImageUrl },
                        onDismissPreview = { ctx.ui.previewImageUrl = null },
                        onSaveImage = { ctx.saveImageToGallery(it) },
                        exportSelectMode = { ctx.ui.exportSelectMode },
                        exportSelectedIds = { ctx.exportSelectedMessageIds() },
                        exportFormat = { ctx.ui.exportFormat },
                        exportSelectedCount = { ctx.exportSelectedCount() },
                        exportMoreShareExpanded = { ctx.ui.exportMoreShareVisible },
                        exportPdfBusy = { ctx.ui.exportPdfBusy },
                        onToggleExportMessage = { ctx.toggleExportMessage(it) },
                        onExportFormatChange = { ctx.ui.exportFormat = it },
                        onExportConfirm = { ctx.confirmExportSelection() },
                        onExportPdf = { ctx.exportSelectionAsPdf() },
                        onExportCopy = { ctx.copyExportSelection() },
                        onExportMore = { ctx.toggleExportMoreShare() },
                        onExportClose = { ctx.cancelExportSelection() },
                        exportStickySelectorVisible = { ctx.exportStickySelectorVisible() },
                        exportStickySelectorSelected = { ctx.exportStickySelectorSelected() },
                        onExportStickySelectorToggle = { ctx.toggleExportStickySelector() },
                    )
                    // -- 右侧「会话详情面板」--：仅远程模式显示，展示当前会话的标题、
                    //    工作目录、模型、运行状态、队列/作业数量。
                    vif({ ctx.isRemoteHost }) {
                        DshSessionDetailsPanel(
                            title = { ctx.ui.sessions.firstOrNull { it.id == ctx.ui.activeSessionId }?.title ?: "尚无标题" },
                            cwd = { ctx.ui.sessions.firstOrNull { it.id == ctx.ui.activeSessionId }?.cwd ?: "" },
                            modelLabel = { if (ctx.ui.selectedEffortLabel.isEmpty()) ctx.ui.selectedModelLabel else "${ctx.ui.selectedModelLabel} · ${ctx.ui.selectedEffortLabel}" },
                            agentPreset = { ctx.ui.sessions.firstOrNull { it.id == ctx.ui.activeSessionId }?.agentPreset.orEmpty() },
                            running = { ctx.ui.sessionRunning },
                            queueCount = { ctx.ui.queueItems.size },
                            jobCount = { ctx.ui.jobItems.size },
                            colors = { ctx.themeColors },
                        )
                    }
                }
            } else {
                // ==== 窄屏（手机）单栏布局 ====
                // 不显示会话栏/详情面板，对话区直接铺满整宽。
                DshConversation(
                    conversationIds = { ctx.ui.conversationPanelIds },
                    activeConversationId = { ctx.ui.activeSessionId },
                    messagesForSession = { ctx.sessionMessageState(it) },
                    streaming = { ctx.ui.streaming },
                    streamingMessageId = { ctx.ui.streamingAssistantId },
                    streamingContent = { ctx.ui.streamingAssistantContent },
                    scrollerRef = { id, ref -> ctx.messageScrollerRefs[id] = ref },
                    messageRef = { sessionId, messageId, ref ->
                        ctx.messageRowRefs[messageRowKey(sessionId, messageId)] = ref
                    },
                    draft = { ctx.ui.draft },
                    skills = { ctx.ui.skills },
                    onPickSkill = { ctx.insertDraftText("/$it ") },
                    keyboardHeight = { ctx.ui.keyboardHeight },
                    stopButtonVisible = { ctx.ui.stopButtonVisible },
                    inputRef = { ctx.onInputViewCreated(it.view) },
                    onInputFocusChange = { ctx.inputFocused = it },
                    onDraftChange = { ctx.ui.draft = it },
                    keyboardAnimation = { ctx.ui.keyboardAnimation },
                    onKeyboardHeightChange = { ctx.updateKeyboard(it) },
                    onSend = { ctx.sendDraft() },
                    onStop = { ctx.stopStream() },
                    onDismissKeyboard = { ctx.dismissKeyboard() },
                    onUserListScroll = { ctx.onConversationUserScroll(it) },
                    modelLabel = { if (ctx.ui.selectedEffortLabel.isEmpty()) ctx.ui.selectedModelLabel else "${ctx.ui.selectedModelLabel} · ${ctx.ui.selectedEffortLabel}" },
                    commandSheetVisible = { ctx.ui.commandSheetVisible },
                    voiceActive = { ctx.ui.voiceUi.active },
                    onOpenModels = { ctx.openModelPicker() },
                    permissionValue = { ctx.ui.permissionValue },
                    permissionLabel = { ctx.ui.permissionLabel },
                    onOpenPermissions = { ctx.openPermissionPicker() },
                    agentModeLabel = { ctx.ui.agentModeLabel },
                    onOpenAgentModes = { ctx.openAgentModePicker() },
                    onToggleCommandSheet = { ctx.toggleCommandSheet() },
                    onPickCommand = { ctx.insertCommand(it) },
                    onAttachmentTile = { ctx.onAttachmentTile(it) },
                    attachmentEpoch = { ctx.ui.attachmentEpoch },
                    attachmentRevision = { ctx.ui.attachmentRevision },
                    pendingImages = { ctx.ui.pendingImages },
                    onRemovePendingImage = { ctx.removePendingImage(it) },
                    onRetryPendingImage = { ctx.retryPendingImage(it) },
                    pendingFiles = { ctx.ui.pendingFiles },
                    onRemovePendingFile = { ctx.removePendingFile(it) },
                    onRetryPendingFile = { ctx.retryPendingFile(it) },
                    onToggleVoice = { ctx.toggleVoice() },
                        voiceRecording = { ctx.ui.voiceUi.recording },
                        voiceCancelArmed = { ctx.ui.voiceUi.cancelArmed },
                        voicePartialText = { ctx.ui.voiceUi.partialText },
                        voiceWaveform = { ctx.voiceWaveformModel.levels },
                        voiceWaveformRevision = { ctx.ui.voiceUi.waveformRevision },
                        voiceStrings = { dshVoiceStrings(ctx.ui.settingsSnapshot.localeValue) },
                        onVoiceHoldStart = { ctx.beginVoiceRecord(it) },
                        onVoiceHoldMove = { ctx.updateVoiceHold(it) },
                        onVoiceHoldEnd = { ctx.endVoiceRecord() },
                    folderLabel = { ctx.composerFolderLabel() },
                    onOpenWorkspacePicker = { ctx.openWorkspacePicker() },
                    isWebTimeline = { ctx.isRemoteHost },
                    processDisplayMode = { ctx.ui.chatProcessMode },
                    showConnectors = { ctx.ui.chatShowConnectors },
                    showResultCards = { ctx.ui.chatShowResultCards },
                    expandInModal = { ctx.ui.chatExpandInModal },
                    onOpenExpandedModal = { ctx.openExpandedModal(it) },
                    isDisclosureExpanded = { ctx.isWebDisclosureExpanded(it) },
                    onToggleDisclosure = { ctx.toggleWebDisclosure(it) },
                    isJsonNodeExpanded = { messageId, nodeId ->
                        ctx.isWebJsonNodeExpanded(messageId, nodeId)
                    },
                    onToggleJsonNode = { messageId, nodeId ->
                        ctx.toggleWebJsonNode(messageId, nodeId)
                    },
                    onCopyToolContent = {
                        ctx.bridgeModule.copyToPasteboard(it)
                        ctx.bridgeModule.toast("已复制")
                    },
                    onCopyMessageContent = { msg -> ctx.copyMessageBody(msg) },
                    copiedMessageId = { ctx.ui.copiedMessageId },
                    colors = { ctx.themeColors },
                    onMessageLongPress = { msg, content, px, py ->
                        ctx.openMessageActions(msg, content, px, py)
                    },
                        onFooterAction = { msg, action -> ctx.onMessageFooterAction(msg, action) },
                     attachmentDataUrl = { ctx.attachmentDataUrl(it) },
                     queueItems = { ctx.ui.queueItems },
                     jobItems = { ctx.ui.jobItems },
                     liveJobItems = { ctx.ui.liveJobItems },
                     goal = { ctx.ui.goalSnapshot },
                    goalActionBusy = { ctx.ui.goalActionBusy },
                    goalActionError = { ctx.ui.goalActionError },
                    onPauseGoal = { ctx.pauseGoal() },
                    onResumeGoal = { ctx.resumeGoal() },
                    onEditGoal = { text, done -> ctx.editGoal(text, done) },
                    onClearGoal = { ctx.clearGoal() },
                    jobsPanelExpanded = { ctx.ui.jobsPanelExpanded },
                    jobsNow = { ctx.ui.jobsNow },
                    onToggleJobsPanel = { ctx.toggleJobsPanel() },
                    queueExpanded = { ctx.ui.queueDockExpanded },
                    queueEditingId = { ctx.ui.queueEditingId },
                    queueActionBusy = { ctx.ui.queueActionBusy },
                    queueEditingText = { ctx.ui.queueEditingText },
                    sessionRunning = { ctx.ui.sessionRunning },
                    isBlankConversation = { ctx.isBlankSession() },
                    conversationListEpoch = { ctx.conversationListEpochFor(it) },
                    messageRenderEpoch = { ctx.ui.messageRenderEpoch },
                    turnReconnecting = { isReconnectLabel(ctx.connectionLabel) },
                    turnElapsedMs = { ctx.ui.turnElapsedMs },
                    onToggleQueue = { ctx.ui.queueDockExpanded = !ctx.ui.queueDockExpanded },
                    onEditQueueItem = { ctx.editQueueItem(it) },
                    onQueueEditingTextChange = { ctx.ui.queueEditingText = it },
                    onSaveQueueItem = { ctx.saveQueueItem(it) },
                    onCancelQueueItemEdit = { ctx.cancelQueueItemEdit() },
                    onRemoveQueueItem = { ctx.removeQueueItem(it) },
                    onSteerQueueItem = { ctx.steerQueueItem(it) },
                    pendingApproval = { ctx.ui.pendingApproval },
                    pendingQuestion = { ctx.ui.pendingQuestion },
                    interactionBusy = { ctx.ui.interactionBusy },
                    selectedQuestionOptions = { ctx.ui.selectedQuestionOptions },
                    questionCustom = { ctx.ui.questionCustom },
                    questionIndex = { ctx.ui.questionIndex },
                    questionError = { ctx.ui.questionError },
                    questionHasSelection = { ctx.ui.questionHasSelection },
                    onAnswerApproval = { ctx.answerApproval(it) },
                    onToggleQuestionOption = { ctx.toggleQuestionOption(it) },
                    onQuestionCustomChange = { ctx.updateQuestionCustom(it) },
                    onQuestionNavigate = { ctx.navigateQuestion(it) },
                    onQuestionSkip = { ctx.skipQuestion() },
                    onSubmitQuestion = { ctx.submitQuestion() },
                    onDismissQuestion = { ctx.cancelQuestion() },
                    availableWidth = ctx.pagerData.pageViewWidth,
                    connectionLabel = { ctx.connectionLabel },
                    connectionCapsuleVisible = { ctx.ui.connectionCapsuleVisible },
                    connectionCapsuleFadeOut = { ctx.ui.connectionCapsuleFadeOut },
                    connectionCapsuleFadeOutAnimation = { ctx.ui.connectionCapsuleFadeOutAnimation },
                    onPreviewImage = { ctx.ui.previewImageUrl = it },
                    previewImageUrl = { ctx.ui.previewImageUrl },
                    onDismissPreview = { ctx.ui.previewImageUrl = null },
                    onSaveImage = { ctx.saveImageToGallery(it) },
                    exportSelectMode = { ctx.ui.exportSelectMode },
                    exportSelectedIds = { ctx.exportSelectedMessageIds() },
                    exportFormat = { ctx.ui.exportFormat },
                    exportSelectedCount = { ctx.exportSelectedCount() },
                    exportMoreShareExpanded = { ctx.ui.exportMoreShareVisible },
                    exportPdfBusy = { ctx.ui.exportPdfBusy },
                    onToggleExportMessage = { ctx.toggleExportMessage(it) },
                    onExportFormatChange = { ctx.ui.exportFormat = it },
                    onExportConfirm = { ctx.confirmExportSelection() },
                    onExportPdf = { ctx.exportSelectionAsPdf() },
                    onExportCopy = { ctx.copyExportSelection() },
                    onExportMore = { ctx.toggleExportMoreShare() },
                    onExportClose = { ctx.cancelExportSelection() },
                    exportStickySelectorVisible = { ctx.exportStickySelectorVisible() },
                    exportStickySelectorSelected = { ctx.exportStickySelectorSelected() },
                    onExportStickySelectorToggle = { ctx.toggleExportStickySelector() },
                )
            }
        }

    }
}

internal fun DshHomePage.bodySessionDrawer(): ViewBuilder {
    val ctx = this
    return {
        // ===== 会话抽屉 =====
        // 从左侧滑出的侧栏（覆盖在主内容之上）：顶部搜索框、会话列表/工作区分组、
        // 已归档会话与设置入口；点击遮罩或选择会话后关闭。
        vif({ ctx.ui.sessionDrawerVisible }) {
            DshSessionDrawer(
                sessions = { ctx.ui.visibleSessions },
                workspaceGroups = { ctx.ui.workspaceGroups },
                isWebTimeline = { ctx.isRemoteHost },
                activeId = { ctx.ui.activeSessionId },
                animated = { ctx.ui.sessionDrawerAnimated },
                expandedGroupIds = { ctx.ui.workspaceExpandedIds },
                onToggleGroup = { ctx.toggleWorkspaceExpanded(it) },
                sessionPending = { ctx.sessionPending(it) },
                activeWorkspaceId = { ctx.activeWorkspaceId() },
                overflowVisible = { ctx.ui.overflowMenuVisible },
                overflowActions = { ctx.overflowActions() },
                onOverflowSelect = { ctx.onOverflowAction(it) },
                onDismissOverflow = { ctx.closeOverflowMenu() },
                onOpenOverflowFor = { id, x, y -> ctx.openOverflowMenuFor(id, x, y) },
                overflowAnchorX = { ctx.ui.overflowAnchorX },
                overflowAnchorY = { ctx.ui.overflowAnchorY },
                dragState = { ctx.ui.drawerDrag },
                onSessionDragStart = { groupKey, sessionId, index, pageY ->
                    ctx.beginSessionDrag(groupKey, sessionId, index, pageY)
                },
                onWorkspaceDragStart = { workspaceId, index, itemHeight, pageY ->
                    ctx.beginWorkspaceDrag(workspaceId, index, itemHeight, pageY)
                },
                onDragMove = { pageY, heights -> ctx.updateDrawerDrag(pageY, heights) },
                onDragEnd = { ctx.endDrawerDrag() },
                onDragCancel = { ctx.cancelDrawerDrag() },
                viewGroupBy = { ctx.ui.drawerViewGroupBy },
                viewOrderBy = { ctx.ui.drawerViewOrderBy },
                viewOptionsVisible = { ctx.ui.drawerViewOptionsVisible },
                viewOptionsAnchorX = { ctx.ui.drawerViewOptionsX },
                viewOptionsAnchorY = { ctx.ui.drawerViewOptionsY },
                onViewOptionsOpen = { x, y -> ctx.openDrawerViewOptions(x, y) },
                onViewOptionsSelect = { ctx.selectDrawerViewOption(it) },
                onViewOptionsDismiss = { ctx.closeDrawerViewOptions() },
                statusBarHeight = ctx.pagerData.statusBarHeight,
                pageViewWidth = ctx.pagerData.pageViewWidth,
                pageViewHeight = ctx.pagerData.pageViewHeight,
                onClose = { ctx.closeSessionDrawer() },
                onOpenSettings = { ctx.openSettingsPage() },
                onOpenArchive = { ctx.openArchiveList() },
                onSelect = { id ->
                    ctx.closeSessionDrawer()
                    setTimeout(ctx.pagerId, 0) {
                        ctx.selectSession(id)
                    }
                },
                onOpenSearch = { ctx.openSessionSearch() },
                colors = { ctx.themeColors },
            )
        }

    }
}
