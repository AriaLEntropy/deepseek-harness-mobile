package com.example.dsh.ui.home

import com.example.dsh.base.BasePager
import com.example.dsh.base.BridgeModule
import com.example.dsh.bus.BusCommands
import com.example.dsh.bus.BusStore
import com.example.dsh.bus.busParse
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.connection.supportsRelayBridge
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.models.DshSettingsChoice
import com.example.dsh.session.DshRelayProfile
import com.example.dsh.storage.DshLocalStore
import com.example.dsh.storage.createDshLocalStore
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshThemeManager
import com.example.dsh.theme.DshThemeMode
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
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 统一首页（静态版）。
 *
 * 当前用本地 fixture 走 BusStore -> BusHomeView 的完整映射链路，
 * 仅用于校准布局与视觉；下一步把 AgentHostBus 收到的真实快照接进来。
 * 默认路由暂不指向本页，可用 adb 指定 pageName=bus_home 单独预览。
 */
@Page("bus_home")
internal class DshBusHomePage : BasePager() {
    private var homeView by observable(BusHomeView(device = null, agents = emptyList(), tasks = emptyList()))
    private var showAgentSheet by observable(false)
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
    private var localHostName by observable("")
    private var selectedAgentKey by observable("")
    private var viewMode by observable(VIEW_RECENT)
    private val collapsedProjects by observableList<String>()
    private val themeOptions by observableList<DshSettingsChoice>()
    private var everLoaded by observable(false)
    private var waitingTooLong by observable(false)
    private var hasConnection = false
    private var connectionMode = ""
    private var databaseDir = ""
    private var store: DshLocalStore? = null
    private var projectsInitialized = false
    private var alive = true

    private fun openAgentSheet() {
        showAgentSheet = true
    }

    private fun openDeviceSheet() {
        showDeviceSheet = true
    }

    private fun openSettings() {
        showSettings = true
    }

