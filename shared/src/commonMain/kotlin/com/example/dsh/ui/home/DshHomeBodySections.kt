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
            vif({ ctx.exportSelectMode }) {
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
                    title = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.title ?: "DeepSeek Harness" },
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
                    offsetX = if (ctx.sessionDrawerAnimated) {
                        (pagerData.pageViewWidth - 44f).coerceAtMost(340f)
                    } else {
                        0f
                    },
                ))
                    animation(Animation.easeOut(DshHomePage.ANIMATION_DURATION_S), ctx.sessionDrawerAnimated)
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
                            sessions = { ctx.visibleSessions },
                            activeId = { ctx.activeSessionId },
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
                        conversationIds = { ctx.conversationPanelIds },
                        activeConversationId = { ctx.activeSessionId },
                        messagesForSession = { ctx.sessionMessageState(it) },
                        streaming = { ctx.streaming },
                        streamingMessageId = { ctx.streamingAssistantId },
                        streamingContent = { ctx.streamingAssistantContent },
                        scrollerRef = { id, ref -> ctx.messageScrollerRefs[id] = ref },
                        messageRef = { sessionId, messageId, ref ->
                            ctx.messageRowRefs[messageRowKey(sessionId, messageId)] = ref
                        },
                        draft = { ctx.draft },
                        skills = { ctx.skills },
                        onPickSkill = { ctx.insertDraftText("/$it ") },
                        keyboardHeight = { ctx.keyboardHeight },
                        stopButtonVisible = { ctx.stopButtonVisible },
                        inputRef = { ctx.onInputViewCreated(it.view) },
                        onInputFocusChange = { ctx.inputFocused = it },
                        onDraftChange = { ctx.draft = it },
                        keyboardAnimation = { ctx.keyboardAnimation },
                        onKeyboardHeightChange = { ctx.updateKeyboard(it) },
                        onSend = { ctx.sendDraft() },
                        onStop = { ctx.stopStream() },
                        onDismissKeyboard = { ctx.dismissKeyboard() },
                        onUserListScroll = { ctx.onConversationUserScroll(it) },
                        modelLabel = { if (ctx.selectedEffortLabel.isEmpty()) ctx.selectedModelLabel else "${ctx.selectedModelLabel} · ${ctx.selectedEffortLabel}" },
                        commandSheetVisible = { ctx.commandSheetVisible },
                        voiceActive = { ctx.voiceUi.active },
                        onOpenModels = { ctx.openModelPicker() },
                        onToggleCommandSheet = { ctx.toggleCommandSheet() },
                        onPickCommand = { ctx.insertCommand(it) },
                        onAttachmentTile = { ctx.onAttachmentTile(it) },
                    attachmentEpoch = { ctx.attachmentEpoch },
                    attachmentRevision = { ctx.attachmentRevision },
                    pendingImages = { ctx.pendingImages },
                    onRemovePendingImage = { ctx.removePendingImage(it) },
                    onRetryPendingImage = { ctx.retryPendingImage(it) },
                    pendingFiles = { ctx.pendingFiles },
                    onRemovePendingFile = { ctx.removePendingFile(it) },
                    onRetryPendingFile = { ctx.retryPendingFile(it) },
                        onToggleVoice = { ctx.toggleVoice() },
                        voiceRecording = { ctx.voiceUi.recording },
                        voiceCancelArmed = { ctx.voiceUi.cancelArmed },
                        voicePartialText = { ctx.voiceUi.partialText },
                        voiceWaveform = { ctx.voiceWaveformModel.levels },
                        voiceWaveformRevision = { ctx.voiceUi.waveformRevision },
                        voiceStrings = { dshVoiceStrings(ctx.settingsSnapshot.localeValue) },
                        onVoiceHoldStart = { ctx.beginVoiceRecord(it) },
                        onVoiceHoldMove = { ctx.updateVoiceHold(it) },
                        onVoiceHoldEnd = { ctx.endVoiceRecord() },
                        folderLabel = { ctx.composerFolderLabel() },
                        onOpenWorkspacePicker = { ctx.openWorkspacePicker() },
                        permissionValue = { ctx.permissionValue },
                        permissionLabel = { ctx.permissionLabel },
                        onOpenPermissions = { ctx.openPermissionPicker() },
                        agentModeLabel = { ctx.agentModeLabel },
                        onOpenAgentModes = { ctx.openAgentModePicker() },
                        isWebTimeline = { ctx.isRemoteHost },
                        processDisplayMode = { ctx.chatProcessMode },
                        showConnectors = { ctx.chatShowConnectors },
                        showResultCards = { ctx.chatShowResultCards },
                        expandInModal = { ctx.chatExpandInModal },
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
                        copiedMessageId = { ctx.copiedMessageId },
                        colors = { ctx.themeColors },
                        onMessageLongPress = { msg, content, px, py ->
                            ctx.openMessageActions(msg, content, px, py)
                        },
                        onFooterAction = { msg, action -> ctx.onMessageFooterAction(msg, action) },
                         attachmentDataUrl = { ctx.attachmentDataUrl(it) },
                         queueItems = { ctx.queueItems },
                         jobItems = { ctx.jobItems },
                         liveJobItems = { ctx.liveJobItems },
                         goal = { ctx.goalSnapshot },
                        goalActionBusy = { ctx.goalActionBusy },
                        goalActionError = { ctx.goalActionError },
                        onPauseGoal = { ctx.pauseGoal() },
                        onResumeGoal = { ctx.resumeGoal() },
                        onEditGoal = { text, done -> ctx.editGoal(text, done) },
                        onClearGoal = { ctx.clearGoal() },
                        jobsPanelExpanded = { ctx.jobsPanelExpanded },
                        jobsNow = { ctx.jobsNow },
                        onToggleJobsPanel = { ctx.toggleJobsPanel() },
                        queueExpanded = { ctx.queueDockExpanded },
                        queueEditingId = { ctx.queueEditingId },
                        queueActionBusy = { ctx.queueActionBusy },
                        queueEditingText = { ctx.queueEditingText },
                        sessionRunning = { ctx.sessionRunning },
                        isBlankConversation = { ctx.isBlankSession() },
                        conversationListEpoch = { ctx.conversationListEpochFor(it) },
                        turnReconnecting = { isReconnectLabel(ctx.connectionLabel) },
                        turnElapsedMs = { ctx.turnElapsedMs },
                        onToggleQueue = { ctx.queueDockExpanded = !ctx.queueDockExpanded },
                        onEditQueueItem = { ctx.editQueueItem(it) },
                        onQueueEditingTextChange = { ctx.queueEditingText = it },
                        onSaveQueueItem = { ctx.saveQueueItem(it) },
                        onCancelQueueItemEdit = { ctx.cancelQueueItemEdit() },
                        onRemoveQueueItem = { ctx.removeQueueItem(it) },
                        onSteerQueueItem = { ctx.steerQueueItem(it) },
                        pendingApproval = { ctx.pendingApproval },
                        pendingQuestion = { ctx.pendingQuestion },
                        interactionBusy = { ctx.interactionBusy },
                        selectedQuestionOptions = { ctx.selectedQuestionOptions },
                        questionCustom = { ctx.questionCustom },
                        questionIndex = { ctx.questionIndex },
                        questionError = { ctx.questionError },
                        questionHasSelection = { ctx.questionHasSelection },
                        onAnswerApproval = { ctx.answerApproval(it) },
                        onToggleQuestionOption = { ctx.toggleQuestionOption(it) },
                        onQuestionCustomChange = { ctx.updateQuestionCustom(it) },
                        onQuestionNavigate = { ctx.navigateQuestion(it) },
                        onQuestionSkip = { ctx.skipQuestion() },
                        onSubmitQuestion = { ctx.submitQuestion() },
                        onDismissQuestion = { ctx.cancelQuestion() },
                        availableWidth = centerWidth,
                        connectionLabel = { ctx.connectionLabel },
                        connectionCapsuleVisible = { ctx.connectionCapsuleVisible },
                        connectionCapsuleFadeOut = { ctx.connectionCapsuleFadeOut },
                        connectionCapsuleFadeOutAnimation = { ctx.connectionCapsuleFadeOutAnimation },
                        onPreviewImage = { ctx.previewImageUrl = it },
                        previewImageUrl = { ctx.previewImageUrl },
                        onDismissPreview = { ctx.previewImageUrl = null },
                        onSaveImage = { ctx.saveImageToGallery(it) },
                        exportSelectMode = { ctx.exportSelectMode },
                        exportSelectedIds = { ctx.exportSelectedMessageIds() },
                        exportFormat = { ctx.exportFormat },
                        exportSelectedCount = { ctx.exportSelectedCount() },
                        exportMoreShareExpanded = { ctx.exportMoreShareVisible },
                        exportPdfBusy = { ctx.exportPdfBusy },
                        onToggleExportMessage = { ctx.toggleExportMessage(it) },
                        onExportFormatChange = { ctx.exportFormat = it },
                        onExportConfirm = { ctx.confirmExportSelection() },
                        onExportPdf = { ctx.exportSelectionAsPdf() },
                        onExportCopy = { ctx.copyExportSelection() },
                        onExportMore = { ctx.toggleExportMoreShare() },
                        onExportClose = { ctx.cancelExportSelection() },
                    )
                    // -- 右侧「会话详情面板」--：仅远程模式显示，展示当前会话的标题、
                    //    工作目录、模型、运行状态、队列/作业数量。
                    vif({ ctx.isRemoteHost }) {
                        DshSessionDetailsPanel(
                            title = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.title ?: "尚无标题" },
                            cwd = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.cwd ?: "" },
                            modelLabel = { if (ctx.selectedEffortLabel.isEmpty()) ctx.selectedModelLabel else "${ctx.selectedModelLabel} · ${ctx.selectedEffortLabel}" },
                            agentPreset = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.agentPreset.orEmpty() },
                            running = { ctx.sessionRunning },
                            queueCount = { ctx.queueItems.size },
                            jobCount = { ctx.jobItems.size },
                            colors = { ctx.themeColors },
                        )
                    }
                }
            } else {
                // ==== 窄屏（手机）单栏布局 ====
                // 不显示会话栏/详情面板，对话区直接铺满整宽。
                DshConversation(
                    conversationIds = { ctx.conversationPanelIds },
                    activeConversationId = { ctx.activeSessionId },
                    messagesForSession = { ctx.sessionMessageState(it) },
                    streaming = { ctx.streaming },
                    streamingMessageId = { ctx.streamingAssistantId },
                    streamingContent = { ctx.streamingAssistantContent },
                    scrollerRef = { id, ref -> ctx.messageScrollerRefs[id] = ref },
                    messageRef = { sessionId, messageId, ref ->
                        ctx.messageRowRefs[messageRowKey(sessionId, messageId)] = ref
                    },
                    draft = { ctx.draft },
                    skills = { ctx.skills },
                    onPickSkill = { ctx.insertDraftText("/$it ") },
                    keyboardHeight = { ctx.keyboardHeight },
                    stopButtonVisible = { ctx.stopButtonVisible },
                    inputRef = { ctx.onInputViewCreated(it.view) },
                    onInputFocusChange = { ctx.inputFocused = it },
                    onDraftChange = { ctx.draft = it },
                    keyboardAnimation = { ctx.keyboardAnimation },
                    onKeyboardHeightChange = { ctx.updateKeyboard(it) },
                    onSend = { ctx.sendDraft() },
                    onStop = { ctx.stopStream() },
                    onDismissKeyboard = { ctx.dismissKeyboard() },
                    onUserListScroll = { ctx.onConversationUserScroll(it) },
                    modelLabel = { if (ctx.selectedEffortLabel.isEmpty()) ctx.selectedModelLabel else "${ctx.selectedModelLabel} · ${ctx.selectedEffortLabel}" },
                    commandSheetVisible = { ctx.commandSheetVisible },
                    voiceActive = { ctx.voiceUi.active },
                    onOpenModels = { ctx.openModelPicker() },
                    permissionValue = { ctx.permissionValue },
                    permissionLabel = { ctx.permissionLabel },
                    onOpenPermissions = { ctx.openPermissionPicker() },
                    agentModeLabel = { ctx.agentModeLabel },
                    onOpenAgentModes = { ctx.openAgentModePicker() },
                    onToggleCommandSheet = { ctx.toggleCommandSheet() },
                    onPickCommand = { ctx.insertCommand(it) },
                    onAttachmentTile = { ctx.onAttachmentTile(it) },
                    attachmentEpoch = { ctx.attachmentEpoch },
                    attachmentRevision = { ctx.attachmentRevision },
                    pendingImages = { ctx.pendingImages },
                    onRemovePendingImage = { ctx.removePendingImage(it) },
                    onRetryPendingImage = { ctx.retryPendingImage(it) },
                    pendingFiles = { ctx.pendingFiles },
                    onRemovePendingFile = { ctx.removePendingFile(it) },
                    onRetryPendingFile = { ctx.retryPendingFile(it) },
                    onToggleVoice = { ctx.toggleVoice() },
                        voiceRecording = { ctx.voiceUi.recording },
                        voiceCancelArmed = { ctx.voiceUi.cancelArmed },
                        voicePartialText = { ctx.voiceUi.partialText },
                        voiceWaveform = { ctx.voiceWaveformModel.levels },
                        voiceWaveformRevision = { ctx.voiceUi.waveformRevision },
                        voiceStrings = { dshVoiceStrings(ctx.settingsSnapshot.localeValue) },
                        onVoiceHoldStart = { ctx.beginVoiceRecord(it) },
                        onVoiceHoldMove = { ctx.updateVoiceHold(it) },
                        onVoiceHoldEnd = { ctx.endVoiceRecord() },
                    folderLabel = { ctx.composerFolderLabel() },
                    onOpenWorkspacePicker = { ctx.openWorkspacePicker() },
                    isWebTimeline = { ctx.isRemoteHost },
                    processDisplayMode = { ctx.chatProcessMode },
                    showConnectors = { ctx.chatShowConnectors },
                    showResultCards = { ctx.chatShowResultCards },
                    expandInModal = { ctx.chatExpandInModal },
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
                    copiedMessageId = { ctx.copiedMessageId },
                    colors = { ctx.themeColors },
                    onMessageLongPress = { msg, content, px, py ->
                        ctx.openMessageActions(msg, content, px, py)
                    },
                        onFooterAction = { msg, action -> ctx.onMessageFooterAction(msg, action) },
                     attachmentDataUrl = { ctx.attachmentDataUrl(it) },
                     queueItems = { ctx.queueItems },
                     jobItems = { ctx.jobItems },
                     liveJobItems = { ctx.liveJobItems },
                     goal = { ctx.goalSnapshot },
                    goalActionBusy = { ctx.goalActionBusy },
                    goalActionError = { ctx.goalActionError },
                    onPauseGoal = { ctx.pauseGoal() },
                    onResumeGoal = { ctx.resumeGoal() },
                    onEditGoal = { text, done -> ctx.editGoal(text, done) },
                    onClearGoal = { ctx.clearGoal() },
                    jobsPanelExpanded = { ctx.jobsPanelExpanded },
                    jobsNow = { ctx.jobsNow },
                    onToggleJobsPanel = { ctx.toggleJobsPanel() },
                    queueExpanded = { ctx.queueDockExpanded },
                    queueEditingId = { ctx.queueEditingId },
                    queueActionBusy = { ctx.queueActionBusy },
                    queueEditingText = { ctx.queueEditingText },
                    sessionRunning = { ctx.sessionRunning },
                    isBlankConversation = { ctx.isBlankSession() },
                    conversationListEpoch = { ctx.conversationListEpochFor(it) },
                    turnReconnecting = { isReconnectLabel(ctx.connectionLabel) },
                    turnElapsedMs = { ctx.turnElapsedMs },
                    onToggleQueue = { ctx.queueDockExpanded = !ctx.queueDockExpanded },
                    onEditQueueItem = { ctx.editQueueItem(it) },
                    onQueueEditingTextChange = { ctx.queueEditingText = it },
                    onSaveQueueItem = { ctx.saveQueueItem(it) },
                    onCancelQueueItemEdit = { ctx.cancelQueueItemEdit() },
                    onRemoveQueueItem = { ctx.removeQueueItem(it) },
                    onSteerQueueItem = { ctx.steerQueueItem(it) },
                    pendingApproval = { ctx.pendingApproval },
                    pendingQuestion = { ctx.pendingQuestion },
                    interactionBusy = { ctx.interactionBusy },
                    selectedQuestionOptions = { ctx.selectedQuestionOptions },
                    questionCustom = { ctx.questionCustom },
                    questionIndex = { ctx.questionIndex },
                    questionError = { ctx.questionError },
                    questionHasSelection = { ctx.questionHasSelection },
                    onAnswerApproval = { ctx.answerApproval(it) },
                    onToggleQuestionOption = { ctx.toggleQuestionOption(it) },
                    onQuestionCustomChange = { ctx.updateQuestionCustom(it) },
                    onQuestionNavigate = { ctx.navigateQuestion(it) },
                    onQuestionSkip = { ctx.skipQuestion() },
                    onSubmitQuestion = { ctx.submitQuestion() },
                    onDismissQuestion = { ctx.cancelQuestion() },
                    availableWidth = ctx.pagerData.pageViewWidth,
                    connectionLabel = { ctx.connectionLabel },
                    connectionCapsuleVisible = { ctx.connectionCapsuleVisible },
                    connectionCapsuleFadeOut = { ctx.connectionCapsuleFadeOut },
                    connectionCapsuleFadeOutAnimation = { ctx.connectionCapsuleFadeOutAnimation },
                    onPreviewImage = { ctx.previewImageUrl = it },
                    previewImageUrl = { ctx.previewImageUrl },
                    onDismissPreview = { ctx.previewImageUrl = null },
                    onSaveImage = { ctx.saveImageToGallery(it) },
                    exportSelectMode = { ctx.exportSelectMode },
                    exportSelectedIds = { ctx.exportSelectedMessageIds() },
                    exportFormat = { ctx.exportFormat },
                    exportSelectedCount = { ctx.exportSelectedCount() },
                    exportMoreShareExpanded = { ctx.exportMoreShareVisible },
                    exportPdfBusy = { ctx.exportPdfBusy },
                    onToggleExportMessage = { ctx.toggleExportMessage(it) },
                    onExportFormatChange = { ctx.exportFormat = it },
                    onExportConfirm = { ctx.confirmExportSelection() },
                    onExportPdf = { ctx.exportSelectionAsPdf() },
                    onExportCopy = { ctx.copyExportSelection() },
                    onExportMore = { ctx.toggleExportMoreShare() },
                    onExportClose = { ctx.cancelExportSelection() },
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
        vif({ ctx.sessionDrawerVisible }) {
            DshSessionDrawer(
                sessions = { ctx.visibleSessions },
                workspaceGroups = { ctx.workspaceGroups },
                isWebTimeline = { ctx.isRemoteHost },
                activeId = { ctx.activeSessionId },
                animated = { ctx.sessionDrawerAnimated },
                expandedGroupIds = { ctx.workspaceExpandedIds },
                onToggleGroup = { ctx.toggleWorkspaceExpanded(it) },
                sessionPending = { ctx.sessionPending(it) },
                activeWorkspaceId = { ctx.activeWorkspaceId() },
                overflowVisible = { ctx.overflowMenuVisible },
                overflowActions = { ctx.overflowActions() },
                onOverflowSelect = { ctx.onOverflowAction(it) },
                onDismissOverflow = { ctx.closeOverflowMenu() },
                onOpenOverflowFor = { id, x, y -> ctx.openOverflowMenuFor(id, x, y) },
                overflowAnchorX = { ctx.overflowAnchorX },
                overflowAnchorY = { ctx.overflowAnchorY },
                dragState = { ctx.drawerDrag },
                onSessionDragStart = { groupKey, sessionId, index, pageY ->
                    ctx.beginSessionDrag(groupKey, sessionId, index, pageY)
                },
                onWorkspaceDragStart = { workspaceId, index, itemHeight, pageY ->
                    ctx.beginWorkspaceDrag(workspaceId, index, itemHeight, pageY)
                },
                onDragMove = { pageY, heights -> ctx.updateDrawerDrag(pageY, heights) },
                onDragEnd = { ctx.endDrawerDrag() },
                onDragCancel = { ctx.cancelDrawerDrag() },
                viewGroupBy = { ctx.drawerViewGroupBy },
                viewOrderBy = { ctx.drawerViewOrderBy },
                viewOptionsVisible = { ctx.drawerViewOptionsVisible },
                viewOptionsAnchorX = { ctx.drawerViewOptionsX },
                viewOptionsAnchorY = { ctx.drawerViewOptionsY },
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


