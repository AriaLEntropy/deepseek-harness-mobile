package com.example.dsh.chat

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.layout.FlexAlign
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

// AI 回答下方横向操作容器（footer）的可用操作项，对齐 dsh 原版 IconActions 行
internal enum class DshMessageFooterAction { COPY, GOOD, BAD, BRANCH, SHARE }

internal fun ViewContainer<*, *>.DshTurnStatus(
    visible: () -> Boolean,
    reconnecting: () -> Boolean,
    elapsedMs: () -> Long,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    // 回合状态条：思考中/重连中提示 + 已耗时
    vif({ visible() }) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                height(26f)
                marginTop(4f)
                marginBottom(8f)
            }
            Text {
                attr {
                    text(dshTurnStatusLabel(reconnecting()))
                    fontSize(14f)
                    fontWeightBold()
                    color(colors().stateBusinessPrimary)
                }
            }
            vif({ elapsedMs() >= TURN_STATUS_CLOCK_AFTER_MS }) {
                Text {
                    attr {
                        text(dshFormatTurnDuration(elapsedMs()))
                        fontSize(13f)
                        color(colors().labelTertiary)
                        marginLeft(8f)
                    }
                }
            }
        }
    }
}

internal const val TURN_STATUS_BLUE = 0xFF4D6BFE
internal const val TURN_STATUS_CLOCK_AFTER_MS = 15_000L