    private fun selectViewMode(mode: String) {
        if (viewMode == mode) return
        viewMode = mode
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(BUS_VIEW_MODE_KEY, mode)
        }
        initProjectCollapseIfNeeded()
    }

    /** 对齐原型：首次进入按项目时只展开第一组，其余折叠；用户手动操作后不再重置。 */
    private fun initProjectCollapseIfNeeded() {
        if (projectsInitialized || viewMode != VIEW_PROJECTS) return
        val groups = homeView.projectGroups(selectedAgentKey)
        if (groups.isEmpty()) return
        groups.drop(1).forEach { group ->
            if (!collapsedProjects.contains(group.key)) collapsedProjects.add(group.key)
        }
        projectsInitialized = true
    }

    private fun toggleProject(key: String) {
        if (collapsedProjects.contains(key)) collapsedProjects.remove(key) else collapsedProjects.add(key)
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

    private fun startableAgents(): List<BusHomeAgent> =
        homeView.agents.filter {
            it.available && it.enabled && (it.source == "acp" || it.source == "dsh")
        }

    private fun selectedNewTaskAgent(): BusHomeAgent? =
        startableAgents().firstOrNull { it.key == newTaskAgentKey }

    /**
     * 新建任务默认模型：优先取该 Agent 最近一次任务结尾的 model/effort；
     * 没有历史任务再退回 ConnectorInfo.models 的第一个。
     */
    private fun defaultModelForAgent(agent: BusHomeAgent): Pair<String, String> {
        val last = homeView.tasks
            .filter { it.agentKey == agent.key && it.model.isNotBlank() }
            .maxByOrNull { it.updatedAt }
        if (last == null) {
            val first = agent.models.firstOrNull()
            return first?.id.orEmpty() to first?.defaultEffort.orEmpty()
        }
        // 电脑还可能保留着已经下线的模型；列表里没有就退回电脑当前默认。
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
        // 有强度可选时先让用户把强度定下来；没有就选完即收起。
        showNewTaskModels = model.efforts.size > 1
    }

    private fun selectNewTaskEffort(effort: String) {
        newTaskEffort = effort
        showNewTaskModels = false
    }

    /** 任务行 / 新任务共用的对话页；草稿态 taskId 为空。 */
    private fun openBusChat(task: BusHomeTask) {
        val device = homeView.device ?: return
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "bus_chat",
            JSONObject().apply {
                put("pageName", "bus_chat")
                put("taskId", task.id)
                put("agentId", device.agentId)
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
            },
        )
    }

    /**
     * 外面筛选 Agent，进去直接聊：不再经过“新建任务”表单。
     * 选“全部 Agent”时先用第一个可启动的 Agent；默认偏好后续再补。
     */
    private fun openDraftChat() {
        val device = homeView.device ?: return
        val agents = startableAgents()
        val agent = agents.firstOrNull { it.key == selectedAgentKey } ?: agents.firstOrNull() ?: return
        val project = homeView.projects.firstOrNull()
        val (model, effort) = defaultModelForAgent(agent)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "bus_chat",
            JSONObject().apply {
                put("pageName", "bus_chat")
                put("taskId", "")
                put("agentId", device.agentId)
                put("source", agent.source)
                put("connectorId", agent.connectorId.orEmpty())
                put("agentKey", agent.key)
                put("agentName", agent.name)
                put("projectPath", project?.path.orEmpty())
                put("projectName", project?.name.orEmpty())
                put("title", "新任务")
                put("model", model)
                put("effort", effort)
                put("controllable", true)
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
        val json = BusCommands.startTask(
            commandId = BusCommands.newCommandId(),
            createdAt = BusCommands.createdAt(),
            agentId = device.agentId,
            source = agent.source,
            connectorId = agent.connectorId,
            projectPath = newTaskProjectPath,
            prompt = prompt,
            model = newTaskModelId,
            effort = newTaskEffort,
        )
        acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).busCommand(json) { ack ->
            setTimeout(pagerId, 0) {
                newTaskBusy = false
                if (ack.status == 200 || ack.status == 202) {
                    showNewTaskSheet = false
                    showNewTaskModels = false
                    newTaskPrompt = ""
                    newTaskKeyboardHeight = 0f
                    setTimeout(pagerId, 800) { refreshBusHome() }
                    setTimeout(pagerId, 2_500) { refreshBusHome() }
                } else {
                    newTaskError = if (ack.body.isNotBlank()) {
                        "创建失败：${ack.body}"
                    } else {
                        "创建失败（${ack.status}）"
                    }
                }
            }
        }
    }

    private fun removeCurrentComputer() {
        if (removeBusy) return
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

    override fun created() {
        super.created()
        getBackPressHandler().addCallback(object : BackPressCallback() {
            override fun handleOnBackPressed() {
                when {
                    showThemePicker -> showThemePicker = false
                    showRenameComputer -> showRenameComputer = false
                    showRemoveComputer -> showRemoveComputer = false
                    showNewTaskModels -> showNewTaskModels = false
                    showNewTaskSheet -> showNewTaskSheet = false
                    showAddComputer -> showAddComputer = false
                    showSettings -> showSettings = false
                    showDeviceSheet -> showDeviceSheet = false
                    showAgentSheet -> showAgentSheet = false
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
        localHostName = runCatching { store?.loadRelayProfile()?.hostName }
            .getOrNull()
            .orEmpty()
        hasConnection = connectionMode.isNotEmpty()
        if (!hasConnection) {
            homeView = previewBusHomeView()
        }
        val savedView = runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .getItem(BUS_VIEW_MODE_KEY)
        }.getOrNull()
        viewMode = if (savedView == VIEW_PROJECTS) VIEW_PROJECTS else VIEW_RECENT
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
        refreshBusHome()
        pollBusHome()
        connectIfNeeded()
        if (hasConnection) {
            setTimeout(pagerId, 8_000) {
                if (alive && homeView.device == null) waitingTooLong = true
            }
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
        val next = runCatching {
            val store = BusStore()
            if (store.applySnapshot(busParse(json)) == null) return@runCatching null
            store.toHomeView()
        }.getOrNull() ?: return
        if (next.device == null) return
        everLoaded = true
        waitingTooLong = false
        if (selectedAgentKey.isNotEmpty() && next.agents.none { it.key == selectedAgentKey }) {
            selectedAgentKey = ""
        }
        if (next != homeView) homeView = next
        initProjectCollapseIfNeeded()
    }

    private fun pollBusHome() {
        setTimeout(pagerId, 2_000) {
            if (!alive) return@setTimeout
            refreshBusHome()
            pollBusHome()
        }
    }

    private fun syncNavigationBarColor() {
        val argb = if (themeColors.isDark) 0xFF151517 else 0xFFFFFFFF
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
            // 绑定值必须是单个可比较对象：此前写成多条 `a to b` 语句，
            // Kotlin 只返回最后一条，themeColors 变化时比较结果不变，整块内容不会重建。
            vbind({
                BusHomeBindKey(
                    homeView = this@DshBusHomePage.homeView,
                    selectedAgentKey = this@DshBusHomePage.selectedAgentKey,
                    everLoaded = this@DshBusHomePage.everLoaded,
                    waitingTooLong = this@DshBusHomePage.waitingTooLong,
                    themeColors = this@DshBusHomePage.themeColors,
                    viewMode = this@DshBusHomePage.viewMode,
                    localHostName = this@DshBusHomePage.localHostName,
                )
            }) {
                // 在 vbind 内读取，主题切换时整块内容会按新颜色重建。
                val c = this@DshBusHomePage.themeColors
                val home = this@DshBusHomePage.homeView
                val selectedKey = this@DshBusHomePage.selectedAgentKey
                val device = home.device
                val visibleTasks = home.tasks.filter {
                    selectedKey.isEmpty() || it.agentKey == selectedKey
                }
                val pendingCount = visibleTasks.count { it.pending }
                val runningCount = visibleTasks.count { it.status == "running" }
                val selectedAgentName = home.agents.firstOrNull { it.key == selectedKey }?.name
                    ?: "全部 Agent"
                val deviceTitle = when {
                    device != null -> this@DshBusHomePage.localHostName.ifBlank { device.name }
                    !this@DshBusHomePage.hasConnection -> "未连接电脑"
                    this@DshBusHomePage.waitingTooLong -> "电脑未响应"
                    this@DshBusHomePage.everLoaded -> "电脑已离线"
                    else -> "正在连接电脑…"
                }
                val deviceSubtitle = when {
                    device != null -> "待处理 $pendingCount · 运行中 $runningCount"
                    !this@DshBusHomePage.hasConnection -> "在设置里添加电脑"
                    this@DshBusHomePage.waitingTooLong -> "请确认电脑端 Agent Host 已启动"
                    this@DshBusHomePage.everLoaded -> "请确认电脑端 Agent Host 仍在运行"
                    else -> "正在同步电脑上的 Agent"
                }
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    backgroundColor(c.bgBase)
                    paddingTop(pagerData.statusBarHeight)
                }

                // 设备条
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(20f)
                        paddingRight(20f)
                        paddingTop(8f)
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(14f)
                            paddingRight(14f)
                            paddingTop(9f)
                            paddingBottom(9f)
                            borderRadius(16f)
                            backgroundColor(c.bgLayer1)
                            border(Border(1f, BorderStyle.SOLID, c.borderL1))
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets(device?.platformAsset ?: "computer.svg"))
                                size(24f, 24f)
                                tintColor(c.labelSecondary)
                            }
                        }
                        View {
                            attr {
                                flexDirectionColumn()
                                marginLeft(10f)
                            }
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                }
                                View {
                                    attr {
                                        size(6f, 6f)
                                        borderRadius(3f)
                                        backgroundColor(
                                            if (device?.online == true) c.stateSuccessPrimary else c.labelTertiary,
                                        )
                                    }
                                }
                                Text {
                                    attr {
                                        text(deviceTitle)
                                        fontSize(13f)
                                        fontWeightBold()
                                        color(c.labelPrimary)
                                        marginLeft(5f)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text(deviceSubtitle)
                                    fontSize(11f)
                                    color(c.labelTertiary)
                                    marginTop(3f)
                                }
                            }
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("chevron-down.svg"))
                                size(10f, 10f)
                                tintColor(c.labelTertiary)
                                marginLeft(8f)
                            }
                        }
                        event { click { this@DshBusHomePage.openDeviceSheet() } }
                    }
                    View { attr { flex(1f) } }
                    View {
                        attr {
                            size(34f, 34f)
                            marginLeft(10f)
                            borderRadius(10f)
                            backgroundColor(c.bgLayer1)
                            border(Border(1f, BorderStyle.SOLID, c.borderL1))
                            alignItemsCenter()
                            justifyContentCenter()
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("settings-cube.svg"))
                                size(17f, 17f)
                                tintColor(c.labelSecondary)
                            }
                        }
                        event { click { this@DshBusHomePage.openSettings() } }
                    }
                }

                // 最近 / 按项目 + Agent 筛选
                View {
                    attr {
                        height(50f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(20f)
                        paddingRight(20f)
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            backgroundColor(c.specificSelector)
                            borderRadius(12f)
                            padding(3f)
                        }
                        View {
                            attr {
                                backgroundColor(
                                    if (this@DshBusHomePage.viewMode == VIEW_RECENT) c.bgBase
                                    else Color(0x00000000),
                                )
                                borderRadius(9f)
                                paddingLeft(16f)
                                paddingRight(16f)
                                paddingTop(6f)
                                paddingBottom(6f)
                            }
                            event { click { this@DshBusHomePage.selectViewMode(VIEW_RECENT) } }
                            Text {
                                attr {
                                    text("最近")
                                    fontSize(13f)
                                    if (this@DshBusHomePage.viewMode == VIEW_RECENT) {
                                        fontWeightBold()
                                        color(c.labelPrimary)
                                    } else {
                                        fontWeightMedium()
                                        color(c.labelSecondary)
                                    }
                                }
                            }
                        }
                        View {
                            attr {
                                backgroundColor(
                                    if (this@DshBusHomePage.viewMode == VIEW_PROJECTS) c.bgBase
                                    else Color(0x00000000),
                                )
                                borderRadius(9f)
                                paddingLeft(16f)
                                paddingRight(16f)
                                paddingTop(6f)
                                paddingBottom(6f)
                            }
                            event { click { this@DshBusHomePage.selectViewMode(VIEW_PROJECTS) } }
                            Text {
                                attr {
                                    text("按项目")
                                    fontSize(13f)
                                    if (this@DshBusHomePage.viewMode == VIEW_PROJECTS) {
                                        fontWeightBold()
                                        color(c.labelPrimary)
                                    } else {
                                        fontWeightMedium()
                                        color(c.labelSecondary)
                                    }
                                }
                            }
                        }
                    }
                    View { attr { flex(1f) } }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("menu.svg"))
                                size(12f, 12f)
                                tintColor(c.labelSecondary)
                            }
                        }
                        Text {
                            attr {
                                text(selectedAgentName)
                                fontSize(12f)
                                fontWeightMedium()
                                color(c.labelSecondary)
                                marginLeft(5f)
                            }
                            event { click { this@DshBusHomePage.openAgentSheet() } }
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("chevron-down.svg"))
                                size(10f, 10f)
                                tintColor(c.labelTertiary)
                                marginLeft(3f)
                            }
                            event { click { this@DshBusHomePage.openAgentSheet() } }
                        }
                        event { click { this@DshBusHomePage.openAgentSheet() } }
                    }
                }

                // 任务列表（BusHomeView）
                View {
                    attr {
                        flex(1f)
                        flexDirectionColumn()
                    }
                    vif({ visibleTasks.isEmpty() }) {
                        View {
                            attr {
                                flex(1f)
                                flexDirectionColumn()
                                alignItemsCenter()
                                justifyContentCenter()
                                paddingBottom(70f)
                            }
                            Image {
                                attr {
                                    src(
                                        ImageUri.commonAssets(
                                            if (c.isDark) "empty-no-tasks-dark.svg" else "empty-no-tasks.svg",
                                        ),
                                    )
                                    size(280f, 175f)
                                }
                            }
                            Text {
                                attr {
                                    text("暂无任务")
                                    fontSize(16f)
                                    fontWeightBold()
                                    color(c.labelSecondary)
                                    marginTop(26f)
                                    textAlignCenter()
                                }
                            }
                            Text {
                                attr {
                                    text("任务完成后\n回到这里查看")
                                    fontSize(13f)
                                    color(c.labelTertiary)
                                    lineHeight(21f)
                                    marginTop(8f)
                                    textAlignCenter()
                                }
                            }
                        }
                    }
                    vif({
                        visibleTasks.isNotEmpty() &&
                            this@DshBusHomePage.viewMode == VIEW_RECENT
                    }) {
                        Scroller {
                            attr { flex(1f) }
                            View {
                                attr {
                                    width(this@DshBusHomePage.pagerData.pageViewWidth)
                                    flexDirectionColumn()
                                }
                                visibleTasks.forEachIndexed { index, task ->
                                    val next = visibleTasks.getOrNull(index + 1)
                                    BusTaskRow(
                                        task = task,
                                        c = c,
                                        topRounded = index == 0,
                                        showDivider = next != null && !(task.pending && next.pending),
                                        onClick = { this@DshBusHomePage.openBusChat(task) },
                                    )
                                }
                            }
                        }
                    }
                    vif({
                        visibleTasks.isNotEmpty() &&
                            this@DshBusHomePage.viewMode == VIEW_PROJECTS
                    }) {
                        Scroller {
                            attr { flex(1f) }
                            View {
                                attr {
                                    width(this@DshBusHomePage.pagerData.pageViewWidth)
                                    flexDirectionColumn()
                                }
                                home.projectGroups(selectedKey).forEach { group ->
                                    val expanded = !this@DshBusHomePage.collapsedProjects.contains(group.key)
                                    BusProjectHeader(
                                        name = group.name,
                                        expanded = expanded,
                                        c = c,
                                    ) { this@DshBusHomePage.toggleProject(group.key) }
                                    vif({ !this@DshBusHomePage.collapsedProjects.contains(group.key) }) {
                                        group.tasks.forEachIndexed { index, task ->
                                            BusTaskRow(
                                                task = task,
                                                c = c,
                                                topRounded = false,
                                                showDivider = index < group.tasks.lastIndex,
                                                dense = true,
                                                onClick = { this@DshBusHomePage.openBusChat(task) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 新建任务 FAB（激活态，对齐 M2 首页 v8）。
                // 注意：FAB 必须始终参与布局，只切换透明度；
                // 若用 vif 整块移除，任务区 flex(1f) 会被拉高/压缩，
                // Agent 弹窗开合时背后的空态图标就会上下位移。
                View {
                    attr {
                        flexDirectionRow()
                        justifyContentFlexEnd()
                        paddingRight(20f)
                        paddingBottom(20f)
                        paddingTop(12f)
                    }
                    View {
                        attr {
                            size(52f, 52f)
                            borderRadius(17f)
                            backgroundLinearGradient(
                                Direction.TO_BOTTOM_RIGHT,
                                ColorStop(Color(0xFF3B6BFF), 0f),
                                ColorStop(Color(0xFF9B5CFF), 1f),
                            )
                            boxShadow(BoxShadow(0f, 4f, 12f, Color(0x593B6BFF)))
                            alignItemsCenter()
                            justifyContentCenter()
                            opacity(
                                if (
                                    this@DshBusHomePage.showAgentSheet ||
                                    this@DshBusHomePage.showDeviceSheet ||
                                    this@DshBusHomePage.showSettings ||
                                    this@DshBusHomePage.showThemePicker ||
                                    this@DshBusHomePage.showRenameComputer ||
                                    this@DshBusHomePage.showRemoveComputer ||
                                    this@DshBusHomePage.showAddComputer ||
                                    this@DshBusHomePage.showNewTaskSheet
                                ) 0f else 1f,
                            )
                        }
                        event { click { this@DshBusHomePage.openDraftChat() } }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("new-session.svg"))
                                size(23f, 23f)
                                tintColor(Color(0xFFFFFFFF))
                            }
                        }
                    }
                }

            }
            }
            vif({ this@DshBusHomePage.showAgentSheet }) {
                AgentFilterSheet(
                    agents = this@DshBusHomePage.homeView.agents,
                    selectedKey = this@DshBusHomePage.selectedAgentKey,
                    contentWidth = this@DshBusHomePage.pagerData.pageViewWidth - 40f,
                    c = this@DshBusHomePage.themeColors,
                    onSelect = { key ->
                        this@DshBusHomePage.selectedAgentKey = key
                        this@DshBusHomePage.showAgentSheet = false
                    },
                    onClose = { this@DshBusHomePage.showAgentSheet = false },
                )
            }
            vif({ this@DshBusHomePage.showDeviceSheet }) {
                BusDeviceSheet(
                    device = this@DshBusHomePage.homeView.device?.let { device ->
                        device.copy(name = this@DshBusHomePage.currentComputerName())
                    },
                    pendingCount = this@DshBusHomePage.homeView.pendingCount,
                    runningCount = this@DshBusHomePage.homeView.runningCount,
                    contentWidth = this@DshBusHomePage.pagerData.pageViewWidth - 40f,
                    colors = this@DshBusHomePage.themeColors,
                    onClose = { this@DshBusHomePage.showDeviceSheet = false },
                    onAddComputer = { this@DshBusHomePage.openAddComputer() },
                )
            }
            vif({ this@DshBusHomePage.showSettings }) {
                vbind({ this@DshBusHomePage.localHostName }) {
                this.BusSettingsOverlay(
                    computers = this@DshBusHomePage.settingsComputers(),
                    agents = this@DshBusHomePage.homeView.agents,
                    themeModeLabel = { dshThemeModeLabel(this@DshBusHomePage.themeMode) },
                    statusBarHeight = this@DshBusHomePage.pagerData.statusBarHeight,
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
        private const val VIEW_RECENT = "recent"
        private const val VIEW_PROJECTS = "projects"
        private const val BUS_VIEW_MODE_KEY = "bus_home_view_mode"
    }
}

/** vbind 绑定值：任一字段变化（含主题色）都会重建主页内容。 */
private data class BusHomeBindKey(
    val homeView: BusHomeView,
    val selectedAgentKey: String,
    val everLoaded: Boolean,
    val waitingTooLong: Boolean,
    val themeColors: DshColorTokens,
    val viewMode: String,
    val localHostName: String,
)

private fun ViewContainer<*, *>.BusProjectHeader(
    name: String,
    expanded: Boolean,
    c: DshColorTokens,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(44f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(20f)
            paddingRight(20f)
            backgroundColor(c.bgBase)
            borderBottom(Border(1f, BorderStyle.SOLID, c.borderL1))
        }
        event { click { onClick() } }
        Text {
            attr {
                text(name)
                fontSize(14f)
                fontWeightBold()
                color(c.labelPrimary)
                flex(1f)
            }
        }
        Image {
            attr {
                src(ImageUri.commonAssets("chevron-down.svg"))
                size(12f, 12f)
                tintColor(c.labelTertiary)
                transform(Rotate(if (expanded) 180f else 0f))
            }
        }
    }
}

private fun ViewContainer<*, *>.BusTaskRow(
    task: BusHomeTask,
    c: DshColorTokens,
    topRounded: Boolean,
    showDivider: Boolean,
    dense: Boolean = false,
    onClick: () -> Unit = {},
) {
    View {
        attr {
            flexDirectionRow()
            paddingLeft(20f)
            paddingRight(20f)
            paddingTop(if (dense) 9f else 13f)
            paddingBottom(if (dense) 9f else 13f)
            backgroundColor(if (task.pending) homePinned(c) else c.bgBase)
            if (topRounded) borderRadius(BorderRectRadius(14f, 14f, 0f, 0f))
        }
        event { click { onClick() } }
        View {
            attr {
                if (dense) {
                    size(36f, 36f)
                    borderRadius(18f)
                } else {
                    size(40f, 40f)
                    borderRadius(20f)
                }
                backgroundColor(homeSurface(c))
                alignItemsCenter()
                justifyContentCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(TaskIconRegistry.iconFor(task.taskType)))
                    size(if (dense) 20f else 22f, if (dense) 20f else 22f)
                    tintColor(c.labelSecondary)
                }
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                marginLeft(if (dense) 12f else 13f)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(task.title)
                        fontSize(15f)
                        fontWeightBold()
                        color(c.labelPrimary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text(task.time)
                        fontSize(11f)
                        color(c.labelTertiary)
                    }
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(5f)
                }
                StatusPill(task.statusLabel, c)
                Text {
                    attr {
                        text(task.agent)
                        fontSize(12f)
                        fontWeightMedium()
                        color(c.labelSecondary)
                        marginLeft(7f)
                    }
                }
                Text {
                    attr {
                        text("· ${task.project}")
                        fontSize(12f)
                        color(c.labelTertiary)
                        marginLeft(4f)
                        flex(1f)
                    }
                }
            }
        }
    }
    if (showDivider) {
        View {
            attr {
                height(1f)
                backgroundColor(c.borderL1)
                marginLeft(if (dense) 68f else 73f)
                marginRight(20f)
            }
        }
    }
}

private fun ViewContainer<*, *>.AgentFilterSheet(
    agents: List<BusHomeAgent>,
    selectedKey: String,
    contentWidth: Float,
    c: DshColorTokens,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    DshBottomSheet(
        colors = { c },
        onClose = onClose,
        initialDetent = DshSheetDetent.LARGE,
        panelHeight = 380f,
        panelBackground = { c.bgBase },
    ) {
        View {
            attr {
                height(42f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(20f)
                paddingRight(20f)
                marginTop(6f)
            }
            Text {
                attr {
                    text("选择 Agent")
                    fontSize(15f)
                    fontWeightBold()
                    color(c.labelPrimary)
                    flex(1f)
                }
            }
            View {
                attr {
                    size(30f, 30f)
                    borderRadius(15f)
                    backgroundColor(homeSurface(c))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { onClose() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("x.svg"))
                        size(13f, 13f)
                        tintColor(c.labelSecondary)
                    }
                }
            }
        }
        Scroller {
            attr {
                flex(1f)
                marginTop(6f)
                paddingLeft(20f)
                paddingRight(20f)
            }
            View {
                attr {
                    width(contentWidth)
                    flexDirectionColumn()
                }
                AgentFilterRow(
                    name = "全部 Agent",
                    selected = selectedKey.isEmpty(),
                    badge = if (selectedKey.isEmpty()) "当前" else "",
                    badgeKind = if (selectedKey.isEmpty()) "current" else "",
                    c = c,
                    onClick = { onSelect("") },
                )
                agents.forEach { agent ->
                    val selected = selectedKey == agent.key
                    val badge = when {
                        selected -> "当前"
                        !agent.available -> "未安装"
                        !agent.enabled -> "已停用"
                        agent.status == "degraded" -> "需确认"
                        agent.status == "error" -> "异常"
                        else -> ""
                    }
                    val kind = when {
                        selected -> "current"
                        !agent.available || !agent.enabled -> "muted"
                        agent.status == "degraded" || agent.status == "error" -> "attention"
                        else -> ""
                    }
                    AgentFilterRow(
                        name = agent.name,
                        selected = selected,
                        badge = badge,
                        badgeKind = kind,
                        c = c,
                        onClick = { onSelect(agent.key) },
                    )
                }
            }
        }
    }
}
private fun ViewContainer<*, *>.AgentFilterRow(
    name: String,
    selected: Boolean,
    badge: String,
    badgeKind: String,
    c: DshColorTokens,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(64f)
            flexDirectionRow()
            alignItemsCenter()
            justifyContentSpaceBetween()
            paddingLeft(16f)
            paddingRight(16f)
            marginBottom(10f)
            borderRadius(14f)
            backgroundColor(if (selected) agentSelectedBg(c) else c.bgBase)
            border(
                Border(
                    if (selected) 1.5f else 1f,
                    BorderStyle.SOLID,
                    if (selected) c.stateBusinessPrimary else c.borderL1,
                ),
            )
        }
        Text {
            attr {
                text(name)
                fontSize(14f)
                fontWeightBold()
                color(if (badgeKind == "muted") c.labelTertiary else c.labelPrimary)
                flex(1f)
            }
        }
        if (badge.isNotEmpty()) {
            val textColor = when (badgeKind) {
                "attention" -> if (c.isDark) Color(0xFFF0A35E) else Color(0xFFB54708)
                "current" -> c.labelPrimary
                else -> c.labelTertiary
            }
            val bgColor = when (badgeKind) {
                "attention" -> if (c.isDark) Color(0xFF3A2A18) else Color(0xFFFBF3E8)
                "current" -> c.specificSelector
                else -> homeSurface(c)
            }
            View {
                attr {
                    backgroundColor(bgColor)
                    borderRadius(9f)
                    paddingLeft(9f)
                    paddingRight(9f)
                    paddingTop(3f)
                    paddingBottom(3f)
                }
                Text {
                    attr {
                        text(badge)
                        fontSize(11f)
                        fontWeightBold()
                        color(textColor)
                    }
                }
            }
        }
        event { click { onClick() } }
    }
}

