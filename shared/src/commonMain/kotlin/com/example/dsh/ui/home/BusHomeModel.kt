package com.example.dsh.ui.home

import com.example.dsh.bus.BusStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 首页页面模型：只保留 UI 需要的字段，页面不直接读 JSON。
 *
 * BusStore 负责协议校验和事件合并，这里负责把协议字段翻译成
 * 设备卡片、计数、任务行和项目分组。后续接真实快照时，
 * 页面代码不需要再改。
 */
internal data class BusHomeView(
    val device: BusHomeDevice?,
    val agents: List<BusHomeAgent>,
    val tasks: List<BusHomeTask>,
    val projects: List<BusHomeProject> = emptyList(),
) {
    val pendingCount: Int = tasks.count { it.pending }
    val runningCount: Int = tasks.count { it.status == "running" }
}

/**
 * 首页主区状态。
 * - 没有电脑：顶部显示禁用态电脑卡片，主区不再渲染空态插画；
 * - 有电脑但没有可见任务：唯一的“没有任务”空态；
 * - 其余情况渲染任务列表。
 */
internal enum class BusHomeBodyState { DisabledComputer, NoTasks, Content }

internal fun busHomeBodyState(hasDevice: Boolean, visibleTaskCount: Int): BusHomeBodyState = when {
    !hasDevice -> BusHomeBodyState.DisabledComputer
    visibleTaskCount == 0 -> BusHomeBodyState.NoTasks
    else -> BusHomeBodyState.Content
}

/** Agent 选择弹窗里没有可展示 Agent 时显示空态。 */
internal fun busHomeAgentsEmpty(agents: List<BusHomeAgent>): Boolean = agents.isEmpty()

internal data class BusHomeProject(
    val key: String,
    val name: String,
    val path: String,
    val lastUsedAt: String,
    val pinned: Boolean,
)

/** 按项目视图里的一个分组；tasks 已按待处理/更新时间排好序。 */
internal data class BusHomeProjectGroup(
    val key: String,
    val name: String,
    val tasks: List<BusHomeTask>,
    /** 真正可发 removeProject 的项目路径；派生/占位分组为空串。 */
    val path: String = "",
)

internal data class BusHomeModelOption(
    val id: String,
    val displayName: String,
    val efforts: List<String> = emptyList(),
    val defaultEffort: String = "",
)

internal data class BusHomeAgent(
    val key: String,
    val name: String,
    val available: Boolean,
    val enabled: Boolean,
    val status: String,
    val source: String = "",
    val connectorId: String? = null,
    val taskCount: Int = 0,
    val lastError: String = "",
    val models: List<BusHomeModelOption> = emptyList(),
    /** 协议 3.8 起：连接器声明能永久删除原生会话记录。 */
    val canDeleteTasks: Boolean = false,
    /** 协议 3.3 起：连接器声明这台电脑上有「自动批准」开关可用。 */
    val canAutoApprove: Boolean = false,
) {
    /**
     * 能不能在这个 Agent 上直接发起任务。wire 上没有这个字段，按页面既有判断派生：
     * 可用 + 已启用 + 来源是 acp 或 dsh（codex / claude 等只在电脑上启动）。
     */
    val canStartTask: Boolean
        get() = busAgentCanStartTask(available, enabled, source)
}

/** [BusHomeAgent.canStartTask] 的纯函数版本，页面与单测共用。 */
internal fun busAgentCanStartTask(available: Boolean, enabled: Boolean, source: String): Boolean =
    available && enabled && (source == "acp" || source == "dsh")

internal data class BusHomeDevice(
    val agentId: String,
    val name: String,
    val platform: String,
    val online: Boolean,
    val platformAsset: String,
    val lastSeenAt: String = "",
    val appVersion: String = "",
    /** 协议 3.8 起：这台电脑声明能把项目移出 BotBus 电脑端列表。 */
    val canRemoveProjects: Boolean = false,
    /** 协议 3.9 起：这台电脑接受 `restartConnector`（手机上的「重启」）。 */
    val canRestartConnectors: Boolean = false,
)

