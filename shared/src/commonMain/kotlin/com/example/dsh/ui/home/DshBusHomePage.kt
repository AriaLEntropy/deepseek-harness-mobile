package com.example.dsh.ui.home

import com.example.dsh.base.BasePager
import com.example.dsh.base.BridgeModule
import com.example.dsh.bus.BUS_REMOVE_PROJECT_CONFIRM_MESSAGE
import com.example.dsh.bus.BUS_REMOVE_PROJECT_CONFIRM_TITLE
import com.example.dsh.bus.BUS_RESTART_CONNECTOR_CONFIRM_MESSAGE
import com.example.dsh.bus.BUS_RESTART_CONNECTOR_CONFIRM_TITLE
import com.example.dsh.bus.BusCache
import com.example.dsh.bus.BusCacheScope
import com.example.dsh.bus.BusCommand
import com.example.dsh.bus.BusCommandAck
import com.example.dsh.bus.BusCommandSender
import com.example.dsh.bus.BusCommandTransport
import com.example.dsh.bus.BusCommands
import com.example.dsh.bus.BusSendResult
import com.example.dsh.bus.BusStore
import com.example.dsh.bus.DshLocalBusSnapshotCache
import com.example.dsh.bus.busConnectorTarget
import com.example.dsh.bus.busCanRestartConnector
import com.example.dsh.bus.busParse
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.connection.supportsRelayBridge
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.models.DshSettingsChoice
import com.example.dsh.session.DshRelayProfile
import com.example.dsh.session.DshSessionScope
import com.example.dsh.storage.DshLocalStore
import com.example.dsh.storage.createDshLocalStore
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshThemeManager
import com.example.dsh.theme.DshThemeMode
import com.example.dsh.ui.chat.BusChatLocalHidden
import com.example.dsh.ui.dshTopInset
import com.example.dsh.ui.rendering.DshBottomSheet
import com.example.dsh.ui.rendering.DshSheetDetent
import com.example.dsh.ui.session.DshSessionConfirmDialog
import com.example.dsh.ui.session.DshSessionRenameDialog
import com.example.dsh.ui.settings.DshSettingsChoicePicker
import com.example.dsh.ui.settings.dshThemeModeLabel
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlinx.serialization.json.JsonElement

/**
 * 统一首页（v86 定稿形态）
 *
 * 渲染层拆到 BusHomeSections / BusAgentSheet / BusDeviceSheet / BusApprovalBoard，
 * 这里只留状态、轮询与命令：顶部设备入口与分段、Agent 头像栈、任务卡片列表、
 * 底部胶囊（列表 / 待审批互斥切换）+ 新建 FAB，审批视图直接复用同一套顶部与底部栏
 * 快照仍然 2 秒全量轮询，所以列表走 observableList + diffUpdate 增量更新，
 * 高频状态一律用 lambda 绑到 attr 上，避免整页重建丢掉滚动位置
 */
@Page("bus_home")
internal class DshBusHomePage : BasePager() {
    private var homeView by observable(BusHomeView(device = null, agents = emptyList(), tasks = emptyList()))

    /** 主区形态：没有电脑 / 没有可见任务 / 有内容 */
    private var bodyState by observable(BusHomeBodyState.DisabledComputer)

    /** 列表视图（最近 / 按项目）；[BUS_VIEW_APPROVAL] 是临时态，不写回偏好 */
    private var viewMode by observable(BUS_VIEW_RECENT)
    private var listViewMode by observable(BUS_VIEW_RECENT)

    /** Agent 多选筛选：空集合 = 不过滤（等价「全部 Agent」，也是面板首次打开的全选态） */
    private var selectedAgentKeys by observable(emptySet<String>())
    private var showAgentSheet by observable(false)
    private var agentExpandedKey by observable("")
    private var agentBusyKey by observable("")
    private var agentErrorKey by observable("")
    private var restartConfirmAgentKey by observable("")
    private var restartBusy by observable(false)
    private var restartError by observable("")

    private var showDeviceSheet by observable(false)
    private var showSettings by observable(false)
    private var showThemePicker by observable(false)
    private var showRenameComputer by observable(false)
    private var showRemoveComputer by observable(false)
    private var showAddComputer by observable(false)
    private var addComputerBusy by observable(false)
    private var addComputerError by observable("")
    private var showNewTaskSheet by observable(false)
    private var newTaskAgentKey by observable("")
    private var newTaskModelId by observable("")
    private var newTaskEffort by observable("")
    private var newTaskProjectPath by observable("")
    private var newTaskPrompt by observable("")
    private var newTaskBusy by observable(false)
    private var newTaskError by observable("")
    private var newTaskKeyboardHeight by observable(0f)
    private var showNewTaskModels by observable(false)
    private var renameDraft by observable("")
    private var renameBusy by observable(false)
    private var renameError by observable("")
    private var removeBusy by observable(false)
    private var removeError by observable("")
    private var projectMenuPath by observable("")
    private var projectMenuName by observable("")
    private var showRemoveProject by observable(false)
    private var removeProjectBusy by observable(false)
    private var removeProjectError by observable("")
    private var removeProjectPath by observable("")
    private var localHostName by observable("")

    // ---------- 审批视图状态（渲染层只读，改由这里负责） ----------
    private var approvalFilter by observable(BUS_APPROVAL_FILTER_ALL)
    private var approvalAnswered by observable(emptyMap<String, Boolean>())
    private var approvalSelection by observable(emptyMap<String, List<String>>())
    private var approvalBusyKey by observable("")
    private var approvalError by observable("")
    private var approvalLoading by observable(true)
    private var approvalScrolled by observable(false)

    /** 任务列表滚出顶部后才亮起的 56 渐隐条 */
    private var listScrolled by observable(false)

    private val collapsedProjects by observableList<String>()
    private val themeOptions by observableList<DshSettingsChoice>()

    /** 任务区当前这一屏的行；快照变化用 diffUpdate 增量替换 */
    private val taskRows by observableList<BusTaskAreaRow>()

    private var everLoaded by observable(false)
    private var waitingTooLong by observable(false)
    private var hasConnection = false
    private var connectionMode = ""
    private var databaseDir = ""
    private var store: DshLocalStore? = null
    /** Bus 快照缓存：冷启动先读，收到网络快照后再覆盖。 */
    private var busCache: BusCache? = null
    private var localBusCache: DshLocalBusSnapshotCache? = null
    private var hostId = ""
    private var lastCachedSnapshot = ""
    private var projectsInitialized = false
    private var alive = true

    /** 首页的 startTask / removeProject 也走统一发送入口（16 KiB、同 id 重试、60 秒未确认）。 */
    private val commandSender: BusCommandSender by lazy {
        BusCommandSender(
            transport = HomeRelayCommandTransport(
                relay = acquireModule(DshRelayModule.MODULE_NAME),
                pagerId = pagerId,
            ),
        )
    }
    private val retryCommands = mutableMapOf<String, BusCommand>()

    // ---------- 派生数据 ----------

    /** v86 基线只有浅色；深色按 B 号的面板配色桥接，保证不花掉 */
    private fun palette(): BusHomePalette = busSheetPalette(themeColors)

    private fun visibleTasks(): List<BusHomeTask> =
        homeView.tasks.forAgents(selectedAgentKeys).filterNot { BusChatLocalHidden.isHidden(it.id) }

    private fun visiblePendingCount(): Int = visibleTasks().count { it.pending }

    private fun visibleRunningCount(): Int = visibleTasks().count { it.status == "running" }

    private fun approvalItemsNow(): List<BusApprovalItem> = homeView.approvalItems(selectedAgentKeys)

    private fun openApprovalCount(): Int = busHomeOpenApprovalCount(approvalItemsNow(), approvalAnswered)

    private fun devicePlatformAsset(): String = homeView.device?.platformAsset ?: "computer.svg"

    private fun deviceEntryDisabled(): Boolean = homeView.device == null && !hasConnection

