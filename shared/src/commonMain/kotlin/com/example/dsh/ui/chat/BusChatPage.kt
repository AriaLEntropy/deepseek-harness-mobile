package com.example.dsh.ui.chat

import com.example.dsh.base.BasePager
import com.example.dsh.bus.BUS_DELETE_TASK_CONFIRM_MESSAGE
import com.example.dsh.bus.BUS_DELETE_TASK_CONFIRM_TITLE
import com.example.dsh.bus.busCanDeleteTask
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.message.DshMessage
import com.example.dsh.platform.currentTimeMillis
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.ui.home.BusHomeModelOption
import com.example.dsh.ui.home.taskStatusLabel
import com.example.dsh.ui.message.DshMessageRow
import com.example.dsh.ui.rendering.DshDisclosureRow
import com.example.dsh.ui.rendering.DshPromptInput
import com.example.dsh.ui.session.DshSessionConfirmDialog
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 统一对话页：草稿态（还没建任务）和已绑定态（看历史 / 续聊）共用同一个页面。
 *
 * UI 只依赖 [BusChatSource] 暴露的数据与命令，不直接碰 Bus 命令编码或快照 JSON；
 * 以后接旧 DSH 数据源时，换一个 source 实现即可，页面不需要改。
 */
/** 首屏最多先建多少行；其余在滚动时按需创建。 */
private const val BUS_CHAT_INITIAL_RENDER_ROWS = 30
/** 列表里最多保留多少行节点；200 条消息也不会一次全建出来。 */
private const val BUS_CHAT_MAX_RENDERED_ROWS = 80

@Page("bus_chat")
internal class BusChatPage : BasePager() {
    private var taskId by observable("")
    private var computerAgentId = ""
    private var sourceKind = ""
    private var connectorId = ""
    private var agentKey = ""
    private var agentName by observable("")
    private var projectPath = ""
    private var projectName by observable("")
    private var modelId = ""
    private var effort = ""

    private var title by observable("")
    private var status by observable("")
    private var statusLabel by observable("")
    private var controllable by observable(true)
    private var canDeleteTasks by observable(false)
    private var showDeleteConfirm by observable(false)
    private var deleteBusy by observable(false)
    private var deleteError by observable("")
    private var draft by observable("")
    private var sending by observable(false)
    private var error by observable("")
    private var loading by observable(false)
    private var hasMore by observable(false)
    private var lastMessage by observable("")
    private var keyboardHeight by observable(0f)
    /** 已展开的工具组 key；默认全部收起。 */
    private var expandedToolGroups by observable(emptySet<String>())
    private val messages by observableList<DshMessage>()
    /** `vforLazy` 的数据源：消息与工具组合并后的渲染行，用 diffUpdate 增量更新。 */
    private val chatRows by observableList<BusChatRow>()
    private val models by observableList<BusHomeModelOption>()

    private var chatSource: ConversationSource? = null
    private var inputView: TextAreaView? = null
    private var alive = true
    private var lastFetchAt = 0L
    private var lastStatus = ""