internal data class BusHomeTask(
    val id: String,
    val title: String,
    val status: String,
    val statusLabel: String,
    val agent: String,
    val agentKey: String,
    val project: String,
    val time: String,
    val taskType: String?,
    val pending: Boolean,
    val projectPath: String = "",
    val projectKey: String = "",
    val origin: String = "",
    val controllable: Boolean = false,
    val imported: Boolean = false,
    val source: String = "",
    val connectorId: String? = null,
    val updatedAt: String = "",
    val model: String = "",
    val effort: String = "",
    val lastMessage: String = "",
    /** 协议 2.14 起：`pendingRequest.id`；空串 = 这台电脑没在任务上挂请求。 */
    val requestId: String = "",
    /** `pendingRequest.kind`：command / fileChange / permission / input。 */
    val kind: String = "",
    /** `pendingRequest.summary`：一句话说清要批什么（命令、改动、提问）。 */
    val summary: String = "",
    /** `pendingRequest.detail`：可选的展开细节（长命令、diff 摘要等）。 */
    val detail: String = "",
    /** `pendingRequest.questions`：审批范围选项或带选项的提问；没有则为空。 */
    val questions: List<BusApprovalQuestion> = emptyList(),
) {
    /** 任务上是否带着一个等待回答/审批的挂起请求。 */
    val hasPendingRequest: Boolean
        get() = requestId.isNotBlank()
}

/**
 * `PendingRequest.questions` 的一项。协议里单选会整个省略 `multiSelect`，
 * 因此这里收敛成布尔值（缺省 = 单选）；`options` 是 label 到 description 的
 * 有序对，description 可能为空串，回答时只回 label。
 */
internal data class BusApprovalQuestion(
    val id: String,
    val question: String,
    val options: List<Pair<String, String>>,
    val multiSelect: Boolean,
)