    private fun deviceTitleText(): String = when {
        homeView.device != null -> localHostName.ifBlank { homeView.device?.name.orEmpty() }
        deviceEntryDisabled() -> "没有电脑"
        waitingTooLong -> "电脑未响应"
        everLoaded -> "电脑已离线"
        else -> "正在连接电脑…"
    }

    /**
     * 头像栈空掉时右上角那句灰态文案
     *
     * 拿不到 Agent 列表有两种来路：还没配对 / 电脑端还没回快照，以及电脑端确实一个
     * 连接器都没有。后者才说「没有可用 Agent」，前者直接复用顶部胶囊的状态词，
     * 免得同一个屏幕对同一种连接状态给出两种说法
     */
    private fun agentEntryLabel(): String =
        if (homeView.device != null) "没有可用 Agent" else deviceTitleText()

    /**
     * 审批区缺省页的两行文案
     *
     * 「暂无待处理的内容」只在电脑真的回了快照时才成立；电脑没配对或没响应时，
     * 待处理数量是「不知道」而不是「零」，这里直接把顶部胶囊的状态词搬下来，
     * 免得同一块屏幕对同一种连接状态给出两种说法
     */
    private fun approvalEmptyTitle(): String =
        if (homeView.device != null) "暂无待处理的内容" else deviceTitleText()

    private fun approvalEmptySub(): String =
        if (homeView.device != null) "Agent 需要你时，会出现在这里" else "电脑连上后，待处理请求会出现在这里"

    /** 审批卡片的结构指纹：卡片序列 + 当前筛选段；answered / selection 等不进这里，否则每点一次整列重建 */
    private fun approvalBindKey(): String =
        approvalItemsNow().joinToString("|") { it.key } + "#" + approvalFilter

    private fun agentSheetBindKey(): String =
        homeView.agents.joinToString("|") { "${it.key}:${it.status}:${it.taskCount}:${it.lastError}:${it.available}:${it.enabled}" } +
            "#" + (homeView.device?.canRestartConnectors == true)

    // ---------- 首页状态 ----------

    private fun openAgentSheet() {
        agentErrorKey = ""
        showAgentSheet = true
    }

    private fun closeAgentSheet() {
        showAgentSheet = false
        agentErrorKey = ""
    }

    private fun toggleAgentSelection(key: String) {
        // 至少保留一个可勾选的 Agent；取消最后一个时 busToggleAgentFilter 会原样返回
        selectedAgentKeys = busToggleAgentFilter(selectedAgentKeys, key, busAgentSelectableKeys(homeView.agents))
        // 勾选变了，可见任务与分组都得重算一遍
        syncTaskRows()
    }

    /** 标题行快捷全选：回到「空集合 = 全部 Agent」的归一态 */
    private fun selectAllAgents() {
        if (selectedAgentKeys.isEmpty()) return
        selectedAgentKeys = emptySet()
        syncTaskRows()
    }

    private fun toggleAgentExpanded(key: String) {
        agentExpandedKey = busToggleAgentExpanded(agentExpandedKey, key)
    }

    private fun openDeviceSheet() {
        showDeviceSheet = true
    }

    private fun openSettings() {
        showSettings = true
    }

