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
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.layout.Frame
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.KeyboardParams
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.ScrollParams
import com.tencent.kuikly.core.views.SelectableOption
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// AI 回答下方横向操作容器（footer）的可用操作项，对齐 dsh 原版 IconActions 行
internal enum class DshMessageFooterAction { COPY, GOOD, BAD, BRANCH }

internal fun ViewContainer<*, *>.DshTurnStatus(
    visible: () -> Boolean,
    reconnecting: () -> Boolean,
    elapsedMs: () -> Long,
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
                    color(Color(TURN_STATUS_BLUE))
                }
            }
            vif({ elapsedMs() >= TURN_STATUS_CLOCK_AFTER_MS }) {
                Text {
                    attr {
                        text(dshFormatTurnDuration(elapsedMs()))
                        fontSize(13f)
                        color(Color(0xFF8A9399))
                        marginLeft(8f)
                    }
                }
            }
        }
    }
}

internal const val TURN_STATUS_BLUE = 0xFF4D6BFE
internal const val TURN_STATUS_CLOCK_AFTER_MS = 15_000L

// 文本选区浮动复制条的尺寸与定位（坐标相对消息内容容器）
private const val SELECTION_BAR_WIDTH = 64f
private const val SELECTION_BAR_HEIGHT = 32f
private const val SELECTION_BAR_GAP = 8f

private fun selectionBarX(containerWidth: Float, frame: Frame): Float {
    val x = frame.x + frame.width / 2f - SELECTION_BAR_WIDTH / 2f
    return x.coerceIn(SELECTION_BAR_GAP, (containerWidth - SELECTION_BAR_WIDTH - SELECTION_BAR_GAP).coerceAtLeast(SELECTION_BAR_GAP))
}

private fun selectionBarY(frame: Frame): Float {
    val above = frame.y - SELECTION_BAR_HEIGHT - SELECTION_BAR_GAP
    return if (above >= 0f) above else frame.y + frame.height + SELECTION_BAR_GAP
}

/** 助手消息内容容器宽度，与 DshMessageRow 内容容器 attr.width 保持一致，供选区复制条定位 */
private fun selectionBarContainerWidth(pageViewWidth: Float): Float =
    (pageViewWidth - 36f).coerceAtMost(620f).coerceAtLeast(0f)

