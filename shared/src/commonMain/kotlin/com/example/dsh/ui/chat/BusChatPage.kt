package com.example.dsh.ui.chat

import com.example.dsh.base.BasePager
import com.example.dsh.base.BridgeModule
import com.example.dsh.bus.BUS_DELETE_TASK_CONFIRM_MESSAGE
import com.example.dsh.bus.BUS_DELETE_TASK_CONFIRM_TITLE
import com.example.dsh.bus.busCanDeleteTask
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.message.DshMessage
import com.example.dsh.platform.currentTimeMillis
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.ui.dshTopInset
import com.example.dsh.ui.home.BusAgentVisuals
import com.example.dsh.ui.home.BusHomeModelOption
import com.example.dsh.ui.home.taskStatusLabel
import com.example.dsh.ui.session.DshSessionConfirmDialog
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.scrollToPosition
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 任务详情 / 对话页
 *
 * 手机端不重新实现 Agent 逻辑，只把电脑上已经在跑的那一轮接着显示出来。
 * 三种真状态（流式 / 待处理 / 已结束）都落在输入条右下角那颗键上，顶栏不挂状态徽标
 */
/**
 * 消息区的渲染行：消息、更早提示、流式行、审批卡、产物卡统一进一个虚拟列表
 *
 * 审批卡和产物卡以前是 List 的静态兄弟节点，虚拟列表算不到它们的高度，
 * 自动落底会差一截；收进同一组行之后，落底就是最后一个索引
 */
private sealed class BusChatFeedItem {
    abstract val key: String

    data object Hint : BusChatFeedItem() {
        override val key: String = "hint"
    }

    data class MessageRow(val row: BusChatRow) : BusChatFeedItem() {
        override val key: String = row.key
    }

    data object Streaming : BusChatFeedItem() {
        override val key: String = "streaming"
    }

    data class Approval(val request: BusChatPendingRequest) : BusChatFeedItem() {
        override val key: String = "approval-${request.requestId}"
    }

    data class Artifact(val artifact: BusChatArtifact) : BusChatFeedItem() {
        override val key: String = "artifact-${artifact.id}"
    }
}

/** 相邻渲染行的上间距：换轮 24 / 同轮 8 / 工具后正文 14 / 卡片 12 */
private fun busChatFeedGap(previous: BusChatFeedItem?, current: BusChatFeedItem): Float = when {
    previous == null || previous is BusChatFeedItem.Hint -> 0f
    current is BusChatFeedItem.MessageRow ->
        busChatRowGap((previous as? BusChatFeedItem.MessageRow)?.row, current.row)
    current is BusChatFeedItem.Streaming -> busChatPx(14f)
    else -> busChatPx(12f)
}

@Page("bus_chat")
internal class BusChatPage : BasePager() {
    private var taskId by observable("")
    private var computerAgentId = ""
    private var sourceKind by observable("")
    private var connectorId by observable("")
    private var agentKey by observable("")
    private var agentName by observable("")
    private var hostName by observable("")
    private var projectPath by observable("")
    private var projectName by observable("")
    private var modelId by observable("")
    private var effort by observable("")
    private var autoApprove by observable(false)
    private var canAutoApprove by observable(false)
    /** 用户刚选的模型 / 强度 / 自动批准：随下一条消息发出，快照回来前不被覆盖 */
    private var stagedModelId by observable("")
    private var stagedEffort by observable("")
    private var stagedAutoApprove by observable<Boolean?>(null)

    private var title by observable("")
    private var status by observable("")
    private var statusLabel by observable("")
    private var controllable by observable(true)
    private var canDeleteTasks by observable(false)
    private var showDeleteConfirm by observable(false)
    private var deleteBusy by observable(false)
    private var deleteError by observable("")
    private var showRemoveConfirm by observable(false)
    private var draft by observable("")
    private var sending by observable(false)
    private var error by observable("")
    private var loading by observable(false)
    private var hasMore by observable(false)
    private var lastMessage by observable("")
    private var keyboardHeight by observable(0f)
    private var pendingRequest by observable<BusChatPendingRequest?>(null)
    private var requestBusy by observable(false)
    private var requestError by observable("")
    private var answeredRequestId by observable("")
    private var answerSelection by observable<Map<String, List<String>>>(emptyMap())
    private var showAutoApproveConfirm by observable(false)
    private var autoApproveExplained = false
    /** 草稿态的 chips 只有用户主动选过才回显名字 */
    private var workspacePicked by observable(false)
    private var agentPicked by observable(false)

    private var showMenu by observable(false)
    private var sheet by observable(BusChatSheetKind.NONE)

    /** 就地展开的那一组工具行的 key（稿子③）；空串表示都收着，同一时刻只展开一组 */
    private var expandedToolsKey by observable("")
    private var stepIndex by observable(0)
    private var stepTitle by observable("")
    private var listScrolled by observable(false)
    private var atBottom by observable(true)
    private var toast by observable("")
    private val stepTools by observableList<DshMessage>()