    /** 切列表视图（最近 / 按项目）；同时记住它是「下一次点列表分段要回到的那个视图」 */
    private fun selectListViewMode(mode: String) {
        if (viewMode == mode && listViewMode == mode) return
        // 先换数据、后换结构：
        // viewMode 在 vbind 绑定值里，赋值会同步重建列表子树；
        // 若等重建完再 diffUpdate，新的 List 会先按旧行渲染一帧（表现为慢一拍）
        listViewMode = mode
        initProjectCollapseIfNeeded()
        syncTaskRows()
        viewMode = mode
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(BUS_VIEW_MODE_KEY, mode)
        }
        listScrolled = false
    }

    /** 底部胶囊的终端分段：切进审批视图，列表区换成 BusApprovalBoard */
    private fun selectApprovalView() {
        if (viewMode == BUS_VIEW_APPROVAL) return
        viewMode = BUS_VIEW_APPROVAL
        approvalScrolled = false
    }

    /** 底部胶囊的列表分段：回到上一次用的列表视图 */
    private fun selectListView() {
        selectListViewMode(listViewMode)
    }

    /** 对齐原型：首次进入按项目时只展开第一组，其余折叠；用户手动操作后不再重置。 */
    private fun initProjectCollapseIfNeeded() {
        if (projectsInitialized || listViewMode != BUS_VIEW_PROJECTS) return
        val groups = homeView.projectGroups(selectedAgentKeys)
        if (groups.isEmpty()) return
        groups.drop(1).forEach { group ->
            if (!collapsedProjects.contains(group.key)) collapsedProjects.add(group.key)
        }
        projectsInitialized = true
    }

    private fun toggleProject(key: String) {
        if (collapsedProjects.contains(key)) collapsedProjects.remove(key) else collapsedProjects.add(key)
        syncTaskRows()
    }

    /**
     * 把当前视图的行算出来交给 diffUpdate
     *
     * 默认用 data class 的 `==` 比较：内容没变的行保持原节点，
     * 标题 / 时间 / 摘要变了的行才重建，滚动位置不动
     */
    private fun syncTaskRows() {
        val nextRows = if (listViewMode == BUS_VIEW_PROJECTS) {
            busTaskAreaProjectRows(homeView.projectGroups(selectedAgentKeys), collapsedProjects.toSet())
        } else {
            busTaskAreaRecentRows(visibleTasks())
        }
        taskRows.diffUpdate(nextRows)
        applyBodyState()
    }

    private fun applyBodyState() {
        val next = busHomeBodyState(
            hasDevice = homeView.device != null,
            visibleTaskCount = visibleTasks().size,
        )
        if (next != bodyState) bodyState = next
    }

    private fun currentComputerName(): String =
        localHostName.ifBlank { homeView.device?.name.orEmpty() }

    private fun settingsComputers(): List<BusSettingsComputer> {
        val device = homeView.device ?: return emptyList()
        return listOf(
            BusSettingsComputer(
                id = device.agentId,
                name = currentComputerName(),
                platform = device.platform,
                online = device.online,
                lastSeenAt = device.lastSeenAt,
                appVersion = device.appVersion,
                platformAsset = device.platformAsset,
            ),
        )
    }

    private fun openRenameComputer() {
        renameDraft = currentComputerName()
        renameError = ""
        showRenameComputer = true
    }

    private fun saveComputerName() {
        val name = renameDraft.trim()
        if (renameBusy) return
        if (name.isEmpty()) {
            renameError = "电脑名称不能为空"
            return
        }
        renameBusy = true
        renameError = ""
        val profile = runCatching { store?.loadRelayProfile() }.getOrNull()
        if (profile != null) {
            runCatching { store?.saveRelayProfile(profile.copy(hostName = name)) }
        }
        showRenameComputer = false
        localHostName = name
        renameBusy = false
    }

    private fun themePreferenceValue(): String = when (themeMode) {
        DshThemeMode.LIGHT -> "light"
        DshThemeMode.DARK -> "dark"
        DshThemeMode.SUNRISE_SUNSET -> "sunrise-sunset"
        else -> "system"
    }

    private fun applyTheme(value: String) {
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DshThemeManager.PREF_KEY_THEME_MODE, value)
            DshThemeManager.applyPreference(value)
        }
        showThemePicker = false
        setTimeout(pagerId, 0) { syncNavigationBarColor() }
    }

    private fun openAddComputer() {
        addComputerError = ""
        showDeviceSheet = false
        showAddComputer = true
    }

    private fun openSshSetup() {
        showAddComputer = false
        showSettings = false
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "ssh_setup",
            JSONObject().apply {
                put("pageName", "ssh_setup")
                put("databaseDir", databaseDir)
            },
        )
    }

    private fun scanAddComputer() {
        if (addComputerBusy) return
        if (!pageData.supportsRelayBridge) {
            addComputerError = "扫码连接目前仅支持 Android、iOS 和 HarmonyOS"
            return
        }
        addComputerBusy = true
        addComputerError = ""
        acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).scanAndPair { result ->
            setTimeout(pagerId, 0) {
                addComputerBusy = false
                if (!result.ok) {
                    addComputerError = result.message.ifEmpty { "扫码配对失败" }
                    return@setTimeout
                }
                val name = result.hostName.ifEmpty { "电脑" }
                val profile = DshRelayProfile(
                    result.hostId,
                    name,
                    result.relayOrigin,
                    result.pairedAt,
                )
                runCatching {
                    store?.saveRelayProfile(profile)
                    store?.saveLastConnectionMode(DshConnectionMode.RELAY)
                }
                localHostName = name
                showAddComputer = false
                showSettings = false
                val module = acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME)
                module.disconnect()
                module.connect { refreshBusHome() }
                setTimeout(pagerId, 1_200) { refreshBusHome() }
                setTimeout(pagerId, 4_000) { refreshBusHome() }
            }
        }
    }

    private fun startableAgents(): List<BusHomeAgent> = homeView.agents.filter { it.canStartTask }

    private fun selectedNewTaskAgent(): BusHomeAgent? =
        startableAgents().firstOrNull { it.key == newTaskAgentKey }

    /**
     * 新建任务默认模型：优先取该 Agent 最近一次任务结尾的 model/effort；
     * 没有历史任务再退回 ConnectorInfo.models 的第一个
     */
    private fun defaultModelForAgent(agent: BusHomeAgent): Pair<String, String> {
        val last = homeView.tasks
            .filter { it.agentKey == agent.key && it.model.isNotBlank() }
            .maxByOrNull { it.updatedAt }
        if (last == null) {
            val first = agent.models.firstOrNull()
            return first?.id.orEmpty() to first?.defaultEffort.orEmpty()
        }
        // 电脑还可能保留着已经下线的模型；列表里没有就退回电脑当前默认
        if (agent.models.isEmpty() || agent.models.any { it.id == last.model }) {
            return last.model to last.effort
        }
        val first = agent.models.firstOrNull()
        return first?.id.orEmpty() to first?.defaultEffort.orEmpty()
    }

    private fun selectNewTaskAgent(key: String) {
        newTaskAgentKey = key
        val agent = startableAgents().firstOrNull { it.key == key }
        val (model, effort) = agent?.let { defaultModelForAgent(it) } ?: ("" to "")
        newTaskModelId = model
        newTaskEffort = effort
        showNewTaskModels = false
    }

    private fun newTaskModels(): List<BusHomeModelOption> =
        selectedNewTaskAgent()?.models.orEmpty()

    private fun newTaskAgentName(): String =
        selectedNewTaskAgent()?.name.orEmpty().ifBlank { selectedNewTaskAgent()?.key.orEmpty() }

    private fun newTaskModelLabel(): String {
        val models = newTaskModels()
        val model = models.firstOrNull { it.id == newTaskModelId } ?: models.firstOrNull()
        if (model == null) return "默认模型"
        val name = model.displayName.ifBlank { model.id }
        return if (model.efforts.isNotEmpty() && newTaskEffort.isNotBlank()) {
            "$name · ${effortLabel(newTaskEffort)}"
        } else {
            name
        }
    }

    private fun newTaskHasModels(): Boolean = newTaskModels().isNotEmpty()

    private fun toggleNewTaskModels() {
        if (!newTaskHasModels()) return
        showNewTaskModels = !showNewTaskModels
    }

    private fun selectNewTaskModel(modelId: String) {
        val model = newTaskModels().firstOrNull { it.id == modelId } ?: return
        newTaskModelId = model.id
        newTaskEffort = when {
            model.efforts.isEmpty() -> ""
            model.efforts.any { it == newTaskEffort } -> newTaskEffort
            model.defaultEffort.isNotBlank() -> model.defaultEffort
            else -> model.efforts.first()
        }
        // 有强度可选时先让用户把强度定下来；没有就选完即收起
        showNewTaskModels = model.efforts.size > 1
    }

    private fun selectNewTaskEffort(effort: String) {
        newTaskEffort = effort
        showNewTaskModels = false
    }

    /** 任务行 / 新任务共用的对话页；草稿态 taskId 为空。 */
    private fun openBusChat(task: BusHomeTask) {
        val device = homeView.device ?: return
        val agent = homeView.agents.firstOrNull { it.key == task.agentKey }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "bus_chat",
            JSONObject().apply {
                put("pageName", "bus_chat")
                put("taskId", task.id)
                put("agentId", device.agentId)
                put("hostName", device.name)
                put("source", task.source)
                put("connectorId", task.connectorId.orEmpty())
                put("agentKey", task.agentKey)
                put("agentName", task.agent)
                put("projectPath", task.projectPath)
                put("projectName", task.project)
                put("title", task.title)
                put("model", task.model)
                put("effort", task.effort)
                put("controllable", task.controllable)
                put("autoApprove", task.autoApprove)
                put("canAutoApprove", agent?.canAutoApprove == true)
            },
        )
    }

    /** 审批卡点整卡 / 「自定义回复…」都进会话页，输入框在那里 */
    private fun openApprovalTask(item: BusApprovalItem) {
        val task = homeView.tasks.firstOrNull { it.id == item.taskId } ?: return
        openBusChat(task)
    }

    /**
     * 外面筛选 Agent，进去直接聊：不再经过“新建任务”表单
     * 勾了唯一一个 Agent 就用它，否则用第一个可启动的；默认偏好后续再补
     */
    private fun openDraftChat() {
        val device = homeView.device ?: return
        val agents = startableAgents()
        val agent = selectedAgentKeys.singleOrNull()
            ?.let { key -> agents.firstOrNull { it.key == key } }
            ?: agents.firstOrNull()
            ?: return
        val project = homeView.projects.firstOrNull()
        val (model, effort) = defaultModelForAgent(agent)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "bus_chat",
            JSONObject().apply {
                put("pageName", "bus_chat")
                put("taskId", "")
                put("agentId", device.agentId)
                put("hostName", device.name)
                put("source", agent.source)
                put("connectorId", agent.connectorId.orEmpty())
                put("agentKey", agent.key)
                put("agentName", agent.name)
                put("projectPath", project?.path.orEmpty())
                put("projectName", project?.name.orEmpty())
                put("title", "新建任务")
                put("model", model)
                put("effort", effort)
                put("controllable", true)
                put("autoApprove", false)
                put("canAutoApprove", agent.canAutoApprove)
            },
        )
    }

    private fun openNewTask() {
        val agents = startableAgents()
        val first = agents.firstOrNull()
        newTaskAgentKey = first?.key.orEmpty()
        val (model, effort) = first?.let { defaultModelForAgent(it) } ?: ("" to "")
        newTaskModelId = model
        newTaskEffort = effort
        newTaskProjectPath = homeView.projects.firstOrNull()?.path.orEmpty()
        newTaskPrompt = ""
        newTaskError = ""
        newTaskBusy = false
        newTaskKeyboardHeight = 0f
        showNewTaskModels = false
        showNewTaskSheet = true
    }

    private fun submitNewTask() {
        if (newTaskBusy) return
        val agent = startableAgents().firstOrNull { it.key == newTaskAgentKey }
        if (agent == null) {
            newTaskError = "请选择 Agent"
            return
        }
        val prompt = newTaskPrompt.trim()
        if (prompt.isEmpty()) {
            newTaskError = "请描述要做什么"
            return
        }
        val device = homeView.device
        if (device == null) {
            newTaskError = "电脑未连接"
            return
        }
        newTaskBusy = true
        newTaskError = ""
        sendCommand(
            retryKey = listOf(
                "startTask",
                device.agentId,
                agent.source,
                agent.connectorId.orEmpty(),
                newTaskProjectPath,
                prompt,
                newTaskModelId,
                newTaskEffort,
            ).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.startTask(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = device.agentId,
                    source = agent.source,
                    connectorId = agent.connectorId,
                    projectPath = newTaskProjectPath,
                    prompt = prompt,
                    model = newTaskModelId,
                    effort = newTaskEffort,
                )
            },
        ) { result ->
            setTimeout(pagerId, 0) {
                newTaskBusy = false
                when (result) {
                    is BusSendResult.Confirmed -> if (result.ok) {
                        showNewTaskSheet = false
                        showNewTaskModels = false
                        newTaskPrompt = ""
                        newTaskKeyboardHeight = 0f
                        setTimeout(pagerId, 800) { refreshBusHome() }
                        setTimeout(pagerId, 2_500) { refreshBusHome() }
                    } else {
                        newTaskError = result.error.ifBlank { "创建失败" }
                    }
                    is BusSendResult.Failed -> newTaskError = failedCommandMessage(result, "创建失败")
                    else -> newTaskError = result.message
                }
            }
        }
    }

    // ---------- 审批 ----------

    private fun toggleApprovalOption(item: BusApprovalItem, question: BusApprovalQuestion, label: String) {
        val key = busApprovalSelectionKey(item.key, question.id)
        val labels = busApprovalToggleOption(question, approvalSelection[key].orEmpty(), label)
        approvalSelection = approvalSelection + (key to labels)
    }

    /**
     * 发送 approve
     *
     * 快照不会把已经处理过的请求撤掉，所以「处理过没有」由这里本地记；
     * 拒绝按协议不带 answers，批准把当前勾选项原样回给电脑
     */
    private fun answerApproval(item: BusApprovalItem, allow: Boolean, answers: Map<String, List<String>>) {
        val device = homeView.device ?: run {
            approvalError = "电脑未连接"
            return
        }
        if (approvalBusyKey.isNotEmpty()) return
        approvalBusyKey = item.key
        approvalError = ""
        sendCommand(
            retryKey = listOf(
                "approve",
                device.agentId,
                item.taskId,
                item.requestId,
                allow.toString(),
                answers.entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" },
            ).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.approve(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = device.agentId,
                    taskId = item.taskId,
                    requestId = item.requestId,
                    allow = allow,
                    answers = answers,
                )
            },
        ) { result ->
            setTimeout(pagerId, 0) {
                approvalBusyKey = ""
                when (result) {
                    is BusSendResult.Confirmed -> if (result.ok) {
                        approvalAnswered = approvalAnswered + (item.key to allow)
                        clearApprovalSelection(item.key)
                        refreshBusHome()
                        setTimeout(pagerId, 800) { refreshBusHome() }
                    } else {
                        approvalError = result.error.ifBlank { "发送失败" }
                    }
                    is BusSendResult.Failed -> approvalError = failedCommandMessage(result, "发送失败")
                    else -> approvalError = result.message
                }
            }
        }
    }

    private fun clearApprovalSelection(itemKey: String) {
        val prefix = busApprovalSelectionKey(itemKey, "")
        val next = approvalSelection.filterKeys { !it.startsWith(prefix) }
        if (next != approvalSelection) approvalSelection = next
    }

    /**
     * 快照是全量替换，这里把已经不存在的卡片裁掉
     *
     * 不裁的话桌面端重复投递同一个 requestId 时，那张卡会一直灰着
     */
    private fun pruneApprovalState() {
        // 按「不过滤」的口径裁，免得切 Agent 筛选把别的卡片处理记录也清了
        val keys = homeView.approvalItems(emptySet()).map { it.key }.toSet()
        if (approvalAnswered.keys.any { it !in keys }) {
            approvalAnswered = approvalAnswered.filterKeys { it in keys }
        }
        val prefixes = keys.map { it + "|" }
        val nextSelection = approvalSelection.filterKeys { key -> prefixes.any { key.startsWith(it) } }
        if (nextSelection != approvalSelection) approvalSelection = nextSelection
    }

    // ---------- Agent 重启（协议 3.9） ----------

    /** 「重试启动」先确认：重启会中断在跑、等审批、等回答的会话 */
    private fun requestRestartConnector(agentKey: String) {
        if (agentKey.isBlank() || restartBusy) return
        restartError = ""
        restartConfirmAgentKey = agentKey
    }

    private fun confirmRestartConnector() {
        val agentKey = restartConfirmAgentKey
        val device = homeView.device
        if (agentKey.isBlank()) return
        if (device == null) {
            restartError = "电脑未连接"
            return
        }
        if (!busCanRestartConnector(device.canRestartConnectors)) {
            restartError = "这台电脑的 Agent Host 还不支持重启"
            return
        }
        val (connector, connectorId) = busConnectorTarget(agentKey)
        restartBusy = true
        restartError = ""
        agentBusyKey = agentKey
        sendCommand(
            retryKey = listOf("restartConnector", device.agentId, connector, connectorId.orEmpty()).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.restartConnector(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = device.agentId,
                    connector = connector,
                    connectorId = connectorId,
                )
            },
        ) { result ->
            setTimeout(pagerId, 0) {
                restartBusy = false
                agentBusyKey = ""
                when (result) {
                    is BusSendResult.Confirmed -> if (result.ok) {
                        restartConfirmAgentKey = ""
                        refreshBusHome()
                        setTimeout(pagerId, 1_500) { refreshBusHome() }
                        setTimeout(pagerId, 5_000) { refreshBusHome() }
                    } else {
                        restartError = result.error.ifBlank { "重启失败" }
                    }
                    is BusSendResult.Failed -> restartError = failedCommandMessage(result, "重启失败")
                    else -> restartError = result.message
                }
            }
        }
    }

    private fun removeCurrentComputer() {
        if (removeBusy) return
        // 先记下 hostId：配对档案清掉之后就查不到它了
        val removedHostId = knownHostId()
        removeBusy = true
        removeError = ""
        runCatching {
            acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).forget { ok ->
                setTimeout(pagerId, 0) {
                    removeBusy = false
                    if (!ok) {
                        removeError = "移除失败，请重试"
                        return@setTimeout
                    }
                    runCatching { store?.clearRelayProfile() }
                    clearHostCache(removedHostId)
                    localHostName = ""
                    showRemoveComputer = false
                    showSettings = false
                    homeView = BusHomeView(device = null, agents = emptyList(), tasks = emptyList())
                    everLoaded = false
                    waitingTooLong = false
                    acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                }
            }
        }.onFailure {
            removeBusy = false
            removeError = it.message ?: "移除失败"
        }
    }

    /** bus:<hostId> 与 DSH 原生 relay:<hostId> 一起清；其他电脑/SSH 配置不动。 */
    private fun clearHostCache(hostId: String) {
        if (hostId.isBlank()) return
        val cleared = localBusCache?.let { runCatching { it.clearHost(hostId) }.isSuccess } == true
        if (!cleared) {
            runCatching { store?.clearBusCache(BusCacheScope.bus(hostId)) }
            runCatching { store?.clearScope(BusCacheScope.relay(hostId)) }
        }
        if (this.hostId == hostId) {
            this.hostId = ""
            lastCachedSnapshot = ""
        }
    }

    private fun openProjectMenu(name: String, path: String) {
        if (path.isBlank() || homeView.device?.canRemoveProjects != true) return
        projectMenuName = name
        projectMenuPath = path
    }

    private fun closeProjectMenu() {
        projectMenuPath = ""
        projectMenuName = ""
    }

    private fun requestRemoveProject() {
        if (projectMenuPath.isBlank()) return
        removeProjectPath = projectMenuPath
        removeProjectError = ""
        closeProjectMenu()
        showRemoveProject = true
    }

    /** 协议 3.8：把项目从电脑端列表移出，成功后电脑发全量快照。 */
    private fun removeProject() {
        if (removeProjectBusy) return
        val path = removeProjectPath
        if (path.isBlank()) return
        val device = homeView.device ?: run {
            removeProjectError = "电脑未连接"
            return
        }
        removeProjectBusy = true
        removeProjectError = ""
        sendCommand(
            retryKey = listOf("removeProject", device.agentId, path).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.removeProject(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = device.agentId,
                    projectPath = path,
                )
            },
        ) { result ->
            setTimeout(pagerId, 0) {
                removeProjectBusy = false
                when (result) {
                    is BusSendResult.Confirmed -> if (result.ok) {
                        showRemoveProject = false
                        removeProjectPath = ""
                        // 先刷新一次，再等下一轮轮询收尾；重复移除已隐藏项目电脑回成功
                        refreshBusHome()
                        setTimeout(pagerId, 800) { refreshBusHome() }
                    } else {
                        removeProjectError = result.error.ifBlank { "移除失败" }
                    }
                    is BusSendResult.Failed -> removeProjectError = failedCommandMessage(result, "移除失败")
                    else -> removeProjectError = result.message
                }
            }
        }
    }

    /**
     * 统一发送入口：可重试的结果保留命令对象，下一次点击复用同一 id + createdAt
     */
    private fun sendCommand(
        retryKey: String,
        build: (id: String, createdAt: String) -> String,
        onDone: (BusSendResult) -> Unit,
    ) {
        val command = retryCommands[retryKey] ?: commandSender.prepare(build)
        commandSender.send(command) { result ->
            if (result.retryable) retryCommands[retryKey] = command else retryCommands.remove(retryKey)
            onDone(result)
        }
    }

    private fun failedCommandMessage(result: BusSendResult.Failed, fallback: String): String = when {
        result.body.isNotBlank() -> "$fallback：${result.body}"
        result.status == 0 -> result.message
        else -> "$fallback（${result.status}）"
    }

    override fun created() {
        super.created()
        // 首页所有尺寸都按画布 375 等比换算；created 先设一次，避免首屏用到默认值 1
        BusHomeTokens.updateHomeDesignScale(pagerData.pageViewWidth)
        getBackPressHandler().addCallback(object : BackPressCallback() {
            override fun handleOnBackPressed() {
                when {
                    showThemePicker -> showThemePicker = false
                    showRemoveProject -> showRemoveProject = false
                    projectMenuPath.isNotEmpty() -> closeProjectMenu()
                    restartConfirmAgentKey.isNotEmpty() -> restartConfirmAgentKey = ""
                    showRenameComputer -> showRenameComputer = false
                    showRemoveComputer -> showRemoveComputer = false
                    showNewTaskModels -> showNewTaskModels = false
                    showNewTaskSheet -> showNewTaskSheet = false
                    showAddComputer -> showAddComputer = false
                    showSettings -> showSettings = false
                    showDeviceSheet -> showDeviceSheet = false
                    showAgentSheet -> closeAgentSheet()
                    else -> acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                }
            }
        })
        connectionMode = pageData.params.optString("connectionMode")
        databaseDir = pageData.params.optString("databaseDir")
        store = if (databaseDir.isEmpty()) {
            null
        } else {
            runCatching { createDshLocalStore("$databaseDir/dsh.db", null) }.getOrNull()
        }
        localBusCache = store?.let { runCatching { DshLocalBusSnapshotCache(it) }.getOrNull() }
        busCache = localBusCache?.let {
            runCatching { BusCache(it, nowIso = { BusCommands.createdAt() }) }.getOrNull()
        }
        hostId = knownHostId()
        localHostName = runCatching { store?.loadRelayProfile()?.hostName }
            .getOrNull()
            .orEmpty()
        hasConnection = connectionMode.isNotEmpty()
        val savedView = runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .getItem(BUS_VIEW_MODE_KEY)
        }.getOrNull()
        listViewMode = if (savedView == BUS_VIEW_PROJECTS) BUS_VIEW_PROJECTS else BUS_VIEW_RECENT
        viewMode = listViewMode
        themeOptions.clear()
        themeOptions.addAll(
            listOf(
                DshSettingsChoice("system", "跟随系统"),
                DshSettingsChoice("light", "浅色"),
                DshSettingsChoice("dark", "深色"),
                DshSettingsChoice("sunrise-sunset", "日出日落"),
            ),
        )
        setTimeout(pagerId, 50) { syncNavigationBarColor() }
        applyBodyState()
        // 冷启动：先用本地缓存渲染，再等网络快照覆盖
        restoreCachedHome()
        refreshBusHome()
        pollBusHome()
        connectIfNeeded()
        if (hasConnection) {
            setTimeout(pagerId, 8_000) {
                if (alive && homeView.device == null) {
                    waitingTooLong = true
                    // 到点还是没拿到快照，审批区不能再挂在「正在同步」上转圈：
                    // 这里放行，让它落到缺省页并说清是电脑没响应；
                    // 之后电脑真回快照了，applyHomeView 会把内容填回来
                    approvalLoading = false
                }
            }
        } else {
            // 压根没有连接（没配对、也没本地宿主）：没有任何一轮同步会成功，直接结束等待
            approvalLoading = false
        }
    }

    /** 从连接页进来时，探测连接已被拆掉，统一首页自己重新连上 Relay。 */
    private fun connectIfNeeded() {
        if (pageData.params.optString("connectionMode") != "relay") return
        runCatching {
            acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).connect { refreshBusHome() }
        }
    }

    private fun refreshBusHome() {
        val json = runCatching {
            acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).busHomeJson()
        }.getOrNull() ?: return
        val element = runCatching { busParse(json) }.getOrNull() ?: return
        val next = buildHomeView(element) ?: return
        resolveHostId(next)
        applyHomeView(next)
        cacheSnapshot(json)
    }

    /** 协议校验和映射都走 BusStore；坏快照返回 null，页面保持原状态。 */
    private fun buildHomeView(element: JsonElement): BusHomeView? = runCatching {
        val busStore = BusStore()
        if (busStore.applySnapshot(element) == null) return@runCatching null
        busStore.toHomeView().takeIf { it.device != null }
    }.getOrNull()

    private fun applyHomeView(next: BusHomeView) {
        everLoaded = true
        waitingTooLong = false
        approvalLoading = false
        if (next != homeView) homeView = next
        // 电脑上的 Agent 少掉了就把勾选项裁掉，免得筛选出一个空列表；
        // 全部可勾选的 Agent 都在时归一成空集合（全选），与面板首次打开的语义一致
        if (selectedAgentKeys.isNotEmpty()) {
            val selectable = busAgentSelectableKeys(next.agents)
            val kept = selectedAgentKeys.filterTo(mutableSetOf()) { it in selectable }
            val normalized = if (kept.isEmpty() || kept.containsAll(selectable)) emptySet() else kept
            if (normalized != selectedAgentKeys) selectedAgentKeys = normalized
        }
        pruneApprovalState()
        initProjectCollapseIfNeeded()
        syncTaskRows()
    }

    /** 当前电脑的稳定 id：优先用配对档案，其次路由参数；都不行才退回快照 agentId。 */
    private fun knownHostId(): String {
        val fromProfile = runCatching { store?.loadRelayProfile()?.hostId }
            .getOrNull()
            .orEmpty()
        if (fromProfile.isNotEmpty()) return fromProfile
        val fromParams = pageData.params.optString("profileId")
        return if (fromParams.isNotEmpty() && fromParams != DshSessionScope.DEFAULT_REMOTE_PROFILE_ID) {
            fromParams
        } else {
            ""
        }
    }

    private fun resolveHostId(view: BusHomeView) {
        hostId = knownHostId().ifEmpty { view.device?.agentId.orEmpty() }
    }

    private fun restoreCachedHome() {
        val cache = busCache ?: return
        val ids = linkedSetOf<String>()
        knownHostId().takeIf { it.isNotEmpty() }?.let { ids += it }
        hostId.takeIf { it.isNotEmpty() }?.let { ids += it }
        for (id in ids) {
            val entry = runCatching { cache.load(id) }.getOrNull() ?: continue
            val restored = buildHomeView(entry.snapshot) ?: continue
            hostId = id
            applyHomeView(restored)
            return
        }
    }

    private fun cacheSnapshot(json: String) {
        val cache = busCache ?: return
        val id = hostId
        if (id.isEmpty() || json == lastCachedSnapshot) return
        if (runCatching { cache.save(id, json) }.isSuccess) lastCachedSnapshot = json
    }

    private fun pollBusHome() {
        setTimeout(pagerId, 2_000) {
            if (!alive) return@setTimeout
            refreshBusHome()
            pollBusHome()
        }
    }

    private fun syncNavigationBarColor() {
        // 导航栏本身保持透明（宿主已整屏沉浸），这里只按主题翻系统图标的明暗：
        // 浅色底用深色图标，深色底用浅色图标
        val argb = if (themeColors.isDark) 0xFF151517 else 0xFFF4F4F6
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(argb, themeColors.isDark)
        }
    }

    override fun onThemeSynced() {
        syncNavigationBarColor()
    }

    override fun pageWillDestroy() {
        alive = false
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(0x00000000L, themeColors.isDark)
        }
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        return {
            // 绑定值必须是单个可比较对象：这里只放「会改变结构」的东西
            // （视图模式、Agent 多选、配色、有没有电脑），
            // 快照内容改的是 taskRows / 文案 lambda，不参与重建
            vbind({
                BusHomeBindKey(
                    pageWidth = this@DshBusHomePage.pagerData.pageViewWidth,
                    viewMode = this@DshBusHomePage.viewMode,
                    selectedAgentKeys = this@DshBusHomePage.selectedAgentKeys,
                    palette = this@DshBusHomePage.palette(),
                    hasDevice = this@DshBusHomePage.homeView.device != null,
                    hasConnection = this@DshBusHomePage.hasConnection,
                )
            }) {
                // 旋转/分屏改了页宽时重算系数，key 变了整棵子树才会按新系数重建
                BusHomeTokens.updateHomeDesignScale(this@DshBusHomePage.pagerData.pageViewWidth)
                val p = this@DshBusHomePage.palette()
                val pageWidth = this@DshBusHomePage.pagerData.pageViewWidth
                val pageHeight = this@DshBusHomePage.pagerData.pageViewHeight
                val viewMode = this@DshBusHomePage.viewMode
                View {
                    attr {
                        flex(1f)
                        flexDirectionColumn()
                        backgroundColor(p.bgBase)
                        paddingTop(
                            dshTopInset(
                                this@DshBusHomePage.pagerData.statusBarHeight,
                                this@DshBusHomePage.pagerData.safeAreaInsets.top,
                            ),
                        )
                    }

                    // ① 头部淡紫层（绝对定位、不吃触摸，压在其它内容下面）
                    BusHomeHeaderGlow(pageWidth = pageWidth, isDark = p.isDark)

                    // ② 顶部区：top-panel padding 6 20 12
                    View {
                        attr {
                            flexDirectionColumn()
                            paddingLeft(BusHomeTokens.spacePage)
                            paddingRight(BusHomeTokens.spacePage)
                            paddingTop(BusHomeSectionTokens.topPanelPaddingTop)
                            paddingBottom(BusHomeSectionTokens.topPanelPaddingBottom)
                        }
                        View {
                            attr {
                                width(busHomeCardWidth(pageWidth))
                                flexDirectionRow()
                                alignItemsCenter()
                                justifyContentSpaceBetween()
                            }
                            BusHomeDeviceEntry(
                                palette = p,
                                platformAsset = this@DshBusHomePage.devicePlatformAsset(),
                                online = this@DshBusHomePage.homeView.device?.online == true,
                                disabled = this@DshBusHomePage.deviceEntryDisabled(),
                                // 胶囊最多长到设置按钮左边为止（335 - 40 - 10）
                                maxWidth = busHomeCardWidth(pageWidth) -
                                    BusHomeSectionTokens.settingsSize - BusHomeTokens.gapControl,
                                title = { this@DshBusHomePage.deviceTitleText() },
                                pendingCount = { this@DshBusHomePage.visiblePendingCount() },
                                runningCount = { this@DshBusHomePage.visibleRunningCount() },
                                onClick = { this@DshBusHomePage.openDeviceSheet() },
                            )
                            BusHomeSettingsEntry(
                                palette = p,
                                onClick = { this@DshBusHomePage.openSettings() },
                            )
                        }
                        BusHomeListControls(
                            controlsWidth = busHomeCardWidth(pageWidth),
                            viewMode = viewMode,
                            palette = p,
                            agents = this@DshBusHomePage.homeView.agents,
                            selectedKeys = this@DshBusHomePage.selectedAgentKeys,
                            approveCount = { this@DshBusHomePage.openApprovalCount() },
                            agentsEmptyLabel = { this@DshBusHomePage.agentEntryLabel() },
                            onSelectView = { this@DshBusHomePage.selectListViewMode(it) },
                            onOpenAgentSheet = { this@DshBusHomePage.openAgentSheet() },
                        )
                    }

                    // ③ 列表区 / 审批区（共用顶部导航与底部栏，不新开页面）
                    vif({
                        this@DshBusHomePage.viewMode != BUS_VIEW_APPROVAL &&
                            this@DshBusHomePage.bodyState == BusHomeBodyState.Content
                    }) {
                        BusHomeTaskList(
                            rows = this@DshBusHomePage.taskRows,
                            cardWidth = busHomeCardWidth(pageWidth),
                            rowWidth = busHomeRowWidth(pageWidth),
                            palette = p,
                            topFadeVisible = { this@DshBusHomePage.listScrolled },
                            initialRenderRows = BUS_HOME_INITIAL_RENDER_ROWS,
                            maxRenderedRows = BUS_HOME_MAX_RENDERED_ROWS,
                            preloadHeight = pageHeight,
                            onScrollY = {
                                this@DshBusHomePage.listScrolled = it > 1f
                            },
                            onOpenTask = { this@DshBusHomePage.openBusChat(it) },
                            onToggleGroup = { this@DshBusHomePage.toggleProject(it) },
                            onGroupMenu = { this@DshBusHomePage.openProjectMenu(it.name, it.path) },
                        )
                    }
                    vif({
                        this@DshBusHomePage.viewMode != BUS_VIEW_APPROVAL &&
                            this@DshBusHomePage.bodyState == BusHomeBodyState.DisabledComputer
                    }) {
                        // 没有电脑：保留改版前的「还没有连接电脑」空态与连接入口
                        BusHomeNoComputer(
                            palette = p,
                            onGuide = { this@DshBusHomePage.openDeviceSheet() },
                            onScan = { this@DshBusHomePage.openAddComputer() },
                            onSsh = { this@DshBusHomePage.openSshSetup() },
                        )
                    }
                    vif({
                        this@DshBusHomePage.viewMode != BUS_VIEW_APPROVAL &&
                            this@DshBusHomePage.bodyState == BusHomeBodyState.NoTasks
                    }) {
                        BusHomeEmptyTasks(palette = p, isDark = p.isDark)
                    }
                    vif({ this@DshBusHomePage.viewMode == BUS_VIEW_APPROVAL }) {
                        // 卡片序列 + 筛选段变化才重建整块；作答状态一律走 lambda，
                        // 否则每处理一张卡就整列重建、滚动位置丢失
                        vbind({ this@DshBusHomePage.approvalBindKey() }) {
                            BusApprovalBoard(
                                items = this@DshBusHomePage.approvalItemsNow(),
                                filter = this@DshBusHomePage.approvalFilter,
                                answered = { this@DshBusHomePage.approvalAnswered },
                                selection = { this@DshBusHomePage.approvalSelection },
                                busyKey = { this@DshBusHomePage.approvalBusyKey },
                                error = { this@DshBusHomePage.approvalError },
                                loading = { this@DshBusHomePage.approvalLoading },
                                emptyTitle = { this@DshBusHomePage.approvalEmptyTitle() },
                                emptySub = { this@DshBusHomePage.approvalEmptySub() },
                                topFadeVisible = { this@DshBusHomePage.approvalScrolled },
                                palette = p,
                                onFilter = { this@DshBusHomePage.approvalFilter = it },
                                onToggleOption = { item, question, label ->
                                    this@DshBusHomePage.toggleApprovalOption(item, question, label)
                                },
                                onAnswer = { item, allow, answers ->
                                    this@DshBusHomePage.answerApproval(item, allow, answers)
                                },
                                onOpenTask = { this@DshBusHomePage.openApprovalTask(it) },
                                onScrollY = { this@DshBusHomePage.approvalScrolled = it > 1f },
                            )
                        }
                    }

                    // ④ 底部 160 渐隐 + ⑤ 悬浮栏（胶囊 + FAB），都是绝对定位
                    BusHomeBottomFade(isDark = p.isDark)
                    BusHomeBottomBar(
                        barWidth = busHomeCardWidth(pageWidth),
                        palette = p,
                        viewMode = { this@DshBusHomePage.viewMode },
                        badgeCount = { this@DshBusHomePage.openApprovalCount() },
                        hidden = { this@DshBusHomePage.showSettings },
                        fabEnabled = this@DshBusHomePage.homeView.device != null,
                        onSelectListView = { this@DshBusHomePage.selectListView() },
                        onSelectApprovalView = { this@DshBusHomePage.selectApprovalView() },
                        onNewTask = { this@DshBusHomePage.openDraftChat() },
                    )
                }
            }

            // Agent 面板（多选 + 展开能力矩阵 + 报错行重试）
            vif({ this@DshBusHomePage.showAgentSheet }) {
                vbind({ this@DshBusHomePage.agentSheetBindKey() }) {
                    BusAgentSheet(
                        agents = this@DshBusHomePage.homeView.agents,
                        palette = this@DshBusHomePage.palette(),
                        pageWidth = this@DshBusHomePage.pagerData.pageViewWidth,
                        pageHeight = this@DshBusHomePage.pagerData.pageViewHeight,
                        canRestartConnectors = busCanRestartConnector(
                            this@DshBusHomePage.homeView.device?.canRestartConnectors == true,
                        ),
                        selectedKeys = { this@DshBusHomePage.selectedAgentKeys },
                        expandedKey = { this@DshBusHomePage.agentExpandedKey },
                        busyKey = { this@DshBusHomePage.agentBusyKey },
                        errorKey = { this@DshBusHomePage.agentErrorKey },
                        onClose = { this@DshBusHomePage.closeAgentSheet() },
                        onToggle = { this@DshBusHomePage.toggleAgentSelection(it) },
                        onSelectAll = { this@DshBusHomePage.selectAllAgents() },
                        onExpand = { this@DshBusHomePage.toggleAgentExpanded(it) },
                        onRestart = { this@DshBusHomePage.requestRestartConnector(it) },
                        // 「查看详情」就是长按浮层那一块：没有单独的 Agent 详情页
                        onOpenDetail = { this@DshBusHomePage.agentErrorKey = it },
                        onShowError = { this@DshBusHomePage.agentErrorKey = it },
                        onHideError = { this@DshBusHomePage.agentErrorKey = "" },
                    )
                }
            }

            // 电脑面板（当前设备行 + 添加电脑入口）
            vif({ this@DshBusHomePage.showDeviceSheet }) {
                vbind({ this@DshBusHomePage.homeView.device to this@DshBusHomePage.localHostName }) {
                    BusDeviceSheet(
                        device = this@DshBusHomePage.homeView.device?.let { device ->
                            device.copy(name = this@DshBusHomePage.currentComputerName())
                        },
                        pendingCount = this@DshBusHomePage.visiblePendingCount(),
                        runningCount = this@DshBusHomePage.visibleRunningCount(),
                        palette = this@DshBusHomePage.palette(),
                        pageWidth = this@DshBusHomePage.pagerData.pageViewWidth,
                        pageHeight = this@DshBusHomePage.pagerData.pageViewHeight,
                        onClose = { this@DshBusHomePage.showDeviceSheet = false },
                        onAddComputer = { this@DshBusHomePage.openAddComputer() },
                    )
                }
            }

            vif({ this@DshBusHomePage.showSettings }) {
                vbind({ this@DshBusHomePage.localHostName }) {
                this.BusSettingsOverlay(
                    computers = this@DshBusHomePage.settingsComputers(),
                    agents = this@DshBusHomePage.homeView.agents,
                    themeModeLabel = { dshThemeModeLabel(this@DshBusHomePage.themeMode) },
                    statusBarHeight = dshTopInset(
                        this@DshBusHomePage.pagerData.statusBarHeight,
                        this@DshBusHomePage.pagerData.safeAreaInsets.top,
                    ),
                    pageWidth = this@DshBusHomePage.pagerData.pageViewWidth,
                    colors = { this@DshBusHomePage.themeColors },
                    onClose = { this@DshBusHomePage.showSettings = false },
                    onAddComputer = { this@DshBusHomePage.openAddComputer() },
                    onRenameComputer = { this@DshBusHomePage.openRenameComputer() },
                    onRemoveComputer = {
                        this@DshBusHomePage.removeError = ""
                        this@DshBusHomePage.showRemoveComputer = true
                    },
                    onPickTheme = { this@DshBusHomePage.showThemePicker = true },
                )
                }
            }
            vif({ this@DshBusHomePage.showAddComputer }) {
                BusAddComputerSheet(
                    busy = this@DshBusHomePage.addComputerBusy,
                    error = this@DshBusHomePage.addComputerError,
                    replaceHint = this@DshBusHomePage.homeView.device != null,
                    colors = this@DshBusHomePage.themeColors,
                    onClose = { this@DshBusHomePage.showAddComputer = false },
                    onScan = { this@DshBusHomePage.scanAddComputer() },
                    onSsh = { this@DshBusHomePage.openSshSetup() },
                )
            }
            vif({ this@DshBusHomePage.showNewTaskSheet }) {
                BusNewTaskSheet(
                    agents = this@DshBusHomePage.startableAgents(),
                    projects = this@DshBusHomePage.homeView.projects,
                    selectedAgentKey = { this@DshBusHomePage.newTaskAgentKey },
                    selectedProjectPath = { this@DshBusHomePage.newTaskProjectPath },
                    modelLabel = { this@DshBusHomePage.newTaskModelLabel() },
                    modelEnabled = { this@DshBusHomePage.newTaskHasModels() },
                    modelListVisible = { this@DshBusHomePage.showNewTaskModels },
                    modelOptions = { this@DshBusHomePage.newTaskModels() },
                    selectedModelId = { this@DshBusHomePage.newTaskModelId },
                    selectedEffort = { this@DshBusHomePage.newTaskEffort },
                    agentName = { this@DshBusHomePage.newTaskAgentName() },
                    busy = { this@DshBusHomePage.newTaskBusy },
                    error = { this@DshBusHomePage.newTaskError },
                    canSubmit = { this@DshBusHomePage.newTaskPrompt.trim().isNotEmpty() },
                    keyboardHeight = { this@DshBusHomePage.newTaskKeyboardHeight },
                    colors = this@DshBusHomePage.themeColors,
                    onClose = {
                        this@DshBusHomePage.showNewTaskSheet = false
                        this@DshBusHomePage.showNewTaskModels = false
                        this@DshBusHomePage.newTaskKeyboardHeight = 0f
                    },
                    onSelectAgent = { this@DshBusHomePage.selectNewTaskAgent(it) },
                    onSelectProject = { this@DshBusHomePage.newTaskProjectPath = it },
                    onPromptChange = { this@DshBusHomePage.newTaskPrompt = it },
                    onModelClick = { this@DshBusHomePage.toggleNewTaskModels() },
                    onSelectModel = { this@DshBusHomePage.selectNewTaskModel(it) },
                    onSelectEffort = { this@DshBusHomePage.selectNewTaskEffort(it) },
                    onKeyboardHeightChange = { this@DshBusHomePage.newTaskKeyboardHeight = it },
                    onSubmit = { this@DshBusHomePage.submitNewTask() },
                )
            }
            vif({ this@DshBusHomePage.showThemePicker }) {
                DshSettingsChoicePicker(
                    title = "外观",
                    options = { this@DshBusHomePage.themeOptions },
                    selectedValue = { this@DshBusHomePage.themePreferenceValue() },
                    busy = { false },
                    onClose = { this@DshBusHomePage.showThemePicker = false },
                    onSelect = { this@DshBusHomePage.applyTheme(it.value) },
                    colors = { this@DshBusHomePage.themeColors },
                )
            }
            vif({ this@DshBusHomePage.projectMenuPath.isNotEmpty() }) {
                BusProjectMenuSheet(
                    name = this@DshBusHomePage.projectMenuName,
                    c = this@DshBusHomePage.themeColors,
                    onClose = { this@DshBusHomePage.closeProjectMenu() },
                    onRemove = { this@DshBusHomePage.requestRemoveProject() },
                )
            }
            DshSessionConfirmDialog(
                visible = { this@DshBusHomePage.showRemoveProject },
                title = BUS_REMOVE_PROJECT_CONFIRM_TITLE,
                message = BUS_REMOVE_PROJECT_CONFIRM_MESSAGE,
                busyLabel = "移除中...",
                confirmLabel = "从列表移除",
                confirmColor = { this@DshBusHomePage.themeColors.stateErrorPrimary },
                busy = { this@DshBusHomePage.removeProjectBusy },
                error = { this@DshBusHomePage.removeProjectError },
                onCancel = {
                    this@DshBusHomePage.showRemoveProject = false
                    this@DshBusHomePage.removeProjectError = ""
                    this@DshBusHomePage.removeProjectPath = ""
                },
                onConfirm = { this@DshBusHomePage.removeProject() },
                pageViewWidth = this@DshBusHomePage.pagerData.pageViewWidth,
                colors = { this@DshBusHomePage.themeColors },
            )
            DshSessionConfirmDialog(
                visible = { this@DshBusHomePage.restartConfirmAgentKey.isNotEmpty() },
                title = BUS_RESTART_CONNECTOR_CONFIRM_TITLE,
                message = BUS_RESTART_CONNECTOR_CONFIRM_MESSAGE,
                busyLabel = "重启中...",
                confirmLabel = "重启",
                confirmColor = { this@DshBusHomePage.themeColors.stateErrorPrimary },
                busy = { this@DshBusHomePage.restartBusy },
                error = { this@DshBusHomePage.restartError },
                onCancel = {
                    this@DshBusHomePage.restartConfirmAgentKey = ""
                    this@DshBusHomePage.restartError = ""
                },
                onConfirm = { this@DshBusHomePage.confirmRestartConnector() },
                pageViewWidth = this@DshBusHomePage.pagerData.pageViewWidth,
                colors = { this@DshBusHomePage.themeColors },
            )
            DshSessionRenameDialog(
                visible = { this@DshBusHomePage.showRenameComputer },
                draft = { this@DshBusHomePage.renameDraft },
                busy = { this@DshBusHomePage.renameBusy },
                error = { this@DshBusHomePage.renameError },
                onDraftChange = { this@DshBusHomePage.renameDraft = it },
                onCancel = { this@DshBusHomePage.showRenameComputer = false },
                onSave = { this@DshBusHomePage.saveComputerName() },
                pageViewWidth = this@DshBusHomePage.pagerData.pageViewWidth,
                colors = { this@DshBusHomePage.themeColors },
                title = "重命名电脑",
                placeholder = "电脑名称",
            )
            DshSessionConfirmDialog(
                visible = { this@DshBusHomePage.showRemoveComputer },
                title = "移除这台电脑?",
                message = "移除后需要重新扫码配对；电脑上的 Agent 和任务不会被删除。",
                busyLabel = "移除中...",
                confirmLabel = "移除连接",
                confirmColor = { this@DshBusHomePage.themeColors.stateErrorPrimary },
                busy = { this@DshBusHomePage.removeBusy },
                error = { this@DshBusHomePage.removeError },
                onCancel = {
                    this@DshBusHomePage.showRemoveComputer = false
                    this@DshBusHomePage.removeError = ""
                },
                onConfirm = { this@DshBusHomePage.removeCurrentComputer() },
                pageViewWidth = this@DshBusHomePage.pagerData.pageViewWidth,
                colors = { this@DshBusHomePage.themeColors },
            )
        }
    }

    companion object {
        private const val BUS_VIEW_MODE_KEY = "bus_home_view_mode"

        /** 首屏最多先建多少行；400 个任务的其余行按滚动创建。 */
        private const val BUS_HOME_INITIAL_RENDER_ROWS = 20

        /** 列表里最多保留多少行节点。 */
        private const val BUS_HOME_MAX_RENDERED_ROWS = 60
    }
}