private fun ViewContainer<*, *>.StatusPill(status: String, c: DshColorTokens) {
    val textColor: Color
    val bgColor: Color
    when (status) {
        "待审批", "等待输入" -> {
            textColor = if (c.isDark) Color(0xFFF0A35E) else Color(0xFFB54708)
            bgColor = if (c.isDark) Color(0xFF3A2A18) else Color(0xFFFBF3E8)
        }
        "运行中" -> {
            textColor = if (c.isDark) Color(0xFF9AA7E0) else Color(0xFF6673AF)
            bgColor = homeSurface(c)
        }
        else -> {
            textColor = c.labelTertiary
            bgColor = homeSurface(c)
        }
    }
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(bgColor)
            borderRadius(9f)
            paddingLeft(8f)
            paddingRight(8f)
            paddingTop(3f)
            paddingBottom(3f)
        }
        View {
            attr {
                size(5f, 5f)
                borderRadius(2.5f)
                backgroundColor(textColor)
            }
        }
        Text {
            attr {
                text(status)
                fontSize(11f)
                fontWeightBold()
                color(textColor)
                marginLeft(4f)
            }
        }
    }
}

private fun homeSurface(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF2A2A31) else Color(0xFFF5F5F5)

private fun agentSelectedBg(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF24242B) else Color(0xFFFBFAFF)

private fun homePinned(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF2A2A31) else Color(0xFFECECEC)


