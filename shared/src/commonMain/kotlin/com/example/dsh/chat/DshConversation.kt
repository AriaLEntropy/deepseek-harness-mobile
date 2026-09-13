package com.example.dsh.chat

import com.example.dsh.export.DshExportFormat
import com.example.dsh.session.DshGoalSnapshot
import com.example.dsh.attachment.DshImageDraftState
import com.example.dsh.session.DshJobItem
import com.example.dsh.message.DshMessage
import com.example.dsh.interaction.DshPendingApproval
import com.example.dsh.attachment.DshPendingFile
import com.example.dsh.attachment.DshPendingImage
import com.example.dsh.interaction.DshPendingQuestion
import com.example.dsh.interaction.DshPendingQuestionOption
import com.example.dsh.session.DshQueueItem
import com.example.dsh.session.DshSkill
import com.example.dsh.message.dshDisplayedAssistantContent
import com.example.dsh.message.dshShareSelectableIds
import com.example.dsh.message.dshTurnProcessGroup
import com.example.dsh.message.dshTurnTailAssistant
import com.example.dsh.home.CHAT_INITIAL_RENDER_COUNT
import com.example.dsh.home.CHAT_MAX_RENDERED_MESSAGES
import com.example.dsh.home.DshExportSelectionSheet
import com.example.dsh.home.DshHitButton
import com.example.dsh.home.dshPermissionIcon
import com.example.dsh.home.dshPermissionTint
import com.example.dsh.rendering.DshApprovalPanel
import com.example.dsh.rendering.DshDisclosureRow
import com.example.dsh.rendering.DshExpandedPayload
import com.example.dsh.rendering.DshGoalBar
import com.example.dsh.rendering.DshJobsPanel
import com.example.dsh.rendering.DshProcessDisplayMode
import com.example.dsh.rendering.DshQuestionFlow
import com.example.dsh.rendering.DshQuestionFlowView
import com.example.dsh.rendering.DshQueueDock
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.KeyboardParams
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.ScrollParams
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