// 空白会话首页：无消息时的占位引导（logo + 标语 + 预览版徽标）
internal fun ViewContainer<*, *>.DshNewSessionHome() {
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
                        color(Color(0xFF1B1F24))
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
                        backgroundColor(Color(0xFFE8F1FF))
                    }
                    Text {
                        attr {
                            text("预览版")
                            fontSize(11f)
                            fontWeightMedium()
                            color(Color(0xFF4176E6))
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
    inputRef: (com.tencent.kuikly.core.base.ViewRef<InputView>) -> Unit,
    onInputFocusChange: (Boolean) -> Unit,
    onDraftChange: (String) -> Unit,
    onKeyboardHeightChange: (KeyboardParams) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissKeyboard: () -> Unit,
    onUserListScroll: (ScrollParams) -> Unit,
    modelLabel: () -> String,
    attachmentMenuVisible: () -> Boolean,
    voiceActive: () -> Boolean,
    onOpenModels: () -> Unit,
    onToggleAttachments: () -> Unit,
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
    // 参数：message, renderedContent, 相对内容容器的锚点 x/y, 页面坐标 x/y
    onMessageLongPress: (DshMessage, String, Float, Float, Float, Float) -> Unit = { _, _, _, _, _, _ -> },
    onFooterAction: (DshMessage, DshMessageFooterAction) -> Unit = { _, _ -> },
    selectionRef: (String, ViewRef<com.tencent.kuikly.core.views.DivView>) -> Unit = { _, _ -> },
    selectionBarMessageId: () -> String = { "" },
    selectionBarX: () -> Float = { 0f },
    selectionBarY: () -> Float = { 0f },
    onSelectionBar: (String, Float, Float) -> Unit = { _, _, _ -> },
    onSelectionCancel: (String) -> Unit = {},
    onCopySelection: (String) -> Unit = {},
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
    onAnswerApproval: (String) -> Unit,
    onToggleQuestionOption: (String) -> Unit,
    onQuestionCustomChange: (String) -> Unit,
    onQuestionNavigate: (Int) -> Unit,
    onQuestionSkip: () -> Unit,
    onSubmitQuestion: () -> Unit,
    availableWidth: Float,
    pageViewWidth: Float,
    connectionLabel: () -> String,
    connectionCapsuleVisible: () -> Boolean,
    connectionCapsuleFadeOut: () -> Boolean,
    connectionCapsuleFadeOutAnimation: () -> Animation,
) {
    // 聊天主界面根容器：整页白色纵向布局（消息区 + 浮动面板 + 输入条）
    View {
        attr {
            flex(1f)
            width(availableWidth)
            flexDirectionColumn()
            backgroundColor(Color.WHITE)
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
                backgroundColor(Color.WHITE)
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
                    vbind({ conversationListEpoch(sessionId) }) {
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
                                    View {
                                        ref { messageRef(sessionId, message.id, it) }
                                        attr {
                                            width((availableWidth - 36f).coerceAtLeast(0f))
                                        }
                                        DshMessageRow(
                                            message,
                                            pageStreaming = {
                                                streaming() &&
                                                    activeConversationId() == sessionId &&
                                                    streamingMessageId() == message.id
                                            },
                                            isWebTimeline = isWebTimeline(),
                                            pageViewWidth = pageViewWidth,
                                            isExpanded = { isDisclosureExpanded(message.id) },
                                            onToggle = {
                                                onToggleDisclosure(message.id)
                                            },
                                            isBodyExpanded = { isBodyDisclosureExpanded(message.id) },
                                            onToggleBody = {
                                                onToggleBodyDisclosure(message.id)
                                            },
                                            isJsonNodeExpanded = { isJsonNodeExpanded(message.id, it) },
                                            onToggleJsonNode = { onToggleJsonNode(message.id, it) },
                                            onCopyToolContent = { onCopyToolContent(it) },
                                            onCopyMessageContent = { onCopyMessageContent(it) },
                                            onLongPress = { msg, content, sx, sy, px, py ->
                                                onMessageLongPress(msg, content, sx, sy, px, py)
                                            },
                                            onFooterAction = { msg, action -> onFooterAction(msg, action) },
                                            selectionRef = selectionRef,
                                            selectionBarMessageId = selectionBarMessageId,
                                            selectionBarX = selectionBarX,
                                            selectionBarY = selectionBarY,
                                            onSelectionBar = onSelectionBar,
                                            onSelectionCancel = onSelectionCancel,
                                            onCopySelection = onCopySelection,
                                            // footer 只渲染"当前回合（最近一条 user 之后）最后一段
                                            // 已结算 assistant"，中间的过渡文本/分段不会重复渲染
                                            isTurnTail = {
                                                val tailId = dshTurnTailAssistant(messagesForSession(sessionId))?.id
                                                tailId != null && tailId == message.id
                                            },
                                            attachmentDataUrl = { attachmentDataUrl(it) },
                                            contentProvider = {
                                                val stored = messagesForSession(sessionId)
                                                    .firstOrNull { it.id == message.id }
                                                    ?.content
                                                    .orEmpty()
                                                dshDisplayedAssistantContent(
                                                    stored = stored,
                                                    live = streamingContent(),
                                                    isLiveRow = streamingMessageId() == message.id &&
                                                        activeConversationId() == sessionId,
                                                )
                                            },
                                        )
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
                DshNewSessionHome()
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
                index = questionIndex()
                error = questionError()
                busy = interactionBusy()
                onToggleOption = onToggleQuestionOption
                onCustomChange = onQuestionCustomChange
                onNavigate = onQuestionNavigate
                onSkip = onQuestionSkip
                onSubmit = onSubmitQuestion
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
            // 全屏轻遮罩层：点击只收起键盘不穿透，背景隐约可见
            View {
                attr {
                    absolutePositionAllZero()
                    zIndex(50)
                    flexDirectionColumn()
                    justifyContentFlexEnd()
                }
                event { click { onDismissKeyboard() } }
                // 半透明暗化遮罩
                View {
                    attr {
                        absolutePositionAllZero()
                        backgroundColor(Color(0x26000000))
                    }
                }
                // 底部浮动卡片：覆盖输入框区域，圆角 + 弥散阴影，与消息操作菜单视觉一致
                View {
                    attr {
                        width((availableWidth - 24f).coerceAtLeast(0f))
                        marginTop(12f)
                        marginBottom(14f)
                        borderRadius(20f)
                        backgroundColor(Color.WHITE)
                        boxShadow(BoxShadow(0f, 8f, 26f, Color(0x33000000)))
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
                                }
                            }
                            Text {
                                attr {
                                    text(folderLabel())
                                    marginLeft(4f)
                                    fontSize(13f)
                                    color(Color(0xFF1B1F24))
                                }
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("chevron-down.svg"))
                                    size(12f, 12f)
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
                                }
                            }
                            Text {
                                attr {
                                    text(agentModeLabel())
                                    marginLeft(4f)
                                    fontSize(13f)
                                    color(Color(0xFF81858C))
                                }
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("chevron-down.svg"))
                                    size(12f, 12f)
                                }
                            }
                            DshHitButton(onOpenAgentModes)
                        }
                    }
                }

                // 输入卡：DSH Web 风格，细描边 + 弥散阴影，10px 顶部内边距。
                // 左右对称 margin 出 clearance（各 12px），宽度扣减 24 避免右侧溢出截断。
                View {
                    attr {
                        width((availableWidth - 24f).coerceAtLeast(0f))
                        marginLeft(12f)
                        marginRight(12f)
                        flexDirectionColumn()
                        paddingTop(10f)
                        backgroundColor(Color.WHITE)
                        borderRadius(22f)
                        border(Border(1f, BorderStyle.SOLID, Color(0x1A000000)))
                        boxShadow(BoxShadow(0f, 4f, 12f, Color(0x0D000000)))
                    }
                    // 技能建议列表：输入 / 开头时展示匹配的技能供点选
                    vif({
                        isWebTimeline() && draft().startsWith("/") &&
                                visibleSkillList(skills(), draft().removePrefix("/")).isNotEmpty()
                    }) {
                        View {
                            attr {
                                marginLeft(16f)
                                marginRight(12f)
                                maxHeight(132f)
                                marginBottom(6f)
                                flexDirectionColumn()
                                backgroundColor(Color(0xFFF7F9FB))
                                borderRadius(8f)
                                border(Border(1f, BorderStyle.SOLID, Color(0xFFE1E7ED)))
                            }
                            // 单个技能建议行：/技能名 + 描述，点击选用
                            vfor({ visibleSkillList(skills(), draft().removePrefix("/")) }) { skill ->
                                View {
                                    attr {
                                        height(32f)
                                        flexDirectionRow()
                                        alignItemsCenter()
                                        paddingLeft(8f)
                                        paddingRight(8f)
                                    }
                                    event { click { onPickSkill(skill.name) } }
                                    Text {
                                        attr {
                                            text("/${skill.name}")
                                            width(110f)
                                            fontSize(13f)
                                            fontWeightMedium()
                                            color(Color(0xFF2F6F4F))
                                        }
                                    }
                                    Text {
                                        attr {
                                            text(if (skill.modelInvocable) skill.description else "用户专用 · ${skill.description}")
                                            flex(1f)
                                            lines(1)
                                            fontSize(11f)
                                            color(Color(0xFF727D84))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 输入框：与 DSH Web 一致的 padding 和光标色
                    Input {
                        ref { inputRef(it) }
                        attr {
                            marginLeft(16f)
                            marginRight(12f)
                            height(46f)
                            backgroundColor(Color(0x00FFFFFF))
                            fontSize(15f)
                            color(Color(0xFF28323C))
                            placeholder(
                                when {
                                    voiceActive() -> "正在聆听..."
                                    else -> "发消息或按住说话，让电脑继续工作..."
                                },
                            )
                            placeholderColor(Color(0xFFADB2B8))
                            returnKeyTypeSend()
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
                            inputReturn { onSend() }
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
                                    backgroundColor(Color(0xFFF5F6F7))
                                    allCenter()
                                }
                                Image { attr { src(ImageUri.commonAssets("plus.svg")); size(14f, 14f) } }
                                DshHitButton { onToggleAttachments() }
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
                                        }
                                    }
                                    Image {
                                        attr {
                                            src(ImageUri.commonAssets("chevron-down.svg"))
                                            size(12f, 12f)
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
                                        color(Color(0xFF81858C))
                                    }
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("chevron-down.svg"))
                                        size(12f, 12f)
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
                                    backgroundColor(Color(
                                        if (stopButtonVisible()) 0xFFE05252
                                        else 0xFF3964FE
                                    ))
                                    opacity(
                                        if (stopButtonVisible() || draft().trim().isNotEmpty()) 1f
                                        else 0.4f
                                    )
                                    transform(translate = Translate(percentageX = 0f, percentageY = 0f, offsetY = -2f))
                                }
                                vif({ stopButtonVisible() }) {
                                    Image { attr { src(ImageUri.commonAssets("square.svg")); size(16f, 16f) } }
                                }
                                velse {
                                    Image { attr { src(ImageUri.commonAssets("arrow-up.svg")); size(16f, 16f) } }
                                }
                                DshHitButton {
                                    when {
                                        stopButtonVisible() -> onStop()
                                        draft().trim().isNotEmpty() -> onSend()
                                    }
                                }
                            }
                        }
                    }

                    // 附件菜单：点击附件按钮展开（图片/文件两个选项）
                    vif({ attachmentMenuVisible() }) {
                        View {
                            attr {
                                marginLeft(16f)
                                marginRight(12f)
                                marginBottom(8f)
                                flexDirectionColumn()
                                padding(8f)
                                borderRadius(10f)
                                backgroundColor(Color(0xFFF5F6F7))
                            }
                            // 附件菜单 - 图片选项行
                            View {
                                attr {
                                    height(32f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingLeft(8f)
                                }
                                Text { attr { text("图片"); fontSize(14f); color(Color(0xFF3B4147)) } }
                                View { attr { flex(1f) } }
                                Text { attr { text("PNG / JPG / WebP / GIF"); fontSize(11f); color(Color(0xFF9098A0)) } }
                            }
                            // 附件菜单 - 文件选项行
                            View {
                                attr {
                                    height(32f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingLeft(8f)
                                }
                                Text { attr { text("文件"); fontSize(14f); color(Color(0xFF3B4147)) } }
                                View { attr { flex(1f) } }
                                Text { attr { text("选择本地文件"); fontSize(11f); color(Color(0xFF9098A0)) } }
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
                )

    }
}

internal fun ViewContainer<*, *>.DshMessageRow(
    message: DshMessage,
    pageStreaming: () -> Boolean,
    isWebTimeline: Boolean,
    pageViewWidth: Float,
    isExpanded: () -> Boolean,
    onToggle: () -> Unit,
    isBodyExpanded: () -> Boolean = { false },
    onToggleBody: () -> Unit = {},
    isJsonNodeExpanded: (String) -> Boolean = { false },
    onToggleJsonNode: (String) -> Unit = {},
    onCopyToolContent: (String) -> Unit = {},
    onCopyMessageContent: (DshMessage) -> Unit = {},
    onLongPress: (DshMessage, String, Float, Float, Float, Float) -> Unit = { _, _, _, _, _, _ -> },
    selectionRef: (String, ViewRef<com.tencent.kuikly.core.views.DivView>) -> Unit = { _, _ -> },
    selectionBarMessageId: () -> String = { "" },
    selectionBarX: () -> Float = { 0f },
    selectionBarY: () -> Float = { 0f },
    onSelectionBar: (String, Float, Float) -> Unit = { _, _, _ -> },
    onSelectionCancel: (String) -> Unit = {},
    onCopySelection: (String) -> Unit = {},
    onFooterAction: (DshMessage, DshMessageFooterAction) -> Unit = { _, _ -> },
    isTurnTail: () -> Boolean = { true },
    attachmentDataUrl: (String) -> String? = { null },
    contentProvider: (() -> String)? = null,
) {
    if (message.hidden) return
    val isUser = message.role == DshMessageRole.USER
    val isError = message.role == DshMessageRole.ERROR
    DshStreamLog.i("row role=${message.role} id=${message.id} content='${DshStreamLog.preview(message.content, 40)}'")
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
    if (isWebTimeline && message.isContextInjection) {
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                marginBottom(12f)
            }
            DshDisclosureRow {
                attr {
                    title = "上下文注入"
                    iconAsset = "context.svg"
                    summary = message.toolName.orEmpty()
                    body = if (message.contextCatalog.isNotEmpty()) {
                        message.contextCatalog.joinToString("\n") { "${it.name}\n${it.description}" }
                    } else if (message.contextSections.isNotEmpty()) {
                        message.contextSections.joinToString("\n\n") {
                            "${it.title}\n${boundedContextText(it.body)}"
                        }
                    } else if (message.contextRecalls.isNotEmpty()) {
                        message.contextRecalls.joinToString("\n") {
                            "${it.label} · 保留 ${it.retainedMessages} · 省略 ${it.omittedMessages}${if (it.truncated) " · 已截断" else ""}"
                        } + "\n\n" + boundedContextText(message.contextBody)
                    } else if (message.contextInstructions.isNotEmpty()) {
                        message.contextInstructions.joinToString("\n") { "${it.path} · ${it.action}" } +
                            "\n\n" + boundedContextText(message.contextBody)
                    } else if (message.contextRelaySender.isNotEmpty()) {
                        "来自 ${message.contextRelaySender}\n\n${boundedContextText(message.contextBody)}"
                    } else {
                        boundedContextText(message.contextBody)
                    }
                    open = isExpanded()
                    expandable = message.contextCanExpand()
                    this.onToggle = onToggle
                    bodyExpanded = isBodyExpanded()
                    this.onToggleBody = onToggleBody
                    maxBodyLines = 8
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
                backgroundColor(Color(0xFFF6F8FA))
                border(Border(1f, BorderStyle.SOLID, Color(0xFFE4E8EC)))
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
                        color(Color(0xFF7A838A))
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
                    summary = message.content.dshReasoningSummary(message.streaming)
                    body = message.content
                    open = isExpanded()
                    expandable = message.content.isNotEmpty()
                    this.onToggle = onToggle
                    bodyExpanded = isBodyExpanded()
                    this.onToggleBody = onToggleBody
                    maxBodyLines = 8
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
                marginBottom(12f)
            }
            DshDisclosureRow {
                attr {
                    title = "Skill"
                    iconAsset = "tool-skill.svg"
                    summary = remoteTool.summary
                    errorSummary = message.toolError
                    body = message.content
                    open = isExpanded()
                    expandable = message.content.isNotEmpty()
                    this.onToggle = onToggle
                    bodyExpanded = isBodyExpanded()
                    this.onToggleBody = onToggleBody
                    maxBodyLines = 8
                    chrome = true
                    running = message.toolRunning
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
        val toolBody = if (remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION) {
            dshAskReadableBody(remoteTool.input, rawBody).ifEmpty { "已回答" }
        } else {
            rawBody
        }
        val trimmedBody = toolBody.trimStart()
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
            ?: if (remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION) "已完成" else
                toolBody.lineSequence().firstOrNull().orEmpty().takeUnless { it.dshLooksLikeJson() }.orEmpty()
        // 搜索类工具（联网 WEB 与文件 glob/grep）采用 think 风格：单行箭头、无卡片，可展开
        val isSearchStyle = remoteTool?.kind == DshRemoteToolKind.SEARCH ||
            remoteTool?.kind == DshRemoteToolKind.WEB
        // 工具调用卡片：Bash/Read 等，JSON 结果可折叠展开
        View {
            attr {
                width((pagerData.pageViewWidth - 36f).coerceAtLeast(0f))
                marginBottom(12f)
            }
            DshDisclosureRow {
                attr {
                    title = if (cardLabel.dshLooksLikeJson()) (remoteTool?.toolName ?: "工具") else cardLabel
                    iconAsset = remoteTool?.iconAsset() ?: message.toolCardType.iconAsset()
                    this.summary = summary
                    errorSummary = message.toolError
                    body = if (isJson) "" else toolBody
                    jsonContent = if (isJson) toolBody else ""
                    open = isExpanded()
                    expandable = true
                    this.onToggle = onToggle
                    bodyExpanded = isBodyExpanded()
                    this.onToggleBody = onToggleBody
                    maxBodyLines = 8
                    this.isJsonNodeExpanded = isJsonNodeExpanded
                    this.onToggleJsonNode = onToggleJsonNode
                    chrome = !isSearchStyle
                    running = message.toolRunning
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
                color(Color(if (isError) 0xFFC23B3B else 0xFF84939D))
                marginBottom(5f)
            }
        }
        // 消息内容容器：用户/错误为气泡底色，助手为纯文本。
        // 助手消息开启原生文本选择（selectable），长按弹菜单，菜单「选择文本」进入选区模式。
        View {
            ref { if (!isUser && !isError) selectionRef(message.id, it) }
            attr {
                if (!isUser && !isError) {
                    width((pagerData.pageViewWidth - 36f).coerceAtMost(620f).coerceAtLeast(0f))
                    selectable(SelectableOption.ENABLE)
                    selectionColor(Color(0xFF4D6BFE))
                }
                maxWidth(620f)
                padding(if (isUser) 10f else 0f, if (isUser) 14f else 0f, if (isUser) 10f else 0f, if (isUser) 14f else 0f)
                borderRadius(if (isUser) 18f else 0f)
                backgroundColor(Color(
                    when {
                        isUser -> 0xFFEDF3FE
                        isError -> 0xFFFFEEEE
                        else -> 0x00FFFFFF
                    },
                ))
            }
            event {
                if (!isUser && !isError) {
                    // 长按弹菜单；it.x/it.y 相对本内容容器，作为「选择文本」的选区锚点
                    longPress {
                        DshStreamLog.i("longpress fired role=${message.role} id=${message.id}")
                        onLongPress(message, renderedContent, it.x, it.y, it.pageX, it.pageY)
                    }
                    // 选区状态变化时同步浮动复制条位置（坐标相对本容器）
                    selectStart { frame ->
                        onSelectionBar(message.id, selectionBarX(selectionBarContainerWidth(pageViewWidth), frame), selectionBarY(frame))
                    }
                    selectChange { frame ->
                        onSelectionBar(message.id, selectionBarX(selectionBarContainerWidth(pageViewWidth), frame), selectionBarY(frame))
                    }
                    selectEnd { frame ->
                        onSelectionBar(message.id, selectionBarX(selectionBarContainerWidth(pageViewWidth), frame), selectionBarY(frame))
                    }
                    selectCancel {
                        onSelectionCancel(message.id)
                    }
                }
                register("touchDown", {
                    DshStreamLog.i("touchdown on msg role=${message.role} id=${message.id}")
                })
            }
            if (isUser || isError) {
                Text {
                    attr {
                        text(message.content)
                        lines(Int.MAX_VALUE)
                        fontSize(15f)
                        color(Color(if (isUser) 0xFF34415B else 0xFFB53232))
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
                            darkMode = false
                        }
                    }
                    vif({ pageStreaming() && (contentProvider?.invoke() ?: message.content).isNotEmpty() }) {
                        Text {
                            attr {
                                text(DshStreamingMarkdown.CURSOR)
                                fontSize(14f)
                                color(Color(0xFF4176E6))
                                marginTop(2f)
                            }
                        }
                    }
                }
            }
            // 选区浮动复制条：跟随选区位置，半透明白毛玻璃胶囊；复制成功后由页面层清除选区
            vif({
                !isUser && !isError &&
                    selectionBarMessageId() == message.id
            }) {
                View {
                    attr {
                        positionAbsolute()
                        left(selectionBarX())
                        top(selectionBarY())
                        width(SELECTION_BAR_WIDTH)
                        height(SELECTION_BAR_HEIGHT)
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                        borderRadius(17f)
                        backgroundColor(Color(0xF2FFFFFF))
                        boxShadow(BoxShadow(0f, 2f, 12f, Color(0x33000000)))
                        selectable(SelectableOption.DISABLE)
                    }
                    event {
                        click { onCopySelection(message.id) }
                    }
                    Text {
                        attr {
                            text("复制")
                            fontSize(13f)
                            fontWeightBold()
                            color(Color(0xFF1F2933))
                        }
                    }
                }
            }
        }
        // AI 回答下方的横向操作容器（footer），对齐 dsh 原版 IconActions 行。
        // 仅在回答结算（非流式）且为该轮最后一段时出现，避免分段重复渲染。
        if (message.role == DshMessageRole.ASSISTANT && !pageStreaming() && isTurnTail()) {
            DshMessageFooter { action ->
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

// 回答下方横向操作容器：复制 / 好的回答 / 有问题的回答 / 在新对话中分支（对齐 dsh 原版）
internal fun ViewContainer<*, *>.DshMessageFooter(
    onAction: (DshMessageFooterAction) -> Unit,
) {
    View {
        attr {
            width(128f)
            height(24f)
            marginTop(2f)
            flexDirectionRow()
            alignItemsCenter()
        }
        DshFooterActionIcon("copy.svg", DshMessageFooterAction.COPY, onAction)
        DshFooterActionIcon("like.svg", DshMessageFooterAction.GOOD, onAction)
        DshFooterActionIcon("dislike.svg", DshMessageFooterAction.BAD, onAction)
        DshFooterActionIcon("branch.svg", DshMessageFooterAction.BRANCH, onAction)
    }
}

// 单个操作图标按钮：32x24 紧凑热区，16px 图标居中
internal fun ViewContainer<*, *>.DshFooterActionIcon(
    asset: String,
    action: DshMessageFooterAction,
    onAction: (DshMessageFooterAction) -> Unit,
) {
    View {
        attr {
            width(32f)
            height(24f)
            allCenter()
        }
        event { click { onAction(action) } }
        Image {
            attr {
                src(ImageUri.commonAssets(asset))
                size(16f, 16f)
            }
        }
    }
}
