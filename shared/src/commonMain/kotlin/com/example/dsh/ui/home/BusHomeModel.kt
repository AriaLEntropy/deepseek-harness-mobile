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
)

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
            )
        }
        .toList()
    return BusHomeView(device = device, agents = agents, tasks = tasks, projects = projects)
}

internal fun List<BusHomeTask>.forAgent(agentKey: String): List<BusHomeTask> =
    if (agentKey.isEmpty()) this else filter { it.agentKey == agentKey }

/**
 * 按项目分组：
 * - 已登记项目按 pinned、lastUsedAt 排序；
 * - 只在任务里出现的项目按最近更新时间排序；
 * - “不在项目中/未命名项目”放最后。
 */
internal fun BusHomeView.projectGroups(agentKey: String): List<BusHomeProjectGroup> {
    val filtered = tasks.forAgent(agentKey)
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