internal fun ViewContainer<*, *>.DshConversation(
    conversationIds: () -> ObservableList<String>,
    activeConversationId: () -> String,
    messagesForSession: (String) -> ObservableList<DshMessage>,
    streaming: () -> Boolean,
    streamingMessageId: () -> String,
    streamingContent: () -> String,
    scrollerRef: (String, ViewRef<ListView<*, *>>) -> Unit,
    messageRef: (String, String, ViewRef<com.tencent.kuikly.core.views.DivView>) -> Unit,
    draft: () -> String,
    skills: () -> ObservableList<DshSkill>,
    onPickSkill: (String) -> Unit,
    keyboardHeight: () -> Float,
    stopButtonVisible: () -> Boolean,
    keyboardAnimation: () -> Animation,
    inputRef: (com.tencent.kuikly.core.base.ViewRef<TextAreaView>) -> Unit,
    onInputFocusChange: (Boolean) -> Unit,
    onDraftChange: (String) -> Unit,
    onKeyboardHeightChange: (KeyboardParams) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissKeyboard: () -> Unit,
    onUserListScroll: (ScrollParams) -> Unit,
    modelLabel: () -> String,
    commandSheetVisible: () -> Boolean,
    voiceActive: () -> Boolean,
    onOpenModels: () -> Unit,
    onToggleCommandSheet: () -> Unit,
    onPickCommand: (DshCommand) -> Unit,
    onAttachmentTile: (DshCommandSheetTile) -> Unit,
    attachmentEpoch: () -> Int = { 0 },
    attachmentRevision: () -> Int = { 0 },
    pendingImages: () -> ObservableList<DshPendingImage> = { ObservableList() },
    onRemovePendingImage: (String) -> Unit = {},
    onRetryPendingImage: (String) -> Unit = {},
    /** 输入区待发送的通用文件草稿（旧 Host 走 host-plugin 落盘）。 */
    pendingFiles: () -> ObservableList<DshPendingFile> = { ObservableList() },
    onRemovePendingFile: (String) -> Unit = {},
    onRetryPendingFile: (String) -> Unit = {},
    onToggleVoice: () -> Unit,
    /** 录音浮层是否可见（按住说话期间为真）。 */
    voiceRecording: () -> Boolean = { false },
    /** 手指上滑是否已进入取消区间。 */
    voiceCancelArmed: () -> Boolean = { false },
    /** 录音过程中的中间识别文本（用于浮层预览）。 */
    voicePartialText: () -> String = { "" },
    /** 滚动音量窗口（0..1），驱动浮层蓝色方块高度。 */
    voiceWaveform: () -> List<Float> = { emptyList() },
    /** 每次音量采样 +1，用于驱动波形整体重绘。 */
    voiceWaveformRevision: () -> Int = { 0 },
    /** 语音输入文案（按语言偏好本地化）。 */
    voiceStrings: () -> DshVoiceStrings = { dshVoiceStrings("") },
    /** 按住说话：按下（参数为按下点 pageY）。 */
    onVoiceHoldStart: (Float) -> Unit = {},
    /** 按住说话：移动（参数为当前点 pageY）。 */
    onVoiceHoldMove: (Float) -> Unit = {},
    /** 按住说话：松手/取消。 */
    onVoiceHoldEnd: () -> Unit = {},
    folderLabel: () -> String,
    onOpenWorkspacePicker: () -> Unit,
    permissionValue: () -> String,
    permissionLabel: () -> String,
    onOpenPermissions: () -> Unit,
    agentModeLabel: () -> String,
    onOpenAgentModes: () -> Unit,
    isWebTimeline: () -> Boolean,
    /** 过程展示方式：统一折叠（对齐最新 dsh）或经典逐条（对齐 rc）。 */
    processDisplayMode: () -> DshProcessDisplayMode = { DshProcessDisplayMode.UNIFIED },
    /** 工具/思考之间的竖向装饰连接线。 */
    showConnectors: () -> Boolean = { true },
    /** 结构化结果卡片（web 检索/抓取、grep/glob）。 */
    showResultCards: () -> Boolean = { true },
    /** 开启后，点击展开不铺在行内，而是打开底部弹层平铺明细。 */
    expandInModal: () -> Boolean = { false },
    onOpenExpandedModal: (DshExpandedPayload) -> Unit = {},
    isDisclosureExpanded: (String) -> Boolean,
    onToggleDisclosure: (String) -> Unit,
    isJsonNodeExpanded: (String, String) -> Boolean,
    onToggleJsonNode: (String, String) -> Unit,
    onCopyToolContent: (String) -> Unit,
    onCopyMessageContent: (DshMessage) -> Unit = {},
    copiedMessageId: () -> String = { "" },
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    // 参数：message, renderedContent, 页面坐标 x/y
    onMessageLongPress: (DshMessage, String, Float, Float) -> Unit = { _, _, _, _ -> },
    onFooterAction: (DshMessage, DshMessageFooterAction) -> Unit = { _, _ -> },
    attachmentDataUrl: (String) -> String?,
    queueItems: () -> ObservableList<DshQueueItem>,
    jobItems: () -> ObservableList<DshJobItem>,
    liveJobItems: () -> ObservableList<DshJobItem>,
    goal: () -> DshGoalSnapshot?,
    goalActionBusy: () -> Boolean,
    goalActionError: () -> String,
    onPauseGoal: () -> Unit,
    onResumeGoal: () -> Unit,
    onEditGoal: (String, (Boolean) -> Unit) -> Unit,
    onClearGoal: () -> Unit,
    jobsPanelExpanded: () -> Boolean,
    jobsNow: () -> Long,
    onToggleJobsPanel: () -> Unit,
    queueExpanded: () -> Boolean,
    queueEditingId: () -> String,
    queueActionBusy: () -> Boolean,
    queueEditingText: () -> String,
    sessionRunning: () -> Boolean,
    isBlankConversation: () -> Boolean,
    conversationListEpoch: (String) -> Int,
    turnReconnecting: () -> Boolean,
    turnElapsedMs: () -> Long,
    onToggleQueue: () -> Unit,
    onEditQueueItem: (String) -> Unit,
    onQueueEditingTextChange: (String) -> Unit,
    onSaveQueueItem: (String) -> Unit,
    onCancelQueueItemEdit: () -> Unit,
    onRemoveQueueItem: (String) -> Unit,
    onSteerQueueItem: (String) -> Unit,
    pendingApproval: () -> DshPendingApproval?,
    pendingQuestion: () -> DshPendingQuestion?,
    interactionBusy: () -> Boolean,
    selectedQuestionOptions: () -> ObservableList<String>,
    questionCustom: () -> String,
    questionIndex: () -> Int,
    questionError: () -> String,
    questionHasSelection: () -> Boolean,
    onAnswerApproval: (String) -> Unit,
    onToggleQuestionOption: (String) -> Unit,
    onQuestionCustomChange: (String) -> Unit,
    onQuestionNavigate: (Int) -> Unit,
    onQuestionSkip: () -> Unit,
    onSubmitQuestion: () -> Unit,
    onDismissQuestion: () -> Unit,
    availableWidth: Float,
    connectionLabel: () -> String,
    connectionCapsuleVisible: () -> Boolean,
    connectionCapsuleFadeOut: () -> Boolean,
    connectionCapsuleFadeOutAnimation: () -> Animation,
    onPreviewImage: (String) -> Unit = {},
    previewImageUrl: () -> String? = { null },
    onDismissPreview: () -> Unit = {},
    onSaveImage: (String) -> Unit = {},
    // 导出多选态：进入后消息列表可勾选，底部弹窗覆盖输入框选择文件格式
    exportSelectMode: () -> Boolean = { false },
    exportSelectedIds: () -> Set<String> = { emptySet() },
    exportFormat: () -> DshExportFormat = { DshExportFormat.HTML },
    exportSelectedCount: () -> Int = { 0 },
    exportMoreShareExpanded: () -> Boolean = { false },
    exportPdfBusy: () -> Boolean = { false },
    onToggleExportMessage: (String) -> Unit = {},
    onExportFormatChange: (DshExportFormat) -> Unit = {},
    onExportConfirm: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    onExportCopy: () -> Unit = {},
    onExportMore: () -> Unit = {},
    onExportClose: () -> Unit = {},
) {
    // 聊天主界面根容器：整页白色纵向布局（消息区 + 浮动面板 + 输入条）
    View {
        attr {
            flex(1f)
            width(availableWidth)
            flexDirectionColumn()
            backgroundColor(colors().bgBase)
            // 底部预留在输入卡之下渲染工具调用轮次状态区的高度（移动端该状态区暂不常驻
            // 渲染，但需要预留其高度让输入条不贴底、与原版对齐）。
            // 取值= 状态区单行高 26f 偏大，视觉仍显远，收敛到紧凑间距 20f。
            // 页面是 immersive（LAYOUT_FULLSCREEN + STABLE，无 LAYOUT_HIDE_NAVIGATION），
            // 系统已将内容区停在虚拟导航栏上方、pageViewHeight 已扣除导航栏高度，
            // 此时 safeAreaInsets.bottom 仍返回导航栏高度，若再加会双重 padding 把输入条抬高一个导航栏。
            paddingBottom(20f)
        }
        // 消息视口容器：消息列表区域，随键盘高度向上收缩，输入条自然浮在键盘上方
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                // Reduce the conversation viewport when the keyboard opens.
                // The header stays outside this container and the composer
                // naturally settles above the keyboard without translating
                // the list outside its clipping bounds.
                marginBottom(keyboardHeight())
                animation(keyboardAnimation(), keyboardHeight())
            }
            // 消息页容器：承载所有会话页面的层，白色背景
            View {
                attr {
                flex(1f)
                width(availableWidth)
                backgroundColor(colors().bgBase)
            }
            // 单个会话的消息页：按会话 id 叠放，仅激活会话可见可点
            vfor({ conversationIds() }) { sessionId ->
                View {
                    attr {
                        absolutePositionAllZero()
                        width(availableWidth)
                        visibility(true)
                        opacity(if (activeConversationId() == sessionId) 1f else 0f)
                        touchEnable(activeConversationId() == sessionId)
                        zIndex(if (activeConversationId() == sessionId) 1 else 0)
                    }
                    // vfor 的直接子节点不能是 vif/vbind。空 List 先挂载后 addAll
                    // 时 LazyLoop 会把增量当成「加到可见范围后面」而不建 cell。
                    vbind({ conversationListEpoch(sessionId) to colors() }) {
                        // 消息滚动列表：懒加载渲染该会话消息，点击/拖动收起键盘
                        vif({ messagesForSession(sessionId).isNotEmpty() }) {
                            List {
                                ref { scrollerRef(sessionId, it) }
                                attr {
                                    absolutePositionAllZero()
                                    width(availableWidth)
                                    padding(16f, 18f, 20f, 18f)
                                    firstContentLoadMaxIndex(CHAT_INITIAL_RENDER_COUNT)
                                    preloadViewDistance(pagerData.pageViewHeight)
                                }
                                event {
                                    click { onDismissKeyboard() }
                                    dragBegin { params ->
                                        onDismissKeyboard()
                                        onUserListScroll(params)
                                    }
                                    scroll { onUserListScroll(it) }
                                    scrollEnd { onUserListScroll(it) }
                                    register("touchDown", { onDismissKeyboard() })
                                }
                                vforLazy(
                                    { messagesForSession(sessionId) },
                                    maxLoadItem = CHAT_MAX_RENDERED_MESSAGES,
                                // 单条消息行：包一层宽度约束，内部由 DshMessageRow 渲染
                                ) { message, _, _ ->
                                    val processGroup = if (
                                        isWebTimeline() &&
                                        processDisplayMode() == DshProcessDisplayMode.UNIFIED
                                    ) {
                                        dshTurnProcessGroup(messagesForSession(sessionId), message)
                                    } else {
                                        null
                                    }
                                    // 是否给过程项绘制左侧装饰连接线：标题下方按 [竖线 | 内容]
                                    // 两列布局，由 DshDisclosureRow 自身处理（经典/统一模式一致）。
                                    // 单条消息渲染入口；过程分组展开时成员复用同一入口。
                                    // inProcess=true 会锁开成员卡片正文（只读明细），且不显示 footer。
                                    val renderMessage: ViewContainer<*, *>.(DshMessage, Boolean) -> Unit = { target, inProcess ->
                                        DshMessageRow(
                                            target,
                                            pageStreaming = {
                                                !inProcess && streaming() &&
                                                    activeConversationId() == sessionId &&
                                                    streamingMessageId() == target.id
                                            },
                                            isWebTimeline = isWebTimeline(),
                                            isExpanded = { isDisclosureExpanded(target.id) },
                                            onToggle = { onToggleDisclosure(target.id) },
                                            isJsonNodeExpanded = { isJsonNodeExpanded(target.id, it) },
                                            onToggleJsonNode = { onToggleJsonNode(target.id, it) },
                                            onCopyToolContent = { onCopyToolContent(it) },
                                            onCopyMessageContent = { onCopyMessageContent(it) },
                                            copied = { copiedMessageId() == target.id },
                                            colors = { colors() },
                                            onLongPress = { msg, content, px, py ->
                                                onMessageLongPress(msg, content, px, py)
                                            },
                                            onFooterAction = { msg, action -> onFooterAction(msg, action) },
                                            // footer 只渲染"当前回合（最近一条 user 之后）最后一段
                                            // 已结算 assistant"；过程成员一律不渲染，避免重复操作栏。
                                            isTurnTail = {
                                                if (inProcess) {
                                                    false
                                                } else {
                                                    val hasInteraction =
                                                        pendingQuestion()?.sessionId == sessionId ||
                                                            pendingApproval()?.sessionId == sessionId
                                                    if (hasInteraction) {
                                                        false
                                                    } else {
                                                        val tailId = dshTurnTailAssistant(messagesForSession(sessionId))?.id
                                                        tailId != null && tailId == target.id
                                                    }
                                                }
                                            },
                                            attachmentDataUrl = { attachmentDataUrl(it) },
                                            attachmentRevision = { attachmentRevision() },
                                            onPreviewImage = { onPreviewImage(it) },
                                            contentProvider = {
                                                val stored = messagesForSession(sessionId)
                                                    .firstOrNull { it.id == target.id }
                                                    ?.content
                                                    .orEmpty()
                                                dshDisplayedAssistantContent(
                                                    stored = stored,
                                                    live = streamingContent(),
                                                    isLiveRow = !inProcess &&
                                                        streamingMessageId() == target.id &&
                                                        activeConversationId() == sessionId,
                                                )
                                            },
                                            bodyLocked = inProcess,
                                            expandInModal = expandInModal,
                                            onRequestModal = onOpenExpandedModal,
                                            showResultCards = showResultCards,
                                            showConnectors = { isWebTimeline() && showConnectors() },
                                        )
                                    }
                                    View {
                                        ref { messageRef(sessionId, message.id, it) }
                                        attr {
                                            width((availableWidth - 36f).coerceAtLeast(0f))
                                            // 用三元而非 if：Kuikly attr 在条件为 false 时不会清除
                                            // 之前设置过的属性，退出多选态必须显式复位内边距。
                                            // 仅可分享项（用户发言 / 每轮最终回复）才留出勾选框位置。
                                            // 选中态只用左侧勾选框表示，不再给整条消息铺底色。
                                            paddingLeft(if (exportSelectMode() && message.id in dshShareSelectableIds(messagesForSession(sessionId))) 30f else 0f)
                                            borderRadius(if (exportSelectMode() && message.id in dshShareSelectableIds(messagesForSession(sessionId))) 10f else 0f)
                                        }
                                        when {
                                            // 普通消息：直接渲染。
                                            processGroup == null -> this.renderMessage(message, false)
                                            // 过程分组首条：整个过程块放进同一行，避免 vforLazy
                                            // 无法为折叠态零高度的后续行补建视图导致展开不生效。
                                            processGroup.isFirst -> View {
                                                attr {
                                                    width((availableWidth - 36f).coerceAtLeast(0f))
                                                    flexDirectionColumn()
                                                    marginBottom(6f)
                                                }
                                                // 摘要头保持最左，不缩进也不带竖线。
                                                DshDisclosureRow {
                                                    attr {
                                                        title = processGroup.label
                                                        iconAsset = "think.svg"
                                                        this.colors = colors()
                                                        open = isDisclosureExpanded(processGroup.key)
                                                        expandable = true
                                                        this.onToggle = { onToggleDisclosure(processGroup.key) }
                                                        compact = true
                                                        chrome = true
                                                        headerOnly = true
                                                    }
                                                }
                                                // 展开明细：每个思考项的标题下由各自的 [竖线 | 内容]
                                                // 两列布局呈现（见 DshDisclosureRow），摘要头不缩进。
                                                vif({ isDisclosureExpanded(processGroup.key) }) {
                                                    View {
                                                        attr {
                                                            flexDirectionColumn()
                                                            marginTop(6f)
                                                        }
                                                        // 过程明细：成员各自折叠；每个成员正文自行限高
                                                        // 内部滚动、展开箭头固定在滚动区外，故外层不再套
                                                        // 固定高度滚动区（避免嵌套滚动把箭头一起带走）。
                                                        View {
                                                            attr {
                                                                flexDirectionColumn()
                                                                marginBottom(8f)
                                                            }
                                                            processGroup.members.forEach { member ->
                                                                this.renderMessage(member, false)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            // 其余过程成员：始终并入首行分组，本行不渲染。
                                            else -> Unit
                                        }
                                        // 多选态：左侧圆形勾选框 + 覆盖整行的点击热区（不影响列表滚动）
                                        vif({ exportSelectMode() && message.id in dshShareSelectableIds(messagesForSession(sessionId)) }) {
                                            View {
                                                attr {
                                                    positionAbsolute()
                                                    left(0f)
                                                    top(4f)
                                                    size(20f, 20f)
                                                    borderRadius(10f)
                                                    allCenter()
                                                    border(Border(
                                                        1.5f,
                                                        BorderStyle.SOLID,
                                                        if (message.id in exportSelectedIds()) colors().stateBusinessPrimary
                                                        else colors().borderL2,
                                                    ))
                                                    backgroundColor(
                                                        if (message.id in exportSelectedIds()) colors().stateBusinessPrimary
                                                        else Color(0x00FFFFFF),
                                                    )
                                                }
                                                vif({ message.id in exportSelectedIds() }) {
                                                    Image {
                                                        attr {
                                                            src(ImageUri.commonAssets("check.svg"))
                                                            size(13f, 13f)
                                                            tintColor(Color.WHITE)
                                                        }
                                                    }
                                                }
                                            }
                                            View {
                                                attr { absolutePositionAllZero() }
                                                event { click { onToggleExportMessage(message.id) } }
                                            }
                                        }
                                    }
                                }
                                // 回合状态行：当前 turn 进行中的状态提示
                                View {
                                    attr {
                                        width((availableWidth - 36f).coerceAtLeast(0f))
                                    }
                                    DshTurnStatus(
                                        visible = {
                                            activeConversationId() == sessionId &&
                                                (streaming() || stopButtonVisible() || sessionRunning())
                                        },
                                        reconnecting = turnReconnecting,
                                        elapsedMs = turnElapsedMs,
                                        colors = colors,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 空白会话引导：无消息且非运行中时展示 DshNewSessionHome
            vif({
                conversationListEpoch(activeConversationId())
                isBlankConversation() &&
                    messagesForSession(activeConversationId()).isEmpty() &&
                    !streaming() &&
                    !stopButtonVisible() &&
                    !sessionRunning()
            }) {
                DshNewSessionHome(colors = colors)
            }
        }
        // 队列停靠栏（Web 时间线）：展示等待执行的任务队列
        vif({ isWebTimeline() && queueItems().isNotEmpty() }) {
            DshQueueDock {
                attr {
                    items = queueItems()
                    expanded = queueExpanded()
                    editingId = queueEditingId()
                    actionBusy = queueActionBusy()
                    editingText = queueEditingText()
                    running = sessionRunning()
                    onToggle = onToggleQueue
                    onEdit = onEditQueueItem
                    onEditingTextChange = onQueueEditingTextChange
                    onSaveEdit = onSaveQueueItem
                    onCancelEdit = onCancelQueueItemEdit
                    onRemove = onRemoveQueueItem
                    onSteer = onSteerQueueItem
                    this.colors = colors()
                }
            }
        }
        // 任务面板（Web 时间线）：展示后台任务进度
        vif({ isWebTimeline() && liveJobItems().isNotEmpty() }) {
            DshJobsPanel {
                attr {
                    jobs = liveJobItems()
                    expanded = jobsPanelExpanded()
                    now = jobsNow()
                    onToggle = onToggleJobsPanel
                    this.colors = colors()
                }
            }
        }
        // 目标栏（Web 时间线）：展示当前 Agent 目标与暂停/继续操作
        vif({ isWebTimeline() && goal() != null }) {
            DshGoalBar {
                attr {
                    snapshot = goal()
                    busy = goalActionBusy()
                    error = goalActionError()
                    onPause = onPauseGoal
                    onResume = onResumeGoal
                    onEdit = onEditGoal
                    onClear = onClearGoal
                    this.colors = colors()
                }
            }
        }
        // 审批面板：Host 请求授权（如执行命令/改文件）时弹出
        vif({ isWebTimeline() && pendingApproval()?.sessionId == activeConversationId() }) {
            DshApprovalPanel {
                attr {
                    approval = pendingApproval()
                    busy = interactionBusy()
                    onAnswer = onAnswerApproval
                    this.colors = colors()
                }
            }
        }
        // 提问流程面板：Host 向用户提问/选择时弹出。
        // 宽屏（>=720dp）沿用在输入条上方内联渲染，与 dsh Web 一致；
        // 窄屏（手机）改为独立浮动卡片覆盖输入框，而非像聊天消息一样插入会话流。
        val questionInit: DshQuestionFlowView.() -> Unit = {
            attr {
                question = pendingQuestion()
                val options = ObservableList<DshPendingQuestionOption>()
                pendingQuestion()?.questions?.getOrNull(questionIndex())?.options?.let(options::addAll)
                this.options = options
                selected = selectedQuestionOptions()
                custom = questionCustom()
                hasSelection = questionHasSelection()
                index = questionIndex()
                error = questionError()
                busy = interactionBusy()
                onToggleOption = onToggleQuestionOption
                onCustomChange = onQuestionCustomChange
                onNavigate = onQuestionNavigate
                onSkip = onQuestionSkip
                onSubmit = onSubmitQuestion
                onDismiss = onDismissQuestion
                this.onKeyboardHeightChange = onKeyboardHeightChange
                this.colors = colors()
            }
        }
        val questionActive = {
            isWebTimeline() &&
                pendingApproval() == null &&
                pendingQuestion()?.sessionId == activeConversationId()
        }
        vif({ availableWidth >= 720f && questionActive() }) {
            DshQuestionFlow(questionInit)
        }
        vif({ availableWidth < 720f && questionActive() }) {
            // 全屏覆盖层：点击收起键盘，无背景遮罩色，卡片底部与输入框底部对齐
            // ADJUST_RESIZE 模式下键盘弹出时窗口自动收缩，覆盖层底部自然落在键盘上方
            View {
                attr {
                    absolutePositionAllZero()
                    zIndex(50)
                    flexDirectionColumn()
                    justifyContentFlexEnd()
                    paddingTop(58f)
                }
                event { click { onDismissKeyboard() } }
                // 底部间距容器：卡片左右边距
                View {
                    attr {
                        marginLeft(12f)
                        marginRight(12f)
                        marginTop(12f)
                    }
                    DshQuestionFlow(questionInit)
                }
            }
        }

                // Hero 配置区：文件夹 chip + 模式 chip，在输入卡上方，仅空白会话（未开始）时显示
                vif({ isBlankConversation() }) {
                    View {
                        attr {
                            width((availableWidth - 24f).coerceAtLeast(0f))
                            marginLeft(12f)
                            marginRight(12f)
                            flexDirectionRow()
                            alignItemsCenter()
                            marginBottom(8f)
                        }
                        // 文件夹 chip（工作区选择器）：透明药丸，与 DSH Web HeroShell.workspace 一致（无边框，r16，primary 色）
                        View {
                            attr {
                                height(28f)
                                paddingLeft(8f)
                                paddingRight(8f)
                                flexDirectionRow()
                                alignItemsCenter()
                                borderRadius(16f)
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("folder.svg"))
                                    size(16f, 16f)
                                    tintColor(colors().labelPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text(folderLabel())
                                    marginLeft(4f)
                                    fontSize(13f)
                                    color(colors().labelPrimary)
                                }
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("chevron-down.svg"))
                                    size(12f, 12f)
                                    tintColor(colors().labelCaption)
                                }
                            }
                            DshHitButton(onOpenWorkspacePicker)
                        }
                        // 模式 chip：透明药丸，dsh 语义 —— 图标 + 模式名 + 下箭头，点击打开模式选择弹窗
                        View {
                            attr {
                                height(28f)
                                marginLeft(8f)
                                paddingLeft(8f)
                                paddingRight(4f)
                                flexDirectionRow()
                                alignItemsCenter()
                                borderRadius(24f)
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("agent-preset.svg"))
                                    size(16f, 16f)
                                    tintColor(colors().labelTertiary)
                                }
                            }
                            Text {
                                attr {
                                    text(agentModeLabel())
                                    marginLeft(4f)
                                    fontSize(13f)
                                    color(colors().labelTertiary)
                                }
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("chevron-down.svg"))
                                    size(12f, 12f)
                                    tintColor(colors().labelCaption)
                                }
                            }
                            DshHitButton(onOpenAgentModes)
                        }
                    }
                }

                // 输入卡：DSH Web 风格，细描边 + 弥散阴影，10px 顶部内边距。
                // 左右对称 margin 出 clearance（各 12px），宽度扣减 24 避免右侧溢出截断。
                // 弹出提问流程面板时隐藏输入框（与 DSH 原版一致），由浮动卡片接管底部交互区
                // 导出多选态下输入卡让位给底部格式弹窗。
                vif({ !questionActive() && !exportSelectMode() }) {
                View {
                    attr {
                        width((availableWidth - 24f).coerceAtLeast(0f))
                        marginLeft(12f)
                        marginRight(12f)
                        flexDirectionColumn()
                        paddingTop(10f)
                        backgroundColor(colors().specificInputMajor)
                        borderRadius(22f)
                        border(Border(1f, BorderStyle.SOLID, colors().borderL2))
                        boxShadow(BoxShadow(0f, 4f, 12f, Color(0x0D000000)))
                    }
                    // 附件预览条：输入框上方横向滚动；超限/失败项带状态遮罩，可移除，失败可点按重试
                    // vbind 订阅 attachmentEpoch，add/remove/retry 等变化强制重建此子树，
                    // 重建时 vfor 重新求值读最新 pendingImages。
                    vbind({ attachmentEpoch() }) {
                        vif({ pendingImages().isNotEmpty() }) {
                        View {
                            attr {
                                width((availableWidth - 24f).coerceAtLeast(0f))
                                flexDirectionRow()
                                height(60f)
                                padding(2f, 10f, 2f, 0f)
                            }
                            vfor({ pendingImages() }) { image ->
                                                                View {
                                    attr {
                                        size(56f, 56f)
                                        marginRight(8f)
                                        borderRadius(8f)
                                        backgroundColor(colors().bgModulePlatform)
                                        border(Border(1f, BorderStyle.SOLID, colors().borderL1))
                                    }
                                    Image {
                                        attr {
                                            src(image.previewDataUrl)
                                            width(56f)
                                            height(56f)
                                            resizeCover()
                                        }
                                    }
                                    vif({ image.isUploading }) {
                                        View {
                                            attr {
                                                absolutePositionAllZero()
                                                backgroundColor(Color(0x66000000))
                                                allCenter()
                                            }
                                            Text {
                                                attr {
                                                    text("发送中")
                                                    fontSize(10f)
                                                    color(Color(0xFFFFFFFF))
                                                }
                                            }
                                        }
                                    }
                                    vif({ image.isInvalid || image.state == DshImageDraftState.FAILED }) {
                                        View {
                                            attr {
                                                absolutePositionAllZero()
                                                backgroundColor(Color(0x4D000000))
                                                allCenter()
                                            }
                                            Text {
                                                attr {
                                                    text(if (image.state == DshImageDraftState.FAILED) "失败" else "超限")
                                                    fontSize(10f)
                                                    color(Color(0xFFFFFFFF))
                                                }
                                            }
                                            event { click { onRetryPendingImage(image.clientId) } }
                                        }
                                    }
                                    View {
                                        attr {
                                            positionType(FlexPositionType.ABSOLUTE)
                                            top(2f)
                                            right(2f)
                                            size(18f, 18f)
                                            borderRadius(9f)
                                            backgroundColor(Color(0x99000000))
                                            allCenter()
                                        }
                                        Image {
                                            attr {
                                                src(ImageUri.commonAssets("x.svg"))
                                                size(10f, 10f)
                                                tintColor(Color.WHITE)
                                            }
                                        }
                                        DshHitButton { onRemovePendingImage(image.clientId) }
                                    }
                                }
                            }
                        }
                        }
                        vif({ pendingFiles().isNotEmpty() }) {
                            Scroller {
                                attr {
                                    width((availableWidth - 24f).coerceAtLeast(0f))
                                    height(64f)
                                    flexDirection(FlexDirection.ROW)
                                    paddingLeft(10f)
                                    paddingRight(12f)
                                    showScrollerIndicator(false)
                                    scrollWithParent(false)
                                }
                                vfor({ pendingFiles() }) { file ->
                                    DshDraftFileCard(
                                        file = file,
                                        onRemove = { onRemovePendingFile(file.clientId) },
                                        onRetry = { onRetryPendingFile(file.clientId) },
                                        colors = colors,
                                    )
                                }
                            }
                        }
                    }
                    // 主输入框：仅文字模式渲染；语音模式整块卸载，避免残留占位
                    vif({ !voiceActive() }) {
                    TextArea {
                        ref { inputRef(it) }
                        attr {
                            marginLeft(16f)
                            marginRight(12f)
                            minHeight(46f)
                            maxHeight(120f) // 约 5 行上限，超出后内部滚动
                            backgroundColor(Color(0x00FFFFFF))
                            fontSize(15f)
                            color(colors().labelPrimary)
                            placeholder(voiceStrings().inputPlaceholder)
                            placeholderColor(colors().labelTertiary)
                        }
                        event {
                            inputFocus { onInputFocusChange(true) }
                            textDidChange { onDraftChange(it.text) }
                            keyboardHeightChange { onKeyboardHeightChange(it) }
                            inputBlur {
                                onInputFocusChange(false)
                                onKeyboardHeightChange(KeyboardParams(0f, 0.24f))
                            }
                        }
                    }
                    }
                    // 语音模式：整行「按住说话」，按下即开始识别，松手发送、上滑取消
                    vif({ voiceActive() }) {
                        View {
                            attr {
                                marginLeft(16f)
                                marginRight(12f)
                                height(50f)
                                allCenter()
                            }
                            Text {
                                attr {
                                    text(
                                        if (voiceRecording()) voiceStrings().recordingReleaseToEnd
                                        else voiceStrings().holdToTalk,
                                    )
                                    fontSize(16f)
                                    fontWeightMedium()
                                    color(colors().labelPrimary)
                                }
                            }
                            event {
                                register("touchDown", { onVoiceHoldStart(TouchParams.decode(it).pageY) }, true)
                                register("touchMove", { onVoiceHoldMove(TouchParams.decode(it).pageY) }, true)
                                register("touchUp", { onVoiceHoldEnd() }, true)
                                register("touchCancel", { onVoiceHoldEnd() }, true)
                            }
                        }
                        // 浅灰细线：把「按住说话」区与下方工具栏分割开
                        View {
                            attr {
                                height(0.5f)
                                marginLeft(16f)
                                marginRight(12f)
                                backgroundColor(colors().borderL2)
                            }
                        }
                    }

                    // 底部工具栏：flex wrap 布局，与 DSH Web 一致（左侧 + 按钮，右侧 模型 chip + 发送按钮）
                    View {
                        attr {
                            flexDirectionRow()
                            flexWrap(FlexWrap.WRAP)
                            alignItemsCenter()
                            justifyContentSpaceBetween()
                            padding(2f, 8f, 6f, 8f)
                        }
                        // 左侧功能区：+ 按钮
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            // + 按钮：32px 圆，浅灰背景，与 DSH Web 一致
                            View {
                                attr {
                                    size(32f, 32f)
                                    borderRadius(999f)
                                    backgroundColor(colors().specificSelector)
                                    allCenter()
                                }
                                Image { attr { src(ImageUri.commonAssets("plus.svg")); size(16f, 16f); tintColor(colors().labelPrimary) } }
                                DshHitButton { onToggleCommandSheet() }
                            }
                            // 权限 chip：dsh 语义 —— 当前权限态盾牌图标 + 下箭头，会话开始前可选（仅图标，文字在弹窗内）
                            vif({ isBlankConversation() }) {
                                View {
                                    attr {
                                        marginLeft(8f)
                                        height(32f)
                                        paddingLeft(14f)
                                        paddingRight(5f)
                                        flexDirectionRow()
                                        alignItemsCenter()
                                        borderRadius(24f)
                                    }
                                    Image {
                                        attr {
                                            src(ImageUri.commonAssets(dshPermissionIcon(permissionValue())))
                                            size(18f, 18f)
                                            tintColor(dshPermissionTint(permissionValue(), false, colors()))
                                        }
                                    }
                                    Image {
                                        attr {
                                            src(ImageUri.commonAssets("chevron-down.svg"))
                                            size(13f, 13f)
                                            tintColor(colors().labelCaption)
                                        }
                                    }
                                    DshHitButton(onOpenPermissions)
                                }
                            }
                        }
                        // 右侧功能区：模型 chip + 停止/发送按钮
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            // 模型 chip：与 DSH Web 一致的透明药丸样式
                            View {
                                attr {
                                    maxWidth(220f)
                                    height(32f)
                                    paddingLeft(8f)
                                    paddingRight(20f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    borderRadius(8f)
                                }
                                Text {
                                    attr {
                                        text(modelLabel())
                                        fontSize(14f)
                                        color(colors().labelTertiary)
                                    }
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("chevron-down.svg"))
                                        size(13f, 13f)
                                        tintColor(colors().labelCaption)
                                    }
                                }
                                DshHitButton(onOpenModels)
                            }
                            // 右侧按钮：停止 / 语音-键盘模式切换 / 发送
                            // - 生成中：红色停止圆钮
                            // - 语音模式：键盘图标（切回文字输入）
                            // - 有草稿或附件：蓝色发送圆钮
                            // - 空输入：麦克风图标（进入语音输入）
                            vif({ stopButtonVisible() }) {
                                // 外层固定 34 与语音/键盘按钮同高，避免文字态/空态切换时输入卡高度跳动
                                View {
                                    attr {
                                        size(34f, 34f)
                                        allCenter()
                                        transform(translate = Translate(percentageX = 0f, percentageY = 0f, offsetY = -2f))
                                    }
                                    View {
                                        attr {
                                            size(30f, 30f)
                                            borderRadius(999f)
                                            allCenter()
                                            backgroundColor(colors().stateErrorPrimary)
                                        }
                                        Image { attr { src(ImageUri.commonAssets("square.svg")); size(14f, 14f); tintColor(Color.WHITE) } }
                                    }
                                    DshHitButton { onStop() }
                                }
                            }
                            velse {
                                vif({ voiceActive() }) {
                                    // 语音模式：键盘按钮，切回文字输入
                                    View {
                                        attr {
                                            size(34f, 34f)
                                            allCenter()
                                            transform(translate = Translate(percentageX = 0f, percentageY = 0f, offsetY = -2f))
                                        }
                                        Image {
                                            attr {
                                                src(ImageUri.commonAssets("keyboard.svg"))
                                                size(30f, 30f)
                                                tintColor(colors().labelSecondary)
                                            }
                                        }
                                        DshHitButton { onToggleVoice() }
                                    }
                                }
                                velse {
                                    vif({
                                        draft().trim().isNotEmpty() ||
                                            pendingImages().isNotEmpty() ||
                                            pendingFiles().isNotEmpty()
                                    }) {
                                        // 蓝色发送圆钮：外层 34 与语音/键盘按钮同高
                                        View {
                                            attr {
                                                size(34f, 34f)
                                                allCenter()
                                                transform(translate = Translate(percentageX = 0f, percentageY = 0f, offsetY = -2f))
                                            }
                                            View {
                                                attr {
                                                    size(30f, 30f)
                                                    borderRadius(999f)
                                                    allCenter()
                                                    backgroundColor(colors().buttonInfoFill)
                                                }
                                                Image { attr { src(ImageUri.commonAssets("arrow-up.svg")); size(16f, 16f); tintColor(Color.WHITE) } }
                                            }
                                            DshHitButton { onSend() }
                                        }
                                    }
                                    velse {
                                        // 空输入：麦克风按钮，进入语音输入
                                        View {
                                            attr {
                                                size(34f, 34f)
                                                allCenter()
                                                transform(translate = Translate(percentageX = 0f, percentageY = 0f, offsetY = -2f))
                                            }
                                            Image {
                                                attr {
                                                    src(ImageUri.commonAssets("mic.svg"))
                                                    size(30f, 30f)
                                                    tintColor(colors().labelSecondary)
                                                }
                                            }
                                            DshHitButton { onToggleVoice() }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
                }

                // 导出多选态底部弹窗：无遮罩，占据输入框位置，提供计数、关闭、文件格式选择与分享操作
                vif({ exportSelectMode() }) {
                    DshExportSelectionSheet(
                        selectedCount = exportSelectedCount,
                        moreExpanded = exportMoreShareExpanded,
                        format = exportFormat,
                        pdfBusy = exportPdfBusy,
                        onPickFormat = onExportFormatChange,
                        onGeneratePdf = onExportPdf,
                        onCopyContent = onExportCopy,
                        onMoreShare = onExportMore,
                        onConfirm = onExportConfirm,
                        onClose = onExportClose,
                        colors = colors,
                    )
                }

        }
        // 语音录音浮层：按住说话时贴底显示，提示 + 实时音量波形
        DshVoiceRecordOverlay(
            visible = voiceRecording,
            cancelArmed = voiceCancelArmed,
            partialText = voicePartialText,
            levels = voiceWaveform,
            revision = voiceWaveformRevision,
            strings = voiceStrings,
            colors = colors,
        )

        // 命令 / 技能建议浮层：absolute 悬浮在输入框上方，命令在上技能在下，白卡圆角阴影，不参与流式布局
        vif({
            draft().startsWith("/") && (
                dshCommandsMatching(dshCommandPrefixFromDraft(draft()).removePrefix("/")).isNotEmpty() ||
                    visibleSkillList(skills(), draft().removePrefix("/")).isNotEmpty()
            )
        }) {
            val prefix = dshCommandPrefixFromDraft(draft()).removePrefix("/")
            val commandList = dshCommandsMatching(prefix)
            val skillList = visibleSkillList(skills(), draft().removePrefix("/"))
            View {
                attr {
                    positionAbsolute()
                    left(16f)
                    right(12f)
                    // 底部对齐「选择文件夹与模式」工具条上方：输入区被 marginBottom(keyboardHeight) 顶起，
                    // 故组件底需叠加键盘高度并上移到真实输入卡顶(~120)之上留间隙，避免与输入框重合
                    bottom(keyboardHeight() + 130f)
                    height(336f)
                    flexDirectionColumn()
                    backgroundColor(colors().specificMenu)
                    borderRadius(14f)
                    boxShadow(BoxShadow(0f, 4f, 12f, Color(0x1A000000)))
                    zIndex(12)
                }
                Scroller {
                    attr {
                        flex(1f)
                        flexDirectionColumn()
                    }
                    // 「命令」分组标题：只有命中命令时显示
                vif({ commandList.isNotEmpty() }) {
                    View {
                        attr {
                            paddingLeft(16f)
                            paddingTop(10f)
                            paddingBottom(6f)
                            flexDirectionColumn()
                        }
                        Text {
                            attr {
                                text("命令")
                                fontSize(12f)
                                color(colors().labelTertiary)
                            }
                        }
                    }
                }
                vfor({ ObservableList<DshCommand>().apply { addAll(commandList) } }) { command ->
                    View {
                        attr {
                            height(40f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(16f)
                            paddingRight(12f)
                        }
                        event { click { onPickCommand(command) } }
                        Text {
                            attr {
                                text("/${command.name}")
                                maxWidth(110f)
                                marginRight(10f)
                                lines(1)
                                fontSize(14f)
                                fontWeightMedium()
                                color(colors().labelPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(command.description)
                                flex(1f)
                                lines(1)
                                fontSize(13f)
                                color(colors().labelTertiary)
                            }
                        }
                    }
                }
                // 「技能」分组标题：只有命中技能时显示
                vif({ skillList.isNotEmpty() }) {
                    View {
                        attr {
                            paddingLeft(16f)
                            paddingTop(8f)
                            paddingBottom(6f)
                            flexDirectionColumn()
                        }
                        Text {
                            attr {
                                text("技能")
                                fontSize(12f)
                                color(colors().labelTertiary)
                            }
                        }
                    }
                }
                vfor({ skillList }) { skill ->
                    View {
                        attr {
                            height(40f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(16f)
                            paddingRight(12f)
                        }
                        event { click { onPickSkill(skill.name) } }
                        Text {
                            attr {
                                text("/${skill.name}")
                                maxWidth(110f)
                                marginRight(10f)
                                lines(1)
                                fontSize(14f)
                                fontWeightMedium()
                                color(colors().labelPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(if (skill.modelInvocable) skill.description else "用户专用 · ${skill.description}")
                                flex(1f)
                                lines(1)
                                fontSize(13f)
                                color(colors().labelTertiary)
                            }
                        }
                    }
                }
            }
            }
        }
        // 连接状态胶囊：浮在输入卡上方，仅非已连接时显示，不参与流式布局
        DshConnectionStatusCapsule(
                    visible = { connectionCapsuleVisible() },
                    connectionLabel = connectionLabel,
                    isBlankConversation = isBlankConversation,
                    fadeOut = { connectionCapsuleFadeOut() },
                    fadeOutAnimation = { connectionCapsuleFadeOutAnimation() },
                    colors = colors,
                )

        // 「+」命令半屏面板：三个附件方块 + 原版命令列表，点击命令写入输入框
        DshCommandSheet(
            visible = commandSheetVisible,
            onClose = onToggleCommandSheet,
            onPickCommand = onPickCommand,
            onPickTile = onAttachmentTile,
            colors = colors,
        )

        // 图片全屏预览层：点击消息图片后显示，点击背景关闭
        vif({ previewImageUrl() != null }) {
            View {
                attr {
                    positionAbsolute()
                    top(0f)
                    left(0f)
                    right(0f)
                    bottom(0f)
                    zIndex(1000)
                }
                // 背景层：点击关闭
                View {
                    attr {
                        positionAbsolute()
                        top(0f)
                        left(0f)
                        right(0f)
                        bottom(0f)
                        backgroundColor(Color(0xCC000000))
                    }
                    event {
                        click { onDismissPreview() }
                    }
                }
                // 内容层：图片+保存按钮，不响应点击关闭
                View {
                    attr {
                        positionAbsolute()
                        top(0f)
                        left(0f)
                        right(0f)
                        bottom(0f)
                        justifyContent(FlexJustifyContent.CENTER)
                        alignItems(FlexAlign.CENTER)
                    }
                    View {
                        attr {
                            flexDirectionColumn()
                            alignItems(FlexAlign.CENTER)
                        }
                        Image {
                            attr {
                                src(previewImageUrl() ?: "")
                                width(availableWidth - 32f)
                                height(availableWidth - 32f)
                                resizeContain()
                            }
                        }
                        // 保存按钮
                        View {
                            attr {
                                marginTop(24f)
                                paddingLeft(24f)
                                paddingRight(24f)
                                paddingTop(10f)
                                paddingBottom(10f)
                                backgroundColor(Color(0x33FFFFFF))
                                borderRadius(20f)
                            }
                            event {
                                click {
                                    val url = previewImageUrl()
                                    if (url != null) onSaveImage(url)
                                }
                            }
                            Text {
                                attr {
                                    text("保存到相册")
                                    fontSize(14f)
                                    color(Color(0xFFFFFFFF.toInt()))
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}