internal fun BusStore.toHomeView(): BusHomeView {
    val agent = agents.firstOrNull()
    val deviceId = agent?.string("agentId")
    val connectors = agent?.objects("connectors").orEmpty()
    val agents = connectors.map { connector ->
        val kind = connector.string("kind").orEmpty()
        val connectorId = connector.string("connectorId")
        BusHomeAgent(
            key = connectorKey(kind, connectorId),
            name = connector.string("displayName").orEmpty()
                .ifBlank { agentLabel(kind, connectorId) },
            available = connector.string("available")?.toBooleanStrictOrNull() == true,
            enabled = connector.string("enabled")?.toBooleanStrictOrNull() != false,
            status = connector.string("status").orEmpty(),
            source = kind,
            connectorId = connectorId,
            taskCount = connector.string("taskCount")?.toIntOrNull() ?: 0,
            lastError = connector.string("lastError").orEmpty(),
            models = connector.objects("models").mapNotNull { model ->
                val id = model.string("id").orEmpty()
                if (id.isEmpty()) {
                    null
                } else {
                    BusHomeModelOption(
                        id = id,
                        displayName = model.string("displayName").orEmpty().ifBlank { id },
                        efforts = (model["efforts"] as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                            .orEmpty(),
                        defaultEffort = model.string("defaultEffort").orEmpty(),
                    )
                }
            },
            canDeleteTasks = connector.string("canDeleteTasks")
                ?.toBooleanStrictOrNull() == true,
            canAutoApprove = connector.string("canAutoApprove")
                ?.toBooleanStrictOrNull() == true,
        )
    }
    val projects = projects.mapNotNull { project ->
        val path = project.string("path").orEmpty()
        val name = project.string("name").orEmpty()
        val key = path.ifBlank { name }
        if (key.isBlank()) {
            null
        } else {
            BusHomeProject(
                key = key,
                name = name.ifBlank { path },
                path = path,
                lastUsedAt = project.string("lastUsedAt").orEmpty(),
                pinned = project.string("pinned")?.toBooleanStrictOrNull() == true,
            )
        }
    }
    val device = agent?.let {
        BusHomeDevice(
            agentId = deviceId.orEmpty(),
            name = it.string("name").orEmpty().ifBlank { "我的电脑" },
            platform = it.string("platform").orEmpty(),
            online = it.string("online")?.toBooleanStrictOrNull() == true,
            platformAsset = platformAsset(it.string("platform").orEmpty()),
            lastSeenAt = it.string("lastSeenAt").orEmpty(),
            appVersion = it.string("appVersion").orEmpty(),
            canRemoveProjects = it.string("canRemoveProjects")
                ?.toBooleanStrictOrNull() == true,
            canRestartConnectors = it.string("canRestartConnectors")
                ?.toBooleanStrictOrNull() == true,
        )
    }
    val tasks = tasks.asSequence()
        .filter { deviceId == null || it.string("agentId") == deviceId }
        .sortedWith(
            compareByDescending<JsonObject> { if (it.string("status").isPendingStatus()) 1 else 0 }
                .thenByDescending { it.string("updatedAt").orEmpty() },
        )
        .map { task ->
            val source = task.string("source").orEmpty()
            val connectorId = task.string("connectorId")
            val connector = connectors.firstOrNull { candidate ->
                if (source == "acp") {
                    candidate.string("kind") == "acp" &&
                        candidate.string("connectorId") == connectorId
                } else {
                    candidate.string("kind") == source
                }
            }
            val status = task.string("status").orEmpty()
            val projectPath = task.string("projectPath").orEmpty()
            val projectName = task.string("projectName").orEmpty()
            val outsideProject = task.string("outsideProject")?.toBooleanStrictOrNull() == true
            val projectKey = projectPath.ifBlank {
                projectName.ifBlank {
                    if (outsideProject) OUTSIDE_PROJECT_KEY else UNKNOWN_PROJECT_KEY
                }
            }
            val request = busPendingRequest(task)
            BusHomeTask(
                id = task.string("id").orEmpty(),
                title = task.string("title").orEmpty().ifBlank { "未命名会话" },
                status = status,
                statusLabel = taskStatusLabel(status),
                agent = connector?.string("displayName").orEmpty()
                    .ifBlank { agentLabel(source, connectorId) },
                agentKey = connectorKey(source, connectorId),
                project = projectLabel(task),
                time = displayTime(task.string("updatedAt").orEmpty()),
                taskType = task.string("taskType").orEmpty().ifBlank { null },
                pending = status.isPendingStatus(),
                projectPath = projectPath,
                projectKey = projectKey,
                origin = task.string("origin").orEmpty(),
                controllable = task.string("controllable")?.toBooleanStrictOrNull() == true,
                imported = task.string("id").orEmpty().startsWith(IMPORTED_TASK_PREFIX),
                source = source,
                connectorId = connectorId,
                updatedAt = task.string("updatedAt").orEmpty(),
                model = task.string("model").orEmpty(),
                effort = task.string("effort").orEmpty(),
                lastMessage = task.string("lastMessage").orEmpty(),
                requestId = request.string("id").orEmpty(),
                kind = request.string("kind").orEmpty(),
                summary = request.string("summary").orEmpty(),
                detail = request.string("detail").orEmpty(),
                questions = busPendingQuestions(request),
            )
        }
        .toList()
    return BusHomeView(device = device, agents = agents, tasks = tasks, projects = projects)
}

/** 任务上的 `pendingRequest`；缺失或形状不对时给空对象，读字段处不必再判空。 */
private fun busPendingRequest(task: JsonObject): JsonObject =
    task["pendingRequest"] as? JsonObject ?: JsonObject(emptyMap())

/**
 * `pendingRequest.questions`。协议里单选会整个省略 `multiSelect`（线格式是字面量 true），
 * 这里统一收敛成布尔值；一个可选项都没有的提问在审批卡上没法点选，直接丢掉。
 */
private fun busPendingQuestions(request: JsonObject): List<BusApprovalQuestion> =
    request.objects("questions").mapNotNull { question ->
        val id = question.string("id").orEmpty()
        val options = question.objects("options").mapNotNull { option ->
            val label = option.string("label").orEmpty()
            if (label.isBlank()) null else label to option.string("description").orEmpty()
        }
        if (id.isBlank() || options.isEmpty()) {
            null
        } else {
            BusApprovalQuestion(
                id = id,
                question = question.string("question").orEmpty(),
                options = options,
                multiSelect = question.string("multiSelect")?.toBooleanStrictOrNull() == true,
            )
        }
    }

/** 单选筛选（旧页面还在用）：空串 = 不过滤。 */
internal fun List<BusHomeTask>.forAgent(agentKey: String): List<BusHomeTask> =
    if (agentKey.isEmpty()) this else filter { it.agentKey == agentKey }

/** Agent 面板的多选筛选：空集合 = 不过滤（等价「全部 Agent」）。 */
internal fun List<BusHomeTask>.forAgents(agentKeys: Set<String>): List<BusHomeTask> =
    if (agentKeys.isEmpty()) this else filter { it.agentKey in agentKeys }

/** 单选 key 转多选集合：空串（全部）转成空集合。 */
internal fun busAgentFilterSet(agentKey: String): Set<String> =
    if (agentKey.isEmpty()) emptySet() else setOf(agentKey)

/** 多选面板点一下：已选则取消，未选则加入；空 key（全部）忽略。 */
internal fun busToggleAgentFilter(selected: Set<String>, agentKey: String): Set<String> = when {
    agentKey.isEmpty() -> emptySet()
    agentKey in selected -> selected - agentKey
    else -> selected + agentKey
}

/** 列表页顶部的可见任务数：多选语义，空集合 = 全部。 */
internal fun BusHomeView.visibleTaskCount(agentKeys: Set<String>): Int =
    tasks.forAgents(agentKeys).size

/** 只有这三类挂起请求走「批准 / 拒绝」；其余（`input`、旧数据）只能回复。 */
internal val busApprovalDecisionKinds = setOf("command", "fileChange", "permission")

/**
 * 审批队列的一张卡片（给审批面板用的投影契约）。
 *
 * `key` = `"taskId|requestId"`：同一条挂起请求刷新多次也只占一行；
 * `replyOnly` = true 时不渲染「批准 / 拒绝」，只渲染回复入口；
 * `questions` 非空时，「批准」要把点选结果按 questionId -> label 列表回给 `approve.answers`；
 * `summary` 是协议里的一句话标题，命令块要显示的是 `detail`（命令原文 / diff 摘要），
 * 电脑端没带 `detail` 的旧数据才退回 `summary`
 */
internal data class BusApprovalItem(
    val key: String,
    val taskId: String,
    val requestId: String,
    val kind: String,
    val title: String,
    val project: String,
    val agentName: String,
    val time: String,
    val summary: String,
    val detail: String = "",
    val questions: List<BusApprovalQuestion>,
    val replyOnly: Boolean,
)

/**
 * 审批投影：一条挂起请求一张卡片。
 *
 * `replyOnly` 只给「批准 / 拒绝」三类请求为 false；`input`（等回答）以及
 * 电脑没带 `pendingRequest` 的旧数据只能回复，不发起 `approve` 决定。
 * 顺序沿用 [toHomeView] 的「待处理优先 + 更新时间倒序」，同一请求只出一条。
 */
internal fun BusHomeView.approvalItems(agentKeys: Set<String>): List<BusApprovalItem> =
    tasks.forAgents(agentKeys)
        .filter { it.pending }
        .map { task ->
            BusApprovalItem(
                key = "${task.id}|${task.requestId}",
                taskId = task.id,
                requestId = task.requestId,
                kind = task.kind,
                title = task.title,
                project = task.project,
                agentName = task.agent,
                time = task.time,
                summary = task.summary.ifBlank { task.lastMessage },
                detail = task.detail,
                questions = task.questions,
                replyOnly = task.kind !in busApprovalDecisionKinds,
            )
        }
        .distinctBy { it.key }

/**
 * 按项目分组（多选）：与 [projectGroups] 单选版同一套排序规则，
 * 空集合 = 全部 Agent。
 */
internal fun BusHomeView.projectGroups(agentKeys: Set<String>): List<BusHomeProjectGroup> {
    val filtered = tasks.forAgents(agentKeys)
    if (filtered.isEmpty()) return emptyList()
    val grouped = filtered.groupBy { it.projectKey.ifBlank { UNKNOWN_PROJECT_KEY } }
    val projectKeys = projects.map { it.key }.toSet()
    val groups = mutableListOf<BusHomeProjectGroup>()
    projects.sortedWith(
        compareByDescending<BusHomeProject> { it.pinned }
            .thenByDescending { it.lastUsedAt },
    ).forEach { project ->
        grouped[project.key]?.let { list ->
            groups += BusHomeProjectGroup(
                key = project.key,
                name = project.name.ifBlank { project.path },
                tasks = list,
                path = project.path,
            )
        }
    }
    grouped.filterKeys { it !in projectKeys }
        .toList()
        .sortedByDescending { (_, list) -> list.firstOrNull()?.updatedAt.orEmpty() }
        .forEach { (key, list) ->
            val name = when (key) {
                OUTSIDE_PROJECT_KEY -> "不在项目中"
                UNKNOWN_PROJECT_KEY -> "未命名项目"
                else -> list.firstOrNull()?.project?.takeIf { it.isNotBlank() } ?: key
            }
            // 只由任务派生的项目 key 就是 projectPath；占位分组不可移除。
            val path = when (key) {
                OUTSIDE_PROJECT_KEY, UNKNOWN_PROJECT_KEY -> ""
                else -> list.firstOrNull()?.projectPath.orEmpty()
            }
            groups += BusHomeProjectGroup(key, name, list, path)
        }
    return groups
}

/**
 * 按项目分组（单选，旧页面还在用）：空串 = 不过滤。
 * - 已登记项目按 pinned、lastUsedAt 排序；
 * - 只在任务里出现的项目按最近更新时间排序；
 * - “不在项目中/未命名项目”放最后。
 */
internal fun BusHomeView.projectGroups(agentKey: String): List<BusHomeProjectGroup> =
    projectGroups(busAgentFilterSet(agentKey))

/**
 * 项目视图的扁平行：`vforLazy` 只能吃一维列表，所以把「项目头 + 展开的任务」
 * 摊平后交给懒加载列表；折叠状态变化时重新生成即可。
 */
internal sealed class BusHomeProjectRow {
    abstract val key: String

    data class Header(val group: BusHomeProjectGroup) : BusHomeProjectRow() {
        override val key: String = "project-${group.key}"
    }

    data class Task(val task: BusHomeTask, val showDivider: Boolean) : BusHomeProjectRow() {
        override val key: String = "task-${task.id}"
    }
}

internal fun busHomeProjectRows(
    groups: List<BusHomeProjectGroup>,
    collapsed: Set<String>,
): List<BusHomeProjectRow> {
    val rows = ArrayList<BusHomeProjectRow>(groups.size * 2)
    for (group in groups) {
        rows += BusHomeProjectRow.Header(group)
        if (group.key in collapsed) continue
        group.tasks.forEachIndexed { index, task ->
            rows += BusHomeProjectRow.Task(task, showDivider = index < group.tasks.lastIndex)
        }
    }
    return rows
}

private const val IMPORTED_TASK_PREFIX = "import:"
private const val OUTSIDE_PROJECT_KEY = "outside:"
private const val UNKNOWN_PROJECT_KEY = "unknown:"

private fun connectorKey(source: String, connectorId: String?): String =
    if (source == "acp") "acp:${connectorId.orEmpty()}" else source

private fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun String?.isPendingStatus(): Boolean =
    this == "waitingApproval" || this == "waitingInput"

internal fun taskStatusLabel(status: String): String = when (status) {
    "running" -> "运行中"
    "waitingApproval" -> "待审批"
    "waitingInput" -> "等待输入"
    "completed" -> "已完成"
    "failed" -> "失败"
    "interrupted" -> "已中断"
    "idle" -> "空闲"
    else -> "未知"
}

internal fun platformAsset(platform: String): String = when (platform.lowercase()) {
    "macos", "mac", "darwin" -> "platform-apple.svg"
    "windows", "win32" -> "platform-windows.svg"
    "linux" -> "platform-linux.svg"
    "harmonyos", "ohos" -> "platform-harmonyos.svg"
    else -> "computer.svg"
}

private fun agentLabel(source: String, connectorId: String?): String = when (source) {
    "acp" -> connectorId ?: "ACP"
    "codex" -> "Codex"
    "claude" -> "Claude Code"
    "dsh" -> "DeepSeek Harness"
    "hermes" -> "Hermes"
    "pi" -> "Pi"
    "openclaw" -> "OpenClaw"
    else -> source.ifBlank { "Agent" }
}

/** 优先显示项目名；不在项目里的会话给一个明确占位。 */
private fun projectLabel(task: JsonObject): String {
    val name = task.string("projectName").orEmpty().ifBlank {
        task.string("projectPath").orEmpty().substringAfterLast('/')
    }
    if (name.isNotBlank()) return name
    return if (task.string("outsideProject")?.toBooleanStrictOrNull() == true) "不在项目中" else "未命名项目"
}

/** 协议时间是 ISO-8601；先做足够稳定的小展示，后面再换本地相对时间。 */
internal fun displayTime(iso: String): String {
    if (iso.length < 16 || iso[10] != 'T') return iso.ifBlank { "—" }
    return "${iso.substring(5, 10)} ${iso.substring(11, 16)}"
}