/** vbind 绑定值：只放会改变页面结构的字段，任一变化都会重建主页内容 */
private data class BusHomeBindKey(
    val pageWidth: Float,
    val viewMode: String,
    val selectedAgentKeys: Set<String>,
    val palette: BusHomePalette,
    val hasDevice: Boolean,
    val hasConnection: Boolean,
)

/** 「按项目」组标题菜单；目前只有协议 3.8 的移出列表一项。 */
private fun ViewContainer<*, *>.BusProjectMenuSheet(
    name: String,
    c: DshColorTokens,
    onClose: () -> Unit,
    onRemove: () -> Unit,
) {
    DshBottomSheet(
        colors = { c },
        onClose = onClose,
        initialDetent = DshSheetDetent.MEDIUM,
        panelHeight = 220f,
        panelBackground = { c.bgBase },
    ) {
        View {
            attr {
                height(busHomePx(42f))
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(busHomePx(20f))
                paddingRight(busHomePx(20f))
                marginTop(busHomePx(6f))
            }
            Text {
                attr {
                    text(name.ifBlank { "项目" })
                    fontSize(busHomePx(15f))
                    fontWeightBold()
                    color(c.labelPrimary)
                    flex(1f)
                    lines(1)
                }
            }
            View {
                attr {
                    size(busHomePx(30f), busHomePx(30f))
                    borderRadius(busHomePx(15f))
                    backgroundColor(homeSurface(c))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { onClose() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("x.svg"))
                        size(busHomePx(13f), busHomePx(13f))
                        tintColor(c.labelSecondary)
                    }
                }
            }
        }
        View {
            attr {
                marginTop(busHomePx(8f))
                marginLeft(busHomePx(20f))
                marginRight(busHomePx(20f))
                height(busHomePx(52f))
                borderRadius(busHomePx(14f))
                backgroundColor(homeSurface(c))
                alignItemsCenter()
                paddingLeft(busHomePx(16f))
            }
            event { click { onRemove() } }
            Text {
                attr {
                    text("从列表移除项目")
                    fontSize(busHomePx(15f))
                    fontWeightMedium()
                    color(c.stateErrorPrimary)
                }
            }
        }
    }
}

private fun homeSurface(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF2A2A31) else Color(0xFFF5F5F5)

/** 把 Kuikly relay / timer 适配成 BusCommandSender 的平台无关边界。 */
private class HomeRelayCommandTransport(
    private val relay: DshRelayModule,
    private val pagerId: String,
) : BusCommandTransport {
    override fun send(json: String, onAck: (BusCommandAck) -> Unit) {
        relay.busCommand(json) { ack -> onAck(BusCommandAck(ack.status, ack.body)) }
    }

    override fun snapshotJson(): String? = runCatching { relay.busHomeJson() }.getOrNull()

    override fun schedule(delayMs: Long, action: () -> Unit) {
        setTimeout(pagerId, delayMs.toInt()) { action() }
    }
}