// 空白会话首页：无消息时的占位引导（logo + 标语 + 预览版徽标）
internal fun ViewContainer<*, *>.DshNewSessionHome(colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light }) {
    View {
        attr {
            absolutePositionAllZero()
            allCenter()
            zIndex(2)
            paddingLeft(28f)
            paddingRight(28f)
        }
        event {
            click { }
        }
        View {
            attr {
                flexDirectionColumn()
                alignItemsCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("fish.svg"))
                    size(56f, 56f)
                    tintColor(if (colors().isDark) Color.WHITE else null)
                }
            }
            View {
                attr {
                    marginTop(16f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text("探索未至之境")
                        fontSize(26f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View {
                    attr {
                        marginLeft(8f)
                        paddingLeft(8f)
                        paddingRight(8f)
                        height(22f)
                        allCenter()
                        borderRadius(11f)
                        backgroundColor(colors().stateBusinessTertiary)
                    }
                    Text {
                        attr {
                            text("预览版")
                            fontSize(11f)
                            fontWeightMedium()
                            color(colors().stateBusinessPrimary)
                        }
                    }
                }
            }
        }
    }
}

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
    onToggleVoice: () -> Unit,
    folderLabel: () -> String,
    onOpenFolderBrowser: () -> Unit,
    permissionValue: () -> String,
    permissionLabel: () -> String,
    onOpenPermissions: () -> Unit,
    agentModeLabel: () -> String,
    onOpenAgentModes: () -> Unit,
    isWebTimeline: () -> Boolean,
    isDisclosureExpanded: (String) -> Boolean,
    onToggleDisclosure: (String) -> Unit,
    isBodyDisclosureExpanded: (String) -> Boolean,
    onToggleBodyDisclosure: (String) -> Unit,
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
    exportFormat: () -> DshExportFormat = { DshExportFormat.TXT },
    exportSelectedCount: () -> Int = { 0 },
    onToggleExportMessage: (String) -> Unit = {},
    onExportFormatChange: (DshExportFormat) -> Unit = {},
    onExportConfirm: () -> Unit = {},
    onExportCancel: () -> Unit = {},
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
                                    val processGroup = if (isWebTimeline()) {
                                        dshTurnProcessGroup(messagesForSession(sessionId), message)
                                    } else {
                                        null
                                    }
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
                                            isBodyExpanded = { isBodyDisclosureExpanded(target.id) },
                                            onToggleBody = { onToggleBodyDisclosure(target.id) },
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
                                        )
                                    }
                                    View {
                                        ref { messageRef(sessionId, message.id, it) }
                                        attr {
                                            width((availableWidth - 36f).coerceAtLeast(0f))
                                            // 用三元而非 if：Kuikly attr 在条件为 false 时不会清除
                                            // 之前设置过的属性，退出多选态必须显式复位背景与内边距。
                                            // 仅可分享项（用户发言 / 每轮最终回复）才留出勾选框位置。
                                            paddingLeft(if (exportSelectMode() && message.id in dshShareSelectableIds(messagesForSession(sessionId))) 30f else 0f)
                                            borderRadius(if (exportSelectMode() && message.id in dshShareSelectableIds(messagesForSession(sessionId))) 10f else 0f)
                                            backgroundColor(
                                                if (exportSelectMode() && message.id in exportSelectedIds()) {
                                                    colors().stateBusinessTertiary
                                                } else {
                                                    Color(0x00FFFFFF)
                                                },
                                            )
                                        }
                                        when {
                                            // 普通消息：直接渲染。
                                            processGroup == null -> this.renderMessage(message, false)
                                            // 过程分组首条：整个过程块放进同一行，避免 vforLazy
                                            // 无法为折叠态零高度的后续行补建视图导致展开不生效。
                                            processGroup.isFirst -> View {
                                                attr {
                                                    width((availableWidth - 36f).coerceAtLeast(0f))
                                                    marginBottom(6f)
                                                }
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
                                                // 明细与表头同处一行，限高并可内部滚动；成员沿用各自
                                                // 卡片（Think 已限高、工具自带滚动），宽度与列表行一致。
                                                vif({ isDisclosureExpanded(processGroup.key) }) {
                                                    Scroller {
                                                        attr {
                                                            height(360f)
                                                            marginTop(6f)
                                                        }
                                                        View {
                                                            attr {
                                                                flexDirectionColumn()
                                                                marginBottom(8f)
                                                            }
                                                            processGroup.members.forEach { member ->
                                                                this.renderMessage(member, true)
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
                            DshHitButton(onOpenFolderBrowser)
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
                    }
                    // 输入框：DSH Web 风格，内容自适应高度（单行起），达 maxHeight 后随输入内部滚动
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
                            placeholder(
                                when {
                                    voiceActive() -> "正在聆听..."
                                    else -> "发消息或按住说话，让电脑继续工作..."
                                },
                            )
                            placeholderColor(colors().labelTertiary)
                            editable(!voiceActive())
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
                            // + 按钮：28px 圆，浅灰背景，与 DSH Web 一致
                            View {
                                attr {
                                    size(28f, 28f)
                                    borderRadius(999f)
                                    backgroundColor(colors().specificSelector)
                                    allCenter()
                                }
                                Image { attr { src(ImageUri.commonAssets("plus.svg")); size(14f, 14f); tintColor(colors().labelPrimary) } }
                                DshHitButton { onToggleCommandSheet() }
                            }
                            // 权限 chip：dsh 语义 —— 当前权限态盾牌图标 + 下箭头，会话开始前可选（仅图标，文字在弹窗内）
                            vif({ isBlankConversation() }) {
                                View {
                                    attr {
                                        marginLeft(6f)
                                        height(28f)
                                        paddingLeft(13f)
                                        paddingRight(4f)
                                        flexDirectionRow()
                                        alignItemsCenter()
                                        borderRadius(24f)
                                    }
                                    Image {
                                        attr {
                                            src(ImageUri.commonAssets(dshPermissionIcon(permissionValue())))
                                            size(16f, 16f)
                                            tintColor(dshPermissionTint(permissionValue(), false, colors()))
                                        }
                                    }
                                    Image {
                                        attr {
                                            src(ImageUri.commonAssets("chevron-down.svg"))
                                            size(12f, 12f)
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
                                    height(28f)
                                    paddingLeft(8f)
                                    paddingRight(20f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    borderRadius(8f)
                                }
                                Text {
                                    attr {
                                        text(modelLabel())
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
                                DshHitButton(onOpenModels)
                            }
                            // 停止/发送按钮：34px 蓝色圆钮，白↑箭头，与 DSH Web 一致
                            View {
                                attr {
                                    size(34f, 34f)
                                    borderRadius(999f)
                                    allCenter()
                                    backgroundColor(
                                        if (stopButtonVisible()) {
                                            colors().stateErrorPrimary
                                        } else {
                                            colors().buttonInfoFill
                                        },
                                    )
                                    opacity(
                                        if (stopButtonVisible() || draft().trim().isNotEmpty()) 1f
                                        else 0.4f
                                    )
                                    transform(translate = Translate(percentageX = 0f, percentageY = 0f, offsetY = -2f))
                                }
                                vif({ stopButtonVisible() }) {
                                    Image { attr { src(ImageUri.commonAssets("square.svg")); size(16f, 16f); tintColor(Color.WHITE) } }
                                }
                                velse {
                                    Image { attr { src(ImageUri.commonAssets("arrow-up.svg")); size(16f, 16f); tintColor(Color.WHITE) } }
                                }
                                DshHitButton {
                                    when {
                                        stopButtonVisible() -> onStop()
                                        draft().trim().isNotEmpty() || pendingImages().isNotEmpty() -> onSend()
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
                        format = exportFormat,
                        onPickFormat = onExportFormatChange,
                        onClose = onExportCancel,
                        onConfirm = onExportConfirm,
                        colors = colors,
                    )
                }

        }
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

internal fun ViewContainer<*, *>.DshMessageRow(
    message: DshMessage,
    pageStreaming: () -> Boolean,
    isWebTimeline: Boolean,
    isExpanded: () -> Boolean,
    onToggle: () -> Unit,
    isBodyExpanded: () -> Boolean = { false },
    onToggleBody: () -> Unit = {},
    isJsonNodeExpanded: (String) -> Boolean = { false },
    onToggleJsonNode: (String) -> Unit = {},
    onCopyToolContent: (String) -> Unit = {},
    onCopyMessageContent: (DshMessage) -> Unit = {},
    copied: () -> Boolean = { false },
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    onLongPress: (DshMessage, String, Float, Float) -> Unit = { _, _, _, _ -> },
    onFooterAction: (DshMessage, DshMessageFooterAction) -> Unit = { _, _ -> },
    isTurnTail: () -> Boolean = { true },
    attachmentDataUrl: (String) -> String? = { null },
    attachmentRevision: () -> Int = { 0 },
    onPreviewImage: (String) -> Unit = {},
    contentProvider: (() -> String)? = null,
    /** 回合过程分组展开时，成员卡片直接铺开、不可再折叠。 */
    bodyLocked: Boolean = false,
) {
    if (message.hidden) return
    val isUser = message.role == DshMessageRole.USER
    val isError = message.role == DshMessageRole.ERROR
    val renderedContent = contentProvider?.invoke() ?: message.content
    if (
        message.role == DshMessageRole.ASSISTANT &&
        !message.isReasoning &&
        pageStreaming() &&
        renderedContent.isEmpty()
    ) {
        return
    }
    // 上下文注入卡片：Web 时间线下展示注入到上下文的资料（可展开）
    // 与 dsh web 对齐：展开后直接显示全文，不做 body 二次折叠；recall 类型标题为“上下文回忆”。
    if (isWebTimeline && message.isContextInjection) {
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                marginBottom(6f)
            }
            DshDisclosureRow {
                attr {
                    title = if (message.contextForm == "recall") "上下文回忆" else "上下文注入"
                    iconAsset = "context.svg"
                    this.colors = colors()
                    summary = message.toolName.orEmpty()
                    body = ""
                    open = bodyLocked || isExpanded()
                    expandable = !bodyLocked && message.contextCanExpand()
                    this.onToggle = onToggle
                    bodyCollapsible = false
                    compact = true
                    bodyChrome = true
                    bodyMaxHeight = 300f
                    contextDetail = DshContextDetail(
                        form = message.contextForm,
                        body = boundedContextText(message.contextBody),
                        catalog = message.contextCatalog,
                        sections = message.contextSections,
                        recalls = message.contextRecalls,
                        instructions = message.contextInstructions,
                        relaySender = message.contextRelaySender,
                    )
                }
            }
        }
        return
    }
    // 附件图片卡片：消息附带的上传图片，加载中显示占位文案
    if (isWebTimeline && message.attachmentId != null) {
        val dataUrl = attachmentDataUrl(message.attachmentId)
        View {
            attr {
                width((pagerData.pageViewWidth - 36f).coerceAtLeast(0f))
                height(220f)
                marginBottom(12f)
                borderRadius(8f)
                backgroundColor(colors().bgModulePlatform)
                border(Border(1f, BorderStyle.SOLID, colors().borderL1))
                justifyContentCenter()
                alignItemsCenter()
            }
            if (dataUrl != null) {
                Image {
                    attr {
                        src(dataUrl)
                        width((pagerData.pageViewWidth - 40f).coerceAtLeast(0f))
                        height(216f)
                        resizeCover()
                    }
                }
            } else {
                Text {
                    attr {
                        text("图片加载中")
                        fontSize(12f)
                        color(colors().labelTertiary)
                    }
                }
            }
        }
        return
    }
    // 推理过程卡片（Think）：展示模型思考摘要，可展开全文
    if (isWebTimeline && message.isReasoning) {
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                marginBottom(12f)
            }
            DshDisclosureRow {
                attr {
                    title = "Think"
                    iconAsset = "think.svg"
                    this.colors = colors()
                    summary = message.content.dshReasoningSummary(message.streaming)
                    body = message.content
                    open = bodyLocked || isExpanded()
                    expandable = !bodyLocked && message.content.isNotEmpty()
                    this.onToggle = onToggle
                    plainBody = true
                    compact = true
                    bodyChrome = true
                    // 长思考限高并提供内部滚动，避免展开后撑爆消息列表。
                    bodyMaxHeight = 320f
                }
            }
        }
        return
    }
    // Skill 调用卡片：展示技能执行摘要与结果
    if (isWebTimeline && message.remoteTool?.kind == DshRemoteToolKind.SKILL) {
        val remoteTool = message.remoteTool
        View {
            attr {
                width((pagerData.pageViewWidth - 36f).coerceAtLeast(0f))
                marginBottom(6f)
            }
            DshDisclosureRow {
                attr {
                 
                    title = "Skill"
                    iconAsset = "tool-skill.svg"
                    this.colors = colors()
                    summary = remoteTool.summary
                    errorSummary = message.toolError
                    stopped = message.toolStopped
                    body = message.content
                    open = bodyLocked || isExpanded()
                    expandable = !bodyLocked && message.content.isNotEmpty()
                    this.onToggle = onToggle
                    bodyExpanded = isBodyExpanded()
                    this.onToggleBody = onToggleBody
                    maxBodyLines = 8
                    running = message.toolRunning
                    compact = true
                }
            }
        }
        return
    }
    if (isWebTimeline && message.role == DshMessageRole.TOOL) {
        val remoteTool = message.remoteTool
        val isRemoteSpecial = remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION ||
            remoteTool?.kind == DshRemoteToolKind.TODO
        val rawBody = remoteTool?.output?.takeIf { it.isNotEmpty() }
            ?: remoteTool?.body?.takeIf { it.isNotEmpty() }
            ?: remoteTool?.input?.takeIf { it.isNotEmpty() }
            ?: message.content
        // body 直接用 settle 阶段生成的结果（问答可读文本 / 取消中断文案），
        // 不再重算兜底：中断/取消时 dshAskReadableBody 返回空，旧逻辑会错误兜成"已回答"。
        val toolBody = remoteTool?.body?.takeIf { it.isNotEmpty() } ?: rawBody
        // 结构化提问卡片：解析成功时用专门组件渲染，替代纯文本 body。
        val askCancelled = remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION &&
            remoteTool.summary == "已取消"
        val askAborted = remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION &&
            message.toolStopped
        val askCardData = if (remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION) {
            dshAskQuestionCard(remoteTool.input, remoteTool.output ?: "", askCancelled, askAborted)
        } else null
        val effectiveBody = if (askCardData != null) "" else toolBody
        val trimmedBody = effectiveBody.trimStart()
        val isJson = !isRemoteSpecial &&
            (trimmedBody.startsWith("{") || trimmedBody.startsWith("["))
        val cardLabel = remoteTool?.title ?: when (message.toolCardType) {
            DshToolCardType.TERMINAL -> "Bash"
            DshToolCardType.READ -> "Read"
            DshToolCardType.DIFF -> "Diff"
            DshToolCardType.SEARCH -> "Search"
            DshToolCardType.WEB -> "Web"
            DshToolCardType.JSON -> "JSON"
            DshToolCardType.GENERIC -> message.toolName ?: "工具"
        }
        val summary = remoteTool?.summary?.takeUnless { it.dshLooksLikeJson() }
            ?: if (remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION) "" else
                toolBody.lineSequence().firstOrNull().orEmpty().takeUnless { it.dshLooksLikeJson() }.orEmpty()
        // 工具调用行：Bash/Read 等，JSON 结果可折叠展开
        View {
            attr {
                width((pagerData.pageViewWidth - 36f).coerceAtLeast(0f))
                marginBottom(6f)
            }
            DshDisclosureRow {
                attr {
                    title = if (cardLabel.dshLooksLikeJson()) (remoteTool?.toolName ?: "工具") else cardLabel
                    iconAsset = remoteTool?.iconAsset() ?: message.toolCardType.iconAsset()
                    this.colors = colors()
                    this.summary = summary
                    errorSummary = message.toolError
                    stopped = message.toolStopped
                    body = ""
                    jsonContent = if (isJson) effectiveBody else ""
                    open = bodyLocked || isExpanded()
                    expandable = !bodyLocked
                    this.onToggle = onToggle
                    bodyExpanded = isBodyExpanded()
                    this.onToggleBody = onToggleBody
                    maxBodyLines = 8
                    this.isJsonNodeExpanded = isJsonNodeExpanded
                    this.onToggleJsonNode = onToggleJsonNode
                    running = message.toolRunning
                    askCard = askCardData
                    compact = true
                    onCopyToolCommand = onCopyToolContent
                    toolDetail = if (isJson || askCardData != null) null else DshToolDetail(
                        kind = remoteTool?.kind ?: DshRemoteToolKind.GENERIC,
                        input = remoteTool?.input.orEmpty(),
                        output = remoteTool?.output.orEmpty(),
                        fallback = effectiveBody,
                        running = message.toolRunning,
                        error = message.toolError,
                        filePath = remoteTool?.filePath,
                    )
                }
            }
        }
        return
    }
        // 普通消息行：用户气泡右对齐，助手/错误左对齐
        View {
            attr {
                flexDirectionColumn()
                alignItems(if (isUser) FlexAlign.FLEX_END else FlexAlign.FLEX_START)
                marginBottom(18f)
            }
        // 消息角色标签：你 / DeepSeek / 工具 / 错误
        Text {
            attr {
               
                text(when (message.role) {
                    DshMessageRole.USER -> "你"
                    DshMessageRole.TOOL -> message.toolName ?: "工具"
                    DshMessageRole.ERROR -> "错误"
                    DshMessageRole.ASSISTANT -> "DeepSeek"
                })
                fontSize(11f)
                color(if (isError) colors().stateErrorPrimary else colors().labelTertiary)
                marginBottom(5f)
            }
        }
        // 消息内容容器：用户/错误为气泡底色，助手为纯文本。长按助手内容弹操作菜单。
        View {
            attr {
                if (!isUser && !isError) {
                    width((pagerData.pageViewWidth - 36f).coerceAtMost(620f).coerceAtLeast(0f))
                }
                maxWidth(620f)
                padding(if (isUser) 10f else 0f, if (isUser) 14f else 0f, if (isUser) 10f else 0f, if (isUser) 14f else 0f)
                borderRadius(if (isUser) 18f else 0f)
                backgroundColor(when {
                        isUser -> colors().specificBubble
                        isError -> Color(0xFFFFEEEE)
                        else -> Color(0x00FFFFFF)
                    })
            }
            event {
                if (!isUser && !isError) {
                    longPress {
                        onLongPress(message, renderedContent, it.pageX, it.pageY)
                    }
                }
            }
            if (isUser || isError) {
                Text {
                    attr {
                        text(message.content)
                        lines(Int.MAX_VALUE)
                        fontSize(15f)
                        color(if (isUser) colors().labelPrimaryBluish else colors().stateErrorPrimary)
                    }
                }
            // 助手回复内容：Markdown 渲染 + 流式光标
            } else {
                View {
                    attr {
                        flexDirectionColumn()
                    }
                    DshMarkdown {
                        attr {
                            contentWidth = (pagerData.pageViewWidth - 36f).coerceAtLeast(0f)
                            val raw = contentProvider?.invoke() ?: message.content
                            val live = pageStreaming()
                            content = raw
                            liveContent = contentProvider
                            streamingProvider = pageStreaming
                            streaming = live
                            darkMode = colors().isDark
                        }
                    }
                    vif({ pageStreaming() && (contentProvider?.invoke() ?: message.content).isNotEmpty() }) {
                        Text {
                            attr {
                                text(DshStreamingMarkdown.CURSOR)
                                fontSize(14f)
                                color(colors().stateBusinessPrimary)
                                marginTop(2f)
                            }
                        }
                    }
                }
            }
        }
        // 用户消息随文图片（本地内存预览；历史恢复走 Host timeline attachmentId，异步加载后通过 attachmentRevision 触发重渲染）
        vif({
            val rev = attachmentRevision() // 建立响应式依赖，attachment 加载完成后重渲染
            isUser && message.imagePreviews.isNotEmpty()
        }) {
            View {
                attr {
                    flexDirectionRow()
                    flexWrap(FlexWrap.WRAP)
                    justifyContent(FlexJustifyContent.FLEX_END)
                    marginTop(6f)
                    marginBottom(2f)
                }
                vforIndex({ ObservableList<String>().apply { addAll(message.imagePreviews) } }) { preview, _, _ ->
                    View {
                        attr {
                            size(72f, 72f)
                            marginLeft(6f)
                            borderRadius(8f)
                            backgroundColor(colors().bgModulePlatform)
                            border(Border(1f, BorderStyle.SOLID, colors().borderL1))
                        }
                        Image {
                            attr {
                                src(preview)
                                width(72f)
                                height(72f)
                                resizeCover()
                            }
                            event {
                                click { onPreviewImage(preview) }
                            }
                        }
                    }
                }
            }
        }
        // AI 回答下方的横向操作容器（footer），对齐 dsh 原版 IconActions 行。
        // 仅在回答结算（非流式）且为该轮最后一段时出现，避免分段重复渲染。
        if (message.role == DshMessageRole.ASSISTANT && !pageStreaming() && isTurnTail()) {
            DshMessageFooter(copied = copied(), colors = colors) { action ->
                // COPY 复制整个回合的完整正文（跨工具调用的所有正文段），由页面层聚合
                if (action == DshMessageFooterAction.COPY) {
                    onCopyMessageContent(message)
                } else {
                    onFooterAction(message, action)
                }
            }
        }
    }
}

// 回答下方横向操作容器：复制 / 好的回答 / 有问题的回答 / 在新对话中分支 / 分享（对齐 dsh 原版）
internal fun ViewContainer<*, *>.DshMessageFooter(
    copied: Boolean = false,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    onAction: (DshMessageFooterAction) -> Unit,
) {
    View {
        attr {
            height(28f)
            marginTop(2f)
            flexDirectionRow()
            alignItemsCenter()
        }
        DshFooterActionIcon(if (copied) "check.svg" else "copy.svg", DshMessageFooterAction.COPY, onAction, colors = colors, first = true)
        DshFooterActionIcon("like.svg", DshMessageFooterAction.GOOD, onAction, colors = colors)
        DshFooterActionIcon("dislike.svg", DshMessageFooterAction.BAD, onAction, colors = colors)
        DshFooterActionIcon("branch.svg", DshMessageFooterAction.BRANCH, onAction, colors = colors)
        DshFooterActionIcon("share.svg", DshMessageFooterAction.SHARE, onAction, colors = colors)
    }
}

// 单个操作图标按钮：28x28 圆形热区，16px 图标居中（对齐 dsh 原版 IconActions）
internal fun ViewContainer<*, *>.DshFooterActionIcon(
    asset: String,
    action: DshMessageFooterAction,
    onAction: (DshMessageFooterAction) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    first: Boolean = false,
) {
    View {
        attr {
            width(28f)
            height(28f)
            allCenter()
            borderRadius(14f)
            if (!first) marginLeft(6f)
        }
        event { click { onAction(action) } }
        Image {
            attr {
                src(ImageUri.commonAssets(asset))
                size(16f, 16f)
                tintColor(colors().labelTertiary)
            }
        }
    }
}