    private val messages by observableList<DshMessage>()
    private val chatRows by observableList<BusChatRow>()
    private val feedRows by observableList<BusChatFeedItem>()
    private val models by observableList<BusHomeModelOption>()
    private val agents by observableList<BusChatAgentOption>()
    private val artifacts by observableList<BusChatArtifact>()
    private val skills by observableList<BusChatSkill>()
    private var skillsRevision by observable(0)
    private var skillsLoading by observable(false)
    private var skillsError by observable("")
    private var skillContentTitle by observable("")
    private var skillContentBody by observable("")
    private var skillContentLoading by observable(false)
    private var skillContentError by observable("")
    private val workspaces by observableList<BusChatWorkspace>()

    private var chatSource: ConversationSource? = null
    private var inputView: InputView? = null
    private var chatListRef: ViewRef<ListView<*, *>>? = null
    private var alive = true
    private var lastFetchAt = 0L
    private var lastStatus = ""

    override fun created() {
        super.created()
        // 390×844 设计稿按页面宽度等比缩放；created 里先设一次，避免首屏用到默认值
        BusChatTokens.updateDesignScale(pagerData.pageViewWidth)
        val params = pageData.params
        taskId = params.optString("taskId")
        computerAgentId = params.optString("agentId")
        sourceKind = params.optString("source")
        connectorId = params.optString("connectorId")
        agentKey = params.optString("agentKey")
        agentName = params.optString("agentName")
        hostName = params.optString("hostName")
        projectPath = params.optString("projectPath")
        projectName = params.optString("projectName")
        modelId = params.optString("model")
        effort = params.optString("effort")
        autoApprove = params.optString("autoApprove") == "true"
        canAutoApprove = params.optString("canAutoApprove") == "true"
        // 草稿态一律用「新建任务」，不沿用列表里带过来的旧标题
        title = if (taskId.isEmpty()) "新建任务" else params.optString("title").ifBlank { "对话" }
        controllable = params.optString("controllable") != "false"

        val relay = acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME)
        val source: ConversationSource = BusChatSource(relay, pagerId)
        chatSource = source
        agents.clear()
        agents.addAll(source.agents())
        // 首页没给 Agent 时选第一个可启动的；给了就把它作为默认选中
        val current = agents.firstOrNull { it.key == agentKey } ?: agents.firstOrNull()
        if (current != null) {
            if (agentKey.isBlank()) {
                agentKey = current.key
                agentName = current.name
                sourceKind = current.source
                connectorId = current.connectorId.orEmpty()
            }
            canAutoApprove = current.canAutoApprove
        }
        source.models(agentKey).let { list ->
            models.clear()
            models.addAll(list)
        }
        skills.clear()
        workspaces.clear()
        workspaces.addAll(source.workspaces())
        if (taskId.isNotEmpty()) {
            artifacts.clear()
            artifacts.addAll(source.artifacts(taskId))
        }
        if (taskId.isNotEmpty()) {
            loading = true
            applyHistory()
            fetchMessages(force = true)
        }
        refreshFeed()
        pollTask()
        pollMessages()
        syncNavigationBarColor()
    }

    override fun onThemeSynced() {
        syncNavigationBarColor()
        syncSystemBarsDim()
    }

    override fun pageWillDestroy() {
        alive = false
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).setSystemBarsDimmed(false)
        }
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(0x00000000L, themeColors.isDark)
        }
        super.pageWillDestroy()
    }

    private fun syncNavigationBarColor() {
        val argb = if (themeColors.isDark) 0xFF151517 else 0xFFF4F4F6
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(argb, themeColors.isDark)
        }
    }

    /** 全屏浮层（对话框 / 底部面板）出现时，把系统导航栏一起压暗，避免遮罩在底部断一条亮带 */
    private fun overlayVisible(): Boolean =
        showAutoApproveConfirm ||
            showRemoveConfirm ||
            showDeleteConfirm ||
            sheet != BusChatSheetKind.NONE

    private fun syncSystemBarsDim() {
        if (!pagerData.isAndroid && !pagerData.isIOS) return
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setSystemBarsDimmed(overlayVisible(), overlayDimColor())
        }
    }

    /**
     * 导航栏要贴合当前浮层：
     * 确认弹窗用遮罩结果色；底部面板直接用面板底色，让面板看起来一直铺到屏幕底，不露背景条
     */
    private fun overlayDimColor(): Long {
        val dialogOnTop = showAutoApproveConfirm || showRemoveConfirm || showDeleteConfirm
        if (dialogOnTop) {
            return if (themeColors.isDark) 0xFF0D0D0EL else 0xFF929296L
        }
        return busChatPalette(themeColors).sheet.hexColor
    }

    private fun applyHistory() {
        val history = chatSource?.history(taskId) ?: return
        hasMore = history.hasMore
        if (history.messages == messages.toList()) {
            loading = false
            return
        }
        messages.clear()
        messages.addAll(history.messages)
        refreshChatRows()
        loading = false
        if (atBottom) scrollToBottom()
    }

    private fun refreshChatRows() {
        chatRows.diffUpdate(busChatRows(messages.toList())) { old, new -> old == new }
        refreshFeed()
    }

    /** 把消息、提示、流式行、审批卡与产物卡重算成一组可落底的行 */
    private fun refreshFeed() {
        val next = ArrayList<BusChatFeedItem>(chatRows.size + 4)
        if (hasMore) next += BusChatFeedItem.Hint
        chatRows.forEach { next += BusChatFeedItem.MessageRow(it) }
        if (isRunning()) next += BusChatFeedItem.Streaming
        val request = pendingRequest
        if (request != null && showApprovalCard()) next += BusChatFeedItem.Approval(request)
        artifacts.take(2).forEach { next += BusChatFeedItem.Artifact(it) }
        feedRows.diffUpdate(next)
        if (atBottom) setTimeout(pagerId, 50) { scrollToBottomNow() }
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
                if (stagedModelId.isBlank() && info.model.isNotBlank()) modelId = info.model
                if (stagedEffort.isBlank() && info.effort.isNotBlank()) effort = info.effort
                controllable = info.controllable
                canDeleteTasks = info.canDeleteTasks
                canAutoApprove = info.canAutoApprove
                if (stagedAutoApprove == null && info.autoApprove != autoApprove) {
                    autoApprove = info.autoApprove
                }
                lastMessage = info.lastMessage
                status = info.status
                statusLabel = info.statusLabel.ifBlank { taskStatusLabel(info.status) }
                val nextRequest = info.request
                if (nextRequest?.requestId != pendingRequest?.requestId) {
                    pendingRequest = nextRequest
                    requestError = ""
                    answeredRequestId = ""
                    answerSelection = emptyMap()
                    if (atBottom) scrollToBottom()
                }
                if (lastStatus == "running" && info.status != "running") {
                    fetchMessages(force = true)
                    setTimeout(pagerId, 800) { applyHistory() }
                }
                lastStatus = info.status
                refreshFeed()
            }
        }
        setTimeout(pagerId, 2_000) { pollTask() }
    }

    private fun stagedModel(): String = stagedModelId.ifBlank { modelId }

    private fun stagedEffortValue(): String = stagedEffort.ifBlank { effort }

    private fun stagedAutoApproveValue(): Boolean = stagedAutoApprove ?: autoApprove

    private fun modelLabel(): String {
        val id = stagedModel()
        val model = models.firstOrNull { it.id == id }
        return busChatModelChipLabel(
            displayName = model?.displayName.orEmpty(),
            modelId = id,
            effort = stagedEffortValue(),
        )
    }

    private fun workspaceLabel(): String =
        projectName.ifBlank { projectPath.substringAfterLast('/') }

    private fun avatarAsset(): String = BusAgentVisuals.assetFor(
        source = sourceKind,
        connectorId = connectorId,
        agentKey = agentKey,
        agentName = agentName,
    )

    private fun avatarOnly(): Boolean {
        val asset = avatarAsset()
        return asset.isNotBlank() && asset != BusAgentVisuals.FALLBACK
    }

    private fun isReadOnly(): Boolean = taskId.isNotEmpty() && !controllable

    private fun isRunning(): Boolean = status == "running"

    private fun canDelete(): Boolean =
        taskId.isNotEmpty() && busCanDeleteTask(status, canDeleteTasks)

    private fun showApprovalCard(): Boolean {
        val request = pendingRequest ?: return false
        return request.requestId.isNotBlank() && request.requestId != answeredRequestId
    }

    private fun scrollToBottom() {
        atBottom = true
        // 审批卡/流式行/产物是异步挂上的，内容高度会晚一拍；多排几次落底
        setTimeout(pagerId, 60) { scrollToBottomNow() }
        setTimeout(pagerId, 260) { scrollToBottomNow() }
        setTimeout(pagerId, 600) { scrollToBottomNow() }
    }

    private fun scrollToBottomNow() {
        val list = chatListRef?.view ?: return
        val last = feedRows.lastIndex
        if (last >= 0) list.scrollToPosition(last, 0f, false)
    }

    private fun onListScroll(offsetY: Float, contentHeight: Float, viewHeight: Float) {
        listScrolled = offsetY > 1f
        atBottom = offsetY + viewHeight >= contentHeight - busChatPx(24f)
    }

    private fun showToast(text: String) {
        toast = text
        setTimeout(pagerId, 2200) {
            if (toast == text) toast = ""
        }
    }

    private fun send() {
        if (sending || isReadOnly()) return
        val text = draft.trim()
        if (text.isEmpty()) return
        error = ""
        sending = true
        sheet = BusChatSheetKind.NONE
        if (taskId.isEmpty()) startTask(text) else followUp(text)
    }

    private fun startTask(prompt: String) {
        val request = BusChatStartRequest(
            agentId = computerAgentId,
            source = sourceKind,
            connectorId = connectorId.ifBlank { null },
            projectPath = projectPath,
            prompt = prompt,
            model = stagedModel(),
            effort = stagedEffortValue(),
            autoApprove = stagedAutoApproveValue().takeIf {
                canAutoApprove && projectPath.isNotBlank()
            },
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
            clearDraft()
            clearStaged()
            loading = true
            atBottom = true
            fetchMessages(force = true)
            setTimeout(pagerId, 800) { applyHistory() }
        }
    }

    private fun followUp(prompt: String) {
        chatSource?.followUp(
            agentId = computerAgentId,
            taskId = taskId,
            prompt = prompt,
            model = stagedModel(),
            effort = stagedEffortValue(),
            autoApprove = stagedAutoApproveValue().takeIf {
                canAutoApprove && projectPath.isNotBlank()
            },
        ) { ok, message ->
            sending = false
            if (!ok) {
                error = message.ifBlank { "发送失败" }
                return@followUp
            }
            clearDraft()
            clearStaged()
            status = "running"
            statusLabel = "运行中"
            atBottom = true
            setTimeout(pagerId, 600) {
                fetchMessages(force = true)
                applyHistory()
                scrollToBottom()
            }
            setTimeout(pagerId, 2_500) {
                fetchMessages(force = true)
                applyHistory()
            }
        }
    }

    private fun clearDraft() {
        draft = ""
        inputView?.setText("")
    }

    private fun clearStaged() {
        stagedModelId = ""
        stagedEffort = ""
        stagedAutoApprove = null
    }

    private fun stop() {
        if (taskId.isEmpty() || !controllable) return
        chatSource?.interrupt(computerAgentId, taskId) { ok, message ->
            if (!ok) error = message.ifBlank { "停止失败" }
        }
    }

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
            setTimeout(pagerId, 300) { close() }
        }
    }

    private fun hideFromList() {
        BusChatLocalHidden.hide(taskId)
        showRemoveConfirm = false
        showToast("已从列表移除")
        setTimeout(pagerId, 260) { close() }
    }

    private fun toggleAnswer(questionId: String, label: String) {
        val question = pendingRequest?.questions?.firstOrNull { it.id == questionId } ?: return
        val current = answerSelection[questionId].orEmpty()
        val next = if (question.multiSelect) {
            if (label in current) current - label else current + label
        } else {
            listOf(label)
        }
        answerSelection = answerSelection + (questionId to next)
    }

    private fun answerRequest(allow: Boolean) {
        val request = pendingRequest ?: return
        if (requestBusy) return
        requestBusy = true
        requestError = ""
        chatSource?.approve(
            agentId = computerAgentId,
            taskId = taskId,
            requestId = request.requestId,
            allow = allow,
            answers = if (allow) answerSelection else emptyMap(),
        ) { ok, message ->
            requestBusy = false
            if (!ok) {
                requestError = message.ifBlank { "发送失败" }
                return@approve
            }
            answeredRequestId = request.requestId
            refreshFeed()
            setTimeout(pagerId, 800) {
                fetchMessages(force = true)
                applyHistory()
            }
        }
    }

    /**
     * 点折叠行：在对话流里就地展开/收起步骤列表（稿子③的中间层）
     *
     * 不再用底部浮层代替这一层——浮层会遮住正文、还跳过用户最常做的一次操作
     */
    private fun toggleToolsExpanded(row: BusChatRow) {
        expandedToolsKey = if (expandedToolsKey == row.key) "" else row.key
    }

    /** 内联列表里点某一行才弹浮层：单步详情（稿子⑤） */
    private fun openStepDetail(row: BusChatRow, index: Int) {
        val tools = when (row) {
            is BusChatRow.ToolGroup -> row.tools
            is BusChatRow.Message -> listOf(row.message)
        }
        if (index !in tools.indices) return
        stepTools.clear()
        stepTools.addAll(tools)
        stepIndex = index
        stepTitle = busChatToolSummary(tools)
        sheet = BusChatSheetKind.STEP_DETAIL
    }

    private fun selectModel(id: String) {
        val model = models.firstOrNull { it.id == id } ?: return
        stagedModelId = model.id
        val current = stagedEffortValue()
        stagedEffort = when {
            model.efforts.isEmpty() -> ""
            model.efforts.any { it == current } -> current
            model.defaultEffort.isNotBlank() -> model.defaultEffort
            else -> model.efforts.first()
        }
        sheet = BusChatSheetKind.NONE
        showToast("下一条消息使用 ${modelLabel()}")
    }

    private fun selectEffort(value: String) {
        stagedEffort = value
        showToast("推理等级 ${busChatEffortLabel(value)}")
    }

    private fun selectWorkspace(path: String) {
        val workspace = workspaces.firstOrNull { it.path == path } ?: return
        if (taskId.isNotEmpty()) {
            // followUp 不带 projectPath，续聊换工作区没有意义，只说明原因
            sheet = BusChatSheetKind.NONE
            showToast("工作区只随新建任务发出，续聊不能更换")
            return
        }
        projectPath = workspace.path
        projectName = workspace.name
        workspacePicked = true
        sheet = BusChatSheetKind.NONE
        showToast("工作区 ${workspace.name}")
    }

    private fun selectAgent(key: String) {
        val option = agents.firstOrNull { it.key == key } ?: return
        agentKey = option.key
        agentName = option.name
        sourceKind = option.source
        connectorId = option.connectorId.orEmpty()
        canAutoApprove = option.canAutoApprove
        if (!option.canAutoApprove) {
            stagedAutoApprove = null
            autoApprove = false
        }
        models.clear()
        models.addAll(option.models)
        val first = option.models.firstOrNull()
        stagedModelId = ""
        stagedEffort = ""
        modelId = first?.id.orEmpty()
        effort = first?.defaultEffort.orEmpty().ifBlank {
            first?.efforts?.firstOrNull().orEmpty()
        }
        agentPicked = true
        sheet = BusChatSheetKind.NONE
        skills.clear()
        skillsLoading = false
        skillsError = ""
        skillsRevision++
        showToast("已选择 ${option.name}")
    }

    private fun openAttach() {
        refreshSkills()
        sheet = BusChatSheetKind.ATTACH
    }

    private fun refreshSkills() {
        val source = chatSource ?: return
        skillsLoading = true
        skillsError = ""
        skillsRevision++
        val requestedAgent = agentKey
        val requestedProject = projectPath
        val requestedTask = taskId
        source.refreshSkills(requestedAgent, requestedProject, requestedTask) { ok, error ->
            if (!alive) return@refreshSkills
            if (requestedAgent != agentKey || requestedProject != projectPath || requestedTask != taskId) {
                return@refreshSkills
            }
            if (!ok) {
                skills.clear()
                skillsLoading = false
                skillsError = error.ifBlank { "技能读取失败" }
                skillsRevision++
                return@refreshSkills
            }
            pollSkills(requestedAgent, requestedProject, requestedTask, 0)
        }
    }

    private fun pollSkills(
        requestedAgent: String,
        requestedProject: String,
        requestedTask: String,
        attempt: Int,
    ) {
        if (!alive) return
        if (requestedAgent != agentKey || requestedProject != projectPath || requestedTask != taskId) return
        val list = chatSource?.skills(requestedAgent, requestedProject, requestedTask)
        if (list == null && attempt < 20) {
            setTimeout(pagerId, 250) {
                pollSkills(requestedAgent, requestedProject, requestedTask, attempt + 1)
            }
            return
        }
        skills.clear()
        skills.addAll(list.orEmpty())
        skillsLoading = false
        skillsError = if (list.isNullOrEmpty()) "这个 Agent 没有上报技能" else ""
        skillsRevision++
    }

    private fun openSkillContent(skill: BusChatSkill) {
        if (!skill.hasBody) {
            showToast("这个技能没有正文")
            return
        }
        skillContentTitle = "/${skill.name}"
        skillContentBody = ""
        skillContentError = ""
        skillContentLoading = true
        skillsRevision++
        sheet = BusChatSheetKind.SKILL_DETAIL
        val requestedAgent = agentKey
        val requestedProject = projectPath
        val requestedTask = taskId
        chatSource?.readSkill(requestedAgent, requestedProject, requestedTask, skill.id) { content, error ->
            if (!alive) return@readSkill
            if (requestedAgent != agentKey || requestedTask != taskId) return@readSkill
            skillContentLoading = false
            if (content == null) {
                skillContentError = error ?: "读取失败"
            } else {
                skillContentTitle = "/${content.name}"
                skillContentBody = content.body
                skillContentError = ""
            }
            skillsRevision++
        }
    }

    private fun selectSkill(name: String) {
        if (name.isBlank()) return
        val token = "/${name.trimStart('/')} "
        draft = if (draft.isBlank()) token else "${draft.trimEnd()} $token"
        inputView?.setText(draft)
        sheet = BusChatSheetKind.NONE
    }

    private fun close() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    override fun body(): ViewBuilder {
        return {
            // Agent 变化时整页重建一次：顶栏头像、模型清单和草稿芯片都要跟着换
            // 宽度变化（旋转/分屏）也要重算整页比例
            vbind({
                BusChatPageBinding(
                    dark = this@BusChatPage.themeColors.isDark,
                    agentKey = this@BusChatPage.agentKey,
                    width = this@BusChatPage.pagerData.pageViewWidth,
                )
            }) {
                BusChatTokens.updateDesignScale(this@BusChatPage.pagerData.pageViewWidth)
                val palette = busChatPalette(this@BusChatPage.themeColors)
                val inset = dshTopInset(this@BusChatPage.pagerData.statusBarHeight, this@BusChatPage.pagerData.safeAreaInsets.top)
                val pageWidth = this@BusChatPage.pagerData.pageViewWidth
                val pageHeight = this@BusChatPage.pagerData.pageViewHeight
                View {
                    attr {
                        flex(1f)
                        flexDirectionColumn()
                        backgroundColor(palette.bgBase)
                    }
                    // 氛围层铺到状态栏和整屏，不能只从页面内容区开始
                    BusChatHeaderGlow(palette)
                    View {
                        attr {
                            flex(1f)
                            flexDirectionColumn()
                            paddingTop(inset)
                        }
                        View {
                            attr {
                                flex(1f)
                                flexDirectionColumn()
                            }
                            // 新建会话：空态只放品牌字标，不再挂底部说明
                            // 键盘弹起后字标让位给键盘与草稿芯片，正文区留空
                            vif({
                                this@BusChatPage.taskId.isEmpty() &&
                                    this@BusChatPage.chatRows.isEmpty() &&
                                    this@BusChatPage.keyboardHeight == 0f
                            }) {
                                View {
                                    attr {
                                        flex(1f)
                                        flexDirectionColumn()
                                        paddingTop(busChatPx(56f))
                                    }
                                    BusChatEmptyState(palette)
                                }
                            }
                            vif({
                                this@BusChatPage.taskId.isNotEmpty() &&
                                    this@BusChatPage.chatRows.isEmpty() &&
                                    !this@BusChatPage.loading &&
                                    this@BusChatPage.pendingRequest == null &&
                                    this@BusChatPage.artifacts.isEmpty()
                            }) {
                                View {
                                    attr {
                                        flex(1f)
                                        paddingTop(busChatPx(56f))
                                    }
                                    BusChatEmptyState(palette)
                                }
                            }
                            vif({
                                this@BusChatPage.chatRows.isEmpty() &&
                                    this@BusChatPage.loading
                            }) {
                                View {
                                    attr { flex(1f); allCenter() }
                                    Text {
                                        attr {
                                            text("正在加载记录…")
                                            fontSize(busChatPx(13f))
                                            color(palette.mut)
                                        }
                                    }
                                }
                            }
                            vif({
                                this@BusChatPage.chatRows.isNotEmpty() ||
                                    this@BusChatPage.pendingRequest != null ||
                                    this@BusChatPage.artifacts.isNotEmpty()
                            }) {
                                List {
                                    ref { viewRef -> this@BusChatPage.chatListRef = viewRef }
                                    attr {
                                        flex(1f)
                                        showScrollerIndicator(false)
                                        paddingTop(busChatPx(56f))
                                        paddingLeft(BusChatTokens.pageSide)
                                        paddingRight(BusChatTokens.pageSide)
                                        // 底部留出和渐隐等高的空间，最后一行可以滚到渐隐上方
                                        paddingBottom(busChatPx(44f))
                                        firstContentLoadMaxIndex(30)
                                        preloadViewDistance(pageHeight)
                                    }
                                    event {
                                        scroll { params ->
                                            this@BusChatPage.onListScroll(
                                                offsetY = params.offsetY,
                                                contentHeight = params.contentHeight,
                                                viewHeight = params.viewHeight,
                                            )
                                        }
                                    }
                                    vforLazy(
                                        { this@BusChatPage.feedRows },
                                        maxLoadItem = 80,
                                    ) { item, index, _ ->
                                        View {
                                            attr {
                                                flexDirectionColumn()
                                                width(pageWidth - BusChatTokens.pageSide * 2f)
                                                marginTop(
                                                    busChatFeedGap(
                                                        previous = this@BusChatPage.feedRows.getOrNull(index - 1),
                                                        current = item,
                                                    ),
                                                )
                                            }
                                            when (item) {
                                                is BusChatFeedItem.Hint -> BusChatHint(
                                                    palette,
                                                    "更早的记录在电脑上",
                                                )

                                                is BusChatFeedItem.MessageRow -> BusChatMessageRow(
                                                    row = item.row,
                                                    palette = palette,
                                                    contentWidth = pageWidth - BusChatTokens.pageSide * 2f,
                                                    streaming = {
                                                        this@BusChatPage.isRunning() &&
                                                            item.row is BusChatRow.Message &&
                                                            item.row.message.role == com.example.dsh.message.DshMessageRole.ASSISTANT &&
                                                            item.row.key == this@BusChatPage.chatRows.lastOrNull()?.key
                                                    },
                                                    expanded = {
                                                        this@BusChatPage.expandedToolsKey == item.row.key
                                                    },
                                                    onToggleTools = {
                                                        this@BusChatPage.toggleToolsExpanded(item.row)
                                                    },
                                                    onSelectStep = { index ->
                                                        this@BusChatPage.openStepDetail(item.row, index)
                                                    },
                                                )

                                                is BusChatFeedItem.Streaming ->
                                                    BusChatStreamingLine(palette, "正在执行命令")

                                                is BusChatFeedItem.Approval -> BusChatApprovalCard(
                                                    palette = palette,
                                                    request = item.request,
                                                    busy = { this@BusChatPage.requestBusy },
                                                    error = { this@BusChatPage.requestError },
                                                    questions = item.request.questions,
                                                    selection = this@BusChatPage.answerSelection,
                                                    onToggleOption = { qid, label ->
                                                        this@BusChatPage.toggleAnswer(qid, label)
                                                    },
                                                    onApprove = { this@BusChatPage.answerRequest(true) },
                                                    onDeny = { this@BusChatPage.answerRequest(false) },
                                                )

                                                is BusChatFeedItem.Artifact -> BusChatArtifactCard(
                                                    palette = palette,
                                                    artifact = item.artifact,
                                                    onClick = {
                                                        this@BusChatPage.showToast("产物在电脑上查看")
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            BusChatGlassBackdrop(palette, { this@BusChatPage.listScrolled })
                            BusChatHeader(
                                palette = palette,
                                title = { this@BusChatPage.title },
                                hostName = {
                                    this@BusChatPage.hostName.ifBlank { "电脑" }
                                },
                                projectName = {
                                    this@BusChatPage.projectName.ifBlank {
                                        this@BusChatPage.projectPath.substringAfterLast('/').ifBlank { "默认目录" }
                                    }
                                },
                                agentName = { this@BusChatPage.agentName },
                                avatarAsset = this@BusChatPage.avatarAsset(),
                                avatarOnly = this@BusChatPage.avatarOnly(),
                                dimmed = { this@BusChatPage.sheet != BusChatSheetKind.NONE },
                                onBack = { this@BusChatPage.close() },
                                onMenu = { this@BusChatPage.showMenu = true },
                                onAgentTap = {},
                            )
                            BusChatBottomFade(palette)
                            BusChatBackToBottom(
                                palette = palette,
                                bottom = busChatPx(12f),
                                visible = { !this@BusChatPage.atBottom },
                                onClick = { this@BusChatPage.scrollToBottom() },
                            )
                        }
                        vif({ this@BusChatPage.error.isNotBlank() }) {
                            View {
                                attr {
                                    paddingLeft(BusChatTokens.pageSide)
                                    paddingRight(BusChatTokens.pageSide)
                                    paddingBottom(busChatPx(6f))
                                }
                                Text {
                                    attr {
                                        text(this@BusChatPage.error)
                                        fontSize(busChatPx(11.5f))
                                        lineHeight(busChatPx(11.5f) * 1.5f)
                                        color(palette.bad)
                                        lines(Int.MAX_VALUE)
                                    }
                                }
                            }
                        }
                    }
                    BusChatComposer(
                            palette = palette,
                            draft = { this@BusChatPage.draft },
                            placeholder = {
                                when {
                                    this@BusChatPage.isRunning() -> BUS_CHAT_PLACEHOLDER_RUNNING
                                    this@BusChatPage.taskId.isEmpty() -> "描述你要做的事…"
                                    else -> BUS_CHAT_PLACEHOLDER_IDLE
                                }
                            },
                            running = { this@BusChatPage.isRunning() },
                            readOnly = { this@BusChatPage.isReadOnly() },
                            sending = { this@BusChatPage.sending },
                            modelLabel = { this@BusChatPage.modelLabel() },
                            workspaceLabel = {
                                if (this@BusChatPage.workspacePicked) this@BusChatPage.workspaceLabel()
                                else ""
                            },
                            agentName = {
                                if (this@BusChatPage.agentPicked) this@BusChatPage.agentName else ""
                            },
                            agentAvatar = this@BusChatPage.avatarAsset(),
                            draftMode = { this@BusChatPage.taskId.isEmpty() },
                            keyboardUp = { this@BusChatPage.keyboardHeight > 0f },
                            showAutoApprove = {
                                busChatCanShowAutoApprove(
                                    canAutoApprove = this@BusChatPage.canAutoApprove,
                                    hasProject = this@BusChatPage.projectPath.isNotBlank(),
                                )
                            },
                            autoApprove = { this@BusChatPage.stagedAutoApproveValue() },
                            onDraftChange = { this@BusChatPage.draft = it },
                            onPlus = { this@BusChatPage.openAttach() },
                            onModel = { this@BusChatPage.sheet = BusChatSheetKind.MODEL },
                            onAutoApprove = {
                                val next = !this@BusChatPage.stagedAutoApproveValue()
                                if (next && !this@BusChatPage.autoApproveExplained) {
                                    this@BusChatPage.showAutoApproveConfirm = true
                                } else {
                                    this@BusChatPage.stagedAutoApprove = next
                                    this@BusChatPage.showToast(
                                        if (next) "自动批准已开，随下一条消息发出"
                                        else "自动批准已关，随下一条消息发出",
                                    )
                                }
                            },
                            onWorkspace = { this@BusChatPage.sheet = BusChatSheetKind.WORKSPACE },
                            onAgentPick = { this@BusChatPage.sheet = BusChatSheetKind.AGENT },
                            onSend = { this@BusChatPage.send() },
                            onStop = { this@BusChatPage.stop() },
                            keyboardHeight = { this@BusChatPage.keyboardHeight },
                            onKeyboardHeightChange = { this@BusChatPage.keyboardHeight = it },
                            inputRef = { this@BusChatPage.inputView = it },
                        )
                    vif({ this@BusChatPage.showMenu }) {
                        BusChatMenuLayer(
                            palette = palette,
                            top = inset + busChatPx(56f),
                            artifactCount = { this@BusChatPage.artifacts.size },
                            canDelete = { this@BusChatPage.canDelete() },
                            busy = { this@BusChatPage.deleteBusy },
                            onArtifacts = {
                                this@BusChatPage.showMenu = false
                                val count = this@BusChatPage.artifacts.size
                                this@BusChatPage.showToast(
                                    if (count == 0) "产物都保存在电脑上" else "本站 $count 件产物 · 在电脑上查看",
                                )
                            },
                            onOpenOnComputer = {
                                this@BusChatPage.showMenu = false
                                this@BusChatPage.showToast("完整视图请在电脑上查看")
                            },
                            onRemove = {
                                this@BusChatPage.showMenu = false
                                this@BusChatPage.showRemoveConfirm = true
                            },
                            onDelete = {
                                this@BusChatPage.showMenu = false
                                this@BusChatPage.requestDelete()
                            },
                            onDismiss = { this@BusChatPage.showMenu = false },
                        )
                    }
                }
                vbind({
                    BusChatSheetBinding(
                        sheet = this@BusChatPage.sheet,
                        stagedEffort = this@BusChatPage.stagedEffortValue(),
                        skillsRevision = this@BusChatPage.skillsRevision,
                        skillsLoading = this@BusChatPage.skillsLoading,
                        skillsError = this@BusChatPage.skillsError,
                        skillContentTitle = this@BusChatPage.skillContentTitle,
                        skillContentBody = this@BusChatPage.skillContentBody,
                        skillContentLoading = this@BusChatPage.skillContentLoading,
                        skillContentError = this@BusChatPage.skillContentError,
                    )
                }) {
                    BusChatSheetHost(
                        kind = this@BusChatPage.sheet,
                        palette = palette,
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                        skills = this@BusChatPage.skills.toList(),
                        skillsLoading = this@BusChatPage.skillsLoading,
                        skillsError = this@BusChatPage.skillsError,
                        skillContentTitle = this@BusChatPage.skillContentTitle,
                        skillContentBody = this@BusChatPage.skillContentBody,
                        skillContentLoading = this@BusChatPage.skillContentLoading,
                        skillContentError = this@BusChatPage.skillContentError,
                        models = this@BusChatPage.models.toList(),
                        modelId = this@BusChatPage.modelId,
                        effort = this@BusChatPage.effort,
                        steps = busChatSteps(this@BusChatPage.stepTools.toList()),
                        stepsTitle = this@BusChatPage.stepTitle,
                        stepIndex = this@BusChatPage.stepIndex,
                        workspaces = this@BusChatPage.workspaces.toList(),
                        workspacePath = this@BusChatPage.projectPath,
                        agents = this@BusChatPage.agents.toList(),
                        agentKey = this@BusChatPage.agentKey,
                        onClose = { this@BusChatPage.sheet = BusChatSheetKind.NONE },
                        onPickCamera = {
                            this@BusChatPage.sheet = BusChatSheetKind.NONE
                            this@BusChatPage.showToast("手机端暂不支持在对话里直接添加附件")
                        },
                        onPickGallery = {
                            this@BusChatPage.sheet = BusChatSheetKind.NONE
                            this@BusChatPage.showToast("手机端暂不支持在对话里直接添加附件")
                        },
                        onPickFile = {
                            this@BusChatPage.sheet = BusChatSheetKind.NONE
                            this@BusChatPage.showToast("手机端暂不支持在对话里直接添加附件")
                        },
                        onSelectSkill = { this@BusChatPage.selectSkill(it) },
                        onReadSkill = { this@BusChatPage.openSkillContent(it) },
                        onSelectModel = { this@BusChatPage.selectModel(it) },
                        onSelectEffort = { this@BusChatPage.selectEffort(it) },
                        // 单步详情现在是从对话流里的内联列表点进来的，返回=关掉浮层回到列表
                        onBackStep = { this@BusChatPage.sheet = BusChatSheetKind.NONE },
                        onSelectStep = { index ->
                            this@BusChatPage.stepIndex = index
                            this@BusChatPage.sheet = BusChatSheetKind.STEP_DETAIL
                        },
                        onSelectWorkspace = { this@BusChatPage.selectWorkspace(it) },
                        onAddWorkspace = {
                            this@BusChatPage.showToast("在首页添加项目后再选")
                        },
                        onSelectAgent = { this@BusChatPage.selectAgent(it) },
                    )
                }
                BusChatToast(
                    palette = palette,
                    text = { this@BusChatPage.toast },
                    bottom = { busChatPx(118f) + this@BusChatPage.keyboardHeight },
                )
                DshSessionConfirmDialog(
                    visible = { this@BusChatPage.showAutoApproveConfirm },
                    title = "开启自动批准",
                    message = "开启后，电脑上这个项目的命令与改文件会直接放行，不再逐条问你。只对这台电脑上的这个项目生效。",
                    busyLabel = "",
                    confirmLabel = "开启",
                    confirmColor = { this@BusChatPage.themeColors.labelPrimary },
                    busy = { false },
                    error = { "" },
                    onCancel = { this@BusChatPage.showAutoApproveConfirm = false },
                    onConfirm = {
                        this@BusChatPage.showAutoApproveConfirm = false
                        this@BusChatPage.autoApproveExplained = true
                        this@BusChatPage.stagedAutoApprove = true
                        this@BusChatPage.showToast("自动批准已开，随下一条消息发出")
                    },
                    pageViewWidth = pageWidth,
                    verticalOffset = this@BusChatPage.pagerData.statusBarHeight +
                        this@BusChatPage.pagerData.safeAreaInsets.bottom,
                    colors = { this@BusChatPage.themeColors },
                )
                DshSessionConfirmDialog(
                    visible = { this@BusChatPage.showRemoveConfirm },
                    title = "从列表移除",
                    message = "只从本机列表移除，不会删除电脑上的会话",
                    busyLabel = "",
                    confirmLabel = "移除",
                    confirmColor = { this@BusChatPage.themeColors.labelPrimary },
                    busy = { false },
                    error = { "" },
                    onCancel = { this@BusChatPage.showRemoveConfirm = false },
                    onConfirm = { this@BusChatPage.hideFromList() },
                    pageViewWidth = pageWidth,
                    verticalOffset = this@BusChatPage.pagerData.statusBarHeight +
                        this@BusChatPage.pagerData.safeAreaInsets.bottom,
                    colors = { this@BusChatPage.themeColors },
                )
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
                    pageViewWidth = pageWidth,
                    verticalOffset = this@BusChatPage.pagerData.statusBarHeight +
                        this@BusChatPage.pagerData.safeAreaInsets.bottom,
                    colors = { this@BusChatPage.themeColors },
                )
                // 浮层开合时同步系统栏明暗；放在最后还是不占位
                vbind({ this@BusChatPage.overlayVisible() }) {
                    this@BusChatPage.syncSystemBarsDim()
                    View { attr { size(0f, 0f) } }
                }
            }
        }
    }
}

private data class BusChatPageBinding(
    val dark: Boolean,
    val agentKey: String,
    val width: Float,
)

private data class BusChatSheetBinding(
    val sheet: BusChatSheetKind,
    val stagedEffort: String,
    val skillsRevision: Int,
    val skillsLoading: Boolean,
    val skillsError: String,
    val skillContentTitle: String,
    val skillContentBody: String,
    val skillContentLoading: Boolean,
    val skillContentError: String,
)