    override fun created() {
        super.created()
        val params = pageData.params
        taskId = params.optString("taskId")
        computerAgentId = params.optString("agentId")
        sourceKind = params.optString("source")
        connectorId = params.optString("connectorId")
        agentKey = params.optString("agentKey")
        agentName = params.optString("agentName").ifBlank { "Agent" }
        projectPath = params.optString("projectPath")
        projectName = params.optString("projectName")
        modelId = params.optString("model")
        effort = params.optString("effort")
        title = params.optString("title").ifBlank { if (taskId.isEmpty()) "新任务" else "对话" }
        controllable = params.optString("controllable") != "false"

        val relay = acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME)
        chatSource = BusChatSource(relay, pagerId)
        chatSource?.models(agentKey)?.let { list ->
            models.clear()
            models.addAll(list)
        }
        if (taskId.isNotEmpty()) {
            loading = true
            applyHistory()
            fetchMessages(force = true)
        }
        pollTask()
        pollMessages()
    }

    override fun pageWillDestroy() {
        alive = false
        super.pageWillDestroy()
    }

    private fun applyHistory() {
        val history = chatSource?.history(taskId) ?: return
        hasMore = history.hasMore
        if (history.messages == messages.toList()) return
        messages.clear()
        messages.addAll(history.messages)
        refreshChatRows()
        loading = false
    }

    /**
     * 消息变化后重算渲染行。`diffUpdate` 按行内容比较：轮询拿到同样的 200 条消息
     * 不会重建节点，只有新增/变化的行才替换。
     */
    private fun refreshChatRows() {
        chatRows.diffUpdate(busChatRows(messages.toList())) { old, new -> old == new }
    }

    private fun fetchMessages(force: Boolean) {
        if (taskId.isEmpty()) return
        val now = currentTimeMillis()
        if (!force && now - lastFetchAt < 3_000) return
        lastFetchAt = now
        chatSource?.fetchMessages(computerAgentId, taskId)
    }

    private fun pollMessages() {
        if (!alive) return
        if (taskId.isNotEmpty()) {
            applyHistory()
            // 不只看 running：一轮可能在上一次状态轮询前就结束；
            // fetch 自带 3 秒节流，保证结束后也能拿到最终记录。
            fetchMessages(force = false)
        }
        setTimeout(pagerId, 1_500) { pollMessages() }
    }

    private fun pollTask() {
        if (!alive) return
        if (taskId.isNotEmpty()) {
            val info = chatSource?.taskInfo(taskId)
            if (info != null) {
                if (info.title.isNotBlank()) title = info.title
                if (info.agentName.isNotBlank()) agentName = info.agentName
                if (info.projectName.isNotBlank()) projectName = info.projectName
                if (info.model.isNotBlank()) modelId = info.model
                if (info.effort.isNotBlank()) effort = info.effort
                controllable = info.controllable
                canDeleteTasks = info.canDeleteTasks
                lastMessage = info.lastMessage
                status = info.status
                statusLabel = info.statusLabel.ifBlank { taskStatusLabel(info.status) }
                if (lastStatus == "running" && info.status != "running") {
                    fetchMessages(force = true)
                    setTimeout(pagerId, 800) { applyHistory() }
                }
                lastStatus = info.status
            }
        }
        setTimeout(pagerId, 2_000) { pollTask() }
    }

    private fun modelLabel(): String {
        val model = models.firstOrNull { it.id == modelId }
        return model?.displayName?.ifBlank { model.id } ?: modelId.ifBlank { "默认模型" }
    }

    private fun isToolGroupExpanded(key: String): Boolean = key in expandedToolGroups

    private fun toggleToolGroup(key: String) {
        expandedToolGroups = toggleBusToolGroup(expandedToolGroups, key)
    }

    private fun send() {
        if (sending) return
        val text = draft.trim()
        if (text.isEmpty()) return
        error = ""
        sending = true
        if (taskId.isEmpty()) {
            startTask(text)
        } else {
            followUp(text)
        }
    }

    private fun startTask(prompt: String) {
        val request = BusChatStartRequest(
            agentId = computerAgentId,
            source = sourceKind,
            connectorId = connectorId.ifBlank { null },
            projectPath = projectPath,
            prompt = prompt,
            model = modelId,
            effort = effort,
        )
        chatSource?.startTask(request) { id, message ->
            sending = false
            if (id == null) {
                error = message ?: "创建失败"
                return@startTask
            }
            taskId = id
            title = prompt.take(40)
            status = "running"
            statusLabel = "运行中"
            controllable = true
            draft = ""
            inputView?.setText("")
            loading = true
            fetchMessages(force = true)
            setTimeout(pagerId, 800) { applyHistory() }
        }
    }

    private fun followUp(prompt: String) {
        chatSource?.followUp(computerAgentId, taskId, prompt, modelId, effort) { ok, message ->
            sending = false
            if (!ok) {
                error = message.ifBlank { "发送失败" }
                return@followUp
            }
            draft = ""
            inputView?.setText("")
            status = "running"
            statusLabel = "运行中"
            setTimeout(pagerId, 600) {
                fetchMessages(force = true)
                applyHistory()
            }
            setTimeout(pagerId, 2_500) {
                fetchMessages(force = true)
                applyHistory()
            }
        }
    }

    private fun stop() {
        if (taskId.isEmpty() || !controllable) return
        chatSource?.interrupt(computerAgentId, taskId) { ok, message ->
            if (!ok) error = message.ifBlank { "停止失败" }
        }
    }

    /** 会话已停止且连接器声明 canDeleteTasks 时才允许永久删除。 */
    private fun canDelete(): Boolean =
        taskId.isNotEmpty() && busCanDeleteTask(status, canDeleteTasks)

    private fun requestDelete() {
        if (!canDelete() || deleteBusy) return
        deleteError = ""
        showDeleteConfirm = true
    }

    private fun deleteTask() {
        if (!canDelete() || deleteBusy) return
        deleteBusy = true
        deleteError = ""
        chatSource?.deleteTask(computerAgentId, taskId) { ok, message ->
            deleteBusy = false
            if (!ok) {
                deleteError = message.ifBlank { "删除失败" }
                return@deleteTask
            }
            showDeleteConfirm = false
            // 电脑确认后发 taskRemoved；任务已永久隐藏，回到列表即可。
            setTimeout(pagerId, 300) { close() }
        }
    }

    private fun close() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    override fun body(): ViewBuilder {
        return {
            vbind({ this@BusChatPage.themeColors }) {
                val c = this@BusChatPage.themeColors
                View {
                    attr {
                        flex(1f)
                        flexDirectionColumn()
                        backgroundColor(c.bgBase)
                        paddingTop(pagerData.statusBarHeight)
                    }
                    ChatHeader(
                        title = { this@BusChatPage.title },
                        subtitle = { this@BusChatPage.subtitle() },
                        statusLabel = { this@BusChatPage.statusLabel },
                        showStop = {
                            this@BusChatPage.taskId.isNotEmpty() &&
                                this@BusChatPage.status == "running" &&
                                this@BusChatPage.controllable
                        },
                        showDelete = { this@BusChatPage.canDelete() },
                        deleteBusy = { this@BusChatPage.deleteBusy },
                        colors = c,
                        onBack = { this@BusChatPage.close() },
                        onStop = { this@BusChatPage.stop() },
                        onDelete = { this@BusChatPage.requestDelete() },
                    )
                    // 行数据为空时先挂空态；有行时才挂 List，避免 LazyLoop 在
                    // 「空列表先挂载、后 addAll」时把增量当成可见范围之后而不建 cell。
                    vif({ this@BusChatPage.chatRows.isEmpty() }) {
                        View {
                            attr {
                                paddingLeft(18f)
                                paddingRight(18f)
                                paddingTop(30f)
                            }
                            Text {
                                attr {
                                    text(
                                        if (this@BusChatPage.loading) "正在加载记录…"
                                        else if (this@BusChatPage.taskId.isEmpty()) "说点什么，开始一个新任务"
                                        else "还没有对话记录",
                                    )
                                    fontSize(13f)
                                    color(c.labelTertiary)
                                }
                            }
                        }
                    }
                    vif({ this@BusChatPage.chatRows.isNotEmpty() }) {
                        // 200 条消息也只创建可见行；工具组展开状态由行内闭包读取。
                        List {
                            attr {
                                flex(1f)
                                padding(12f, 18f, 12f, 18f)
                                firstContentLoadMaxIndex(BUS_CHAT_INITIAL_RENDER_ROWS)
                                preloadViewDistance(pagerData.pageViewHeight)
                            }
                            View {
                                attr { flexDirectionColumn() }
                                vif({ this@BusChatPage.hasMore }) {
                                    Text {
                                        attr {
                                            text("更早的记录在电脑上")
                                            fontSize(11f)
                                            color(c.labelTertiary)
                                            marginBottom(10f)
                                        }
                                    }
                                }
                            }
                            vforLazy(
                                { this@BusChatPage.chatRows },
                                maxLoadItem = BUS_CHAT_MAX_RENDERED_ROWS,
                            ) { row, _, _ ->
                                when (row) {
                                    is BusChatRow.Message -> DshMessageRow(
                                        message = row.message,
                                        pageStreaming = { false },
                                        isWebTimeline = false,
                                        isExpanded = { true },
                                        onToggle = {},
                                        colors = { c },
                                        isTurnTail = { true },
                                    )
                                    is BusChatRow.ToolGroup -> BusToolGroupRow(
                                        group = row,
                                        expanded = { this@BusChatPage.isToolGroupExpanded(row.key) },
                                        onToggle = { this@BusChatPage.toggleToolGroup(row.key) },
                                        colors = c,
                                    )
                                }
                            }
                            View {
                                attr { flexDirectionColumn() }
                                vif({
                                    this@BusChatPage.status == "failed" &&
                                        this@BusChatPage.lastMessage.isNotBlank()
                                }) {
                                    Text {
                                        attr {
                                            text("失败：${this@BusChatPage.lastMessage}")
                                            fontSize(12f)
                                            color(c.stateErrorPrimary)
                                            marginTop(12f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ChatComposer(
                        colors = c,
                        draft = { this@BusChatPage.draft },
                        sending = { this@BusChatPage.sending },
                        error = { this@BusChatPage.error },
                        modelLabel = { this@BusChatPage.modelLabel() },
                        draftMode = { this@BusChatPage.taskId.isEmpty() },
                        readOnly = {
                            this@BusChatPage.taskId.isNotEmpty() && !this@BusChatPage.controllable
                        },
                        keyboardHeight = { this@BusChatPage.keyboardHeight },
                        onDraftChange = { this@BusChatPage.draft = it },
                        onKeyboardHeightChange = { this@BusChatPage.keyboardHeight = it },
                        onInputReady = { this@BusChatPage.inputView = it },
                        onSubmit = { this@BusChatPage.send() },
                    )
                }
            }
        }
        DshSessionConfirmDialog(
            visible = { this@BusChatPage.showDeleteConfirm },
            title = BUS_DELETE_TASK_CONFIRM_TITLE,
            message = BUS_DELETE_TASK_CONFIRM_MESSAGE,
            busyLabel = "删除中...",
            confirmLabel = "永久删除",
            confirmColor = { this@BusChatPage.themeColors.stateErrorPrimary },
            busy = { this@BusChatPage.deleteBusy },
            error = { this@BusChatPage.deleteError },
            onCancel = {
                this@BusChatPage.showDeleteConfirm = false
                this@BusChatPage.deleteError = ""
            },
            onConfirm = { this@BusChatPage.deleteTask() },
            pageViewWidth = this@BusChatPage.pagerData.pageViewWidth,
            colors = { this@BusChatPage.themeColors },
        )
    }

    private fun subtitle(): String {
        if (taskId.isEmpty()) {
            val project = projectName.ifBlank { "默认目录" }
            return "使用 $agentName · $project"
        }
        val agent = agentName.ifBlank { "Agent" }
        val project = projectName.ifBlank { "" }
        return if (project.isBlank()) agent else "$agent · $project"
    }
}

private fun ViewContainer<*, *>.ChatHeader(
    title: () -> String,
    subtitle: () -> String,
    statusLabel: () -> String,
    showStop: () -> Boolean,
    showDelete: () -> Boolean,
    deleteBusy: () -> Boolean,
    colors: DshColorTokens,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    View {
        attr {
            height(52f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(10f)
            paddingRight(14f)
        }
        View {
            attr {
                size(36f, 36f)
                alignItemsCenter()
                justifyContentCenter()
            }
            event { click { onBack() } }
            Image {
                attr {
                    src(ImageUri.commonAssets("chevron-left.svg"))
                    size(20f, 20f)
                    tintColor(colors.labelPrimary)
                }
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                marginLeft(2f)
            }
            Text {
                attr {
                    text(title())
                    fontSize(16f)
                    fontWeightBold()
                    color(colors.labelPrimary)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(subtitle())
                    fontSize(11f)
                    color(colors.labelTertiary)
                    marginTop(2f)
                    lines(1)
                }
            }
        }
        vif({ statusLabel().isNotBlank() }) {
            Text {
                attr {
                    text(statusLabel())
                    fontSize(11f)
                    fontWeightMedium()
                    color(colors.labelSecondary)
                    marginLeft(8f)
                }
            }
        }
        vif({ showStop() }) {
            View {
                attr {
                    height(32f)
                    marginLeft(10f)
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(16f)
                    backgroundColor(colors.specificSelector)
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { onStop() } }
                Text {
                    attr {
                        text("停止")
                        fontSize(12f)
                        fontWeightMedium()
                        color(colors.labelPrimary)
                    }
                }
            }
        }
        vif({ showDelete() }) {
            View {
                attr {
                    height(32f)
                    marginLeft(10f)
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(16f)
                    backgroundColor(colors.specificSelector)
                    alignItemsCenter()
                    justifyContentCenter()
                    opacity(if (deleteBusy()) 0.5f else 1f)
                }
                event { click { if (!deleteBusy()) onDelete() } }
                Text {
                    attr {
                        text(if (deleteBusy()) "删除中…" else "删除")
                        fontSize(12f)
                        fontWeightMedium()
                        color(colors.stateErrorPrimary)
                    }
                }
            }
        }
    }
}

/**
 * 连续 tool 消息的折叠行：组头复用旧版 DSH 的 [DshDisclosureRow]（紧凑表头样式），
 * 默认收起；展开后每条工具仍是一行灰色摘要。
 */
private fun ViewContainer<*, *>.BusToolGroupRow(
    group: BusChatRow.ToolGroup,
    expanded: () -> Boolean,
    onToggle: () -> Unit,
    colors: DshColorTokens,
) {
    View {
        attr {
            flexDirectionColumn()
            marginTop(6f)
            marginBottom(6f)
        }
        // open 需要跟随页面展开状态，用 vbind 让表头箭头在收起/展开时同步。
        vbind({ expanded() }) {
            DshDisclosureRow {
                attr {
                    title = busToolGroupLabel(group.tools.size)
                    iconAsset = "think.svg"
                    this.colors = colors
                    open = expanded()
                    expandable = true
                    this.onToggle = onToggle
                    compact = true
                    chrome = true
                    headerOnly = true
                }
            }
        }
        vif({ expanded() }) {
            View {
                attr { flexDirectionColumn() }
                group.tools.forEach { tool ->
                    ChatToolRow(tool.content, colors)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.ChatToolRow(text: String, colors: DshColorTokens) {
    if (text.isBlank()) return
    Text {
        attr {
            text(text)
            fontSize(11f)
            color(colors.labelTertiary)
            marginTop(6f)
            marginBottom(6f)
            lines(2)
        }
    }
}

private fun ViewContainer<*, *>.ChatComposer(
    colors: DshColorTokens,
    draft: () -> String,
    sending: () -> Boolean,
    error: () -> String,
    modelLabel: () -> String,
    draftMode: () -> Boolean,
    readOnly: () -> Boolean,
    keyboardHeight: () -> Float,
    onDraftChange: (String) -> Unit,
    onKeyboardHeightChange: (Float) -> Unit,
    onInputReady: (TextAreaView) -> Unit,
    onSubmit: () -> Unit,
) {
    View {
        attr {
            flexDirectionColumn()
            backgroundColor(colors.bgBase)
            paddingLeft(14f)
            paddingRight(14f)
            paddingTop(8f)
            paddingBottom(12f)
            marginBottom(keyboardHeight())
            animation(Animation.easeInOut(0.24f), keyboardHeight())
        }
        vif({ readOnly() }) {
            Text {
                attr {
                    text("只能查看，不能继续操作")
                    fontSize(12f)
                    color(colors.labelTertiary)
                    marginTop(8f)
                    marginBottom(8f)
                }
            }
        }
        vif({ !readOnly() }) {
            // 草稿态 → 已绑定态时重建输入框，占位文案从“描述任务”切到“继续对话”。
            vbind({ draftMode() }) {
                DshPromptInput(
                    placeholder = if (draftMode()) "描述任务，例如：修复登录页的布局问题" else "继续对话…",
                    modelLabel = modelLabel,
                    modelEnabled = { false },
                    onPromptChange = onDraftChange,
                    onModelClick = {},
                    colors = { colors },
                    onKeyboardHeightChange = onKeyboardHeightChange,
                    inputRef = onInputReady,
                )
            }
            Text {
                attr {
                    text(error())
                    fontSize(11f)
                    color(colors.stateErrorPrimary)
                    marginTop(6f)
                    opacity(if (error().isEmpty()) 0f else 1f)
                }
            }
            View {
                attr {
                    height(44f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                    borderRadius(12f)
                    backgroundColor(
                        if (draft().trim().isNotEmpty() && !sending()) primaryButtonColor(colors)
                        else colors.specificSelector,
                    )
                }
                event {
                    click {
                        if (draft().trim().isNotEmpty() && !sending()) onSubmit()
                    }
                }
                Text {
                    attr {
                        text(
                            when {
                                sending() -> "发送中…"
                                draftMode() -> "开始任务"
                                else -> "发送"
                            },
                        )
                        fontSize(14f)
                        fontWeightBold()
                        color(
                            if (draft().trim().isNotEmpty() && !sending()) primaryButtonText(colors)
                            else colors.labelTertiary,
                        )
                    }
                }
            }
        }
    }
}

private fun primaryButtonColor(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFFFFFFFF) else Color(0xFF1D1B20)

private fun primaryButtonText(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF1D1B20) else Color(0xFFFFFFFF)
