package com.example.dsh.ui.chat

import com.example.dsh.bus.BusCommand
import com.example.dsh.bus.BusCommandAck
import com.example.dsh.bus.BusCommandSender
import com.example.dsh.bus.BusCommandTransport
import com.example.dsh.bus.BusCommands
import com.example.dsh.bus.BusSendResult
import com.example.dsh.bus.BusStore
import com.example.dsh.bus.busParse
import com.example.dsh.bus.busSkillListKey
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.ui.home.BusApprovalQuestion
import com.example.dsh.ui.home.BusHomeAgent
import com.example.dsh.ui.home.BusHomeModelOption
import com.example.dsh.ui.home.busAgentAsset
import com.example.dsh.ui.home.BusHomeView
import com.example.dsh.ui.home.toHomeView
import com.tencent.kuikly.core.timer.setTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal data class BusChatHistory(
    val messages: List<DshMessage>,
    val hasMore: Boolean,
)

internal data class BusChatStartRequest(
    val agentId: String,
    val source: String,
    val connectorId: String?,
    val projectPath: String,
    val prompt: String,
    val model: String,
    val effort: String,
    /** 协议 3.3：随首轮 startTask 发出；null = 不动电脑上的项目设置 */
    val autoApprove: Boolean? = null,
)

/** 一条挂起的审批/提问：标题、细节与命令 id 都从任务快照投影 */
internal data class BusChatPendingRequest(
    val requestId: String,
    val kind: String,
    val summary: String,
    val detail: String,
    val questions: List<BusApprovalQuestion> = emptyList(),
)

/** 任务级产物（协议 2.3）；快照没给时为空 */
internal data class BusChatArtifact(
    val id: String,
    val kind: String,
    val title: String,
    val size: Long,
    val contentType: String,
    val url: String,
)

/** 技能列表的一行：id 由电脑生成，name 是插入草稿的斜杠命令 */
internal data class BusChatSkill(
    val id: String,
    val name: String,
    val description: String,
    val hint: String = "",
    val kind: String = "skill",
    val source: String = "",
    val hasBody: Boolean = false,
)

/** readSkill 的正文 */
internal data class BusChatSkillContent(
    val skillId: String,
    val name: String,
    val body: String,
    val truncated: Boolean,
)

/** 草稿态可选的 Agent：名称、来源、头像与模型清单 */
internal data class BusChatAgentOption(
    val key: String,
    val name: String,
    val source: String,
    val connectorId: String?,
    val avatarAsset: String,
    val subtitle: String,
    val models: List<BusHomeModelOption>,
    val canAutoApprove: Boolean,
    val canListSkills: Boolean = false,
)

/** 可切换的工作区（项目） */
internal data class BusChatWorkspace(
    val path: String,
    val name: String,
)

internal data class BusChatTaskInfo(
    val title: String,
    val status: String,
    val statusLabel: String,
    val agentName: String,
    val model: String,
    val effort: String,
    val controllable: Boolean,
    val projectName: String,
    val lastMessage: String,
    /** 协议 3.8：对应连接器声明能永久删除会话记录。 */
    val canDeleteTasks: Boolean = false,
    /** 协议 3.3：项目当前是否开着自动批准 */
    val autoApprove: Boolean = false,
    /** 协议 3.3：连接器声明支持自动批准 */
    val canAutoApprove: Boolean = false,
    /** 挂着的审批/提问；空串表示没有 */
    val request: BusChatPendingRequest? = null,
)

/**
 * 对话页依赖的最小接口。页面只认这一层，不认 Bus / DSH / 传输方式；
 * 接旧 DSH 会话时实现同一接口，UI 不用改。
 */
internal interface ConversationSource {
    fun taskInfo(taskId: String): BusChatTaskInfo?
    fun models(agentKey: String): List<BusHomeModelOption>
    fun agents(): List<BusChatAgentOption>
    /** null = 还没收到这台电脑的 skillList；空列表 = 电脑明确返回没有技能 */
    fun skills(agentKey: String, projectPath: String, taskId: String): List<BusChatSkill>?
    fun refreshSkills(
        agentKey: String,
        projectPath: String,
        taskId: String,
        onDone: (ok: Boolean, error: String) -> Unit,
    )
    fun readSkill(
        agentKey: String,
        projectPath: String,
        taskId: String,
        skillId: String,
        onDone: (content: BusChatSkillContent?, error: String?) -> Unit,
    )
    fun workspaces(): List<BusChatWorkspace>
    fun artifacts(taskId: String): List<BusChatArtifact>
    fun history(taskId: String): BusChatHistory?
    fun fetchMessages(agentId: String, taskId: String)
    fun startTask(request: BusChatStartRequest, onDone: (taskId: String?, error: String?) -> Unit)
    fun followUp(
        agentId: String,
        taskId: String,
        prompt: String,
        model: String,
        effort: String,
        autoApprove: Boolean?,
        onDone: (ok: Boolean, error: String) -> Unit,
    )
    fun interrupt(agentId: String, taskId: String, onDone: (ok: Boolean, error: String) -> Unit)

    /** 协议 2.14：批准或拒绝一条挂起请求，可带选项答案 */
    fun approve(
        agentId: String,
        taskId: String,
        requestId: String,
        allow: Boolean,
        answers: Map<String, List<String>>,
        onDone: (ok: Boolean, error: String) -> Unit,
    )

    /** 协议 3.8：永久删除一条已停止的会话；成功后电脑发 taskRemoved。 */
    fun deleteTask(agentId: String, taskId: String, onDone: (ok: Boolean, error: String) -> Unit)
}

/**
 * Bus 对话的数据/命令适配层。
 *
 * 页面只依赖这个类暴露的「历史 / 任务 / 命令」能力，不直接碰
 * `/bus/commands`、快照 JSON 或 DshRelayModule；之后接旧 DSH 数据源时
 * 可以再实现一个同形状的 source，而不改对话 UI。
 */
internal class BusChatSource(
    private val relay: DshRelayModule,
    private val pagerId: String,
) : ConversationSource {
    private val commandSender = BusCommandSender(RelayCommandTransport(relay, pagerId))

    /**
     * 上一次发送没有拿到电脑确认（网络失败 / 60 秒未确认）的命令。
     * 同一操作再次点击时原样复用，保证 id 与 createdAt 不变、电脑按 id 去重。
     */
    private val retryCommands = mutableMapOf<String, BusCommand>()

    fun homeView(): BusHomeView? = runCatching {
        val json = relay.busHomeJson() ?: return null
        val store = BusStore()
        if (store.applySnapshot(busParse(json)) == null) return null
        store.toHomeView()
    }.getOrNull()

    override fun taskInfo(taskId: String): BusChatTaskInfo? {
        val view = homeView() ?: return null
        val task = view.tasks.firstOrNull { it.id == taskId } ?: return null
        val connector = view.agents.firstOrNull { it.key == task.agentKey }
        return BusChatTaskInfo(
            title = task.title,
            status = task.status,
            statusLabel = task.statusLabel,
            agentName = task.agent,
            model = task.model,
            effort = task.effort,
            controllable = task.controllable,
            projectName = task.project,
            lastMessage = task.lastMessage,
            canDeleteTasks = connector?.canDeleteTasks == true,
            autoApprove = task.autoApprove,
            canAutoApprove = connector?.canAutoApprove == true,
            request = task.requestId.takeIf { it.isNotBlank() }?.let {
                BusChatPendingRequest(
                    requestId = task.requestId,
                    kind = task.kind,
                    summary = task.summary,
                    detail = task.detail,
                    questions = task.questions,
                )
            },
        )
    }

    override fun models(agentKey: String): List<BusHomeModelOption> =
        homeView()?.agents?.firstOrNull { it.key == agentKey }?.models.orEmpty()

    override fun agents(): List<BusChatAgentOption> =
        homeView()?.agents.orEmpty()
            .filter { it.canStartTask }
            .map { agent ->
                BusChatAgentOption(
                    key = agent.key,
                    name = agent.name,
                    source = agent.source,
                    connectorId = agent.connectorId,
                    avatarAsset = busAgentAsset(
                        source = agent.source,
                        connectorId = agent.connectorId,
                        agentKey = agent.key,
                        agentName = agent.name,
                    ),
                    subtitle = busChatAgentSubtitle(agent.source),
                    models = agent.models,
                    canAutoApprove = agent.canAutoApprove,
                    canListSkills = agent.canListSkills,
                )
            }

    private fun agentFor(agentKey: String): BusHomeAgent? =
        homeView()?.agents?.firstOrNull { it.key == agentKey }

    override fun skills(
        agentKey: String,
        projectPath: String,
        taskId: String,
    ): List<BusChatSkill>? {
        val agent = agentFor(agentKey) ?: return emptyList()
        if (!agent.canListSkills) return emptyList()
        val key = busSkillListKey(agent.source, agent.connectorId, projectPath, taskId)
        val raw = runCatching { relay.busSkillList(key) }.getOrNull() ?: return null
        val root = runCatching { busParse(raw).jsonObject }.getOrNull() ?: return null
        val items = root["skills"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id")
            val name = obj.string("name")
            if (id.isBlank() || name.isBlank()) {
                null
            } else {
                BusChatSkill(
                    id = id,
                    name = name,
                    description = obj.string("description"),
                    hint = obj.string("hint"),
                    kind = obj.string("kind").ifBlank { "skill" },
                    source = obj.string("source"),
                    hasBody = obj.string("hasBody").toBooleanStrictOrNull() == true,
                )
            }
        }
    }

    override fun refreshSkills(
        agentKey: String,
        projectPath: String,
        taskId: String,
        onDone: (ok: Boolean, error: String) -> Unit,
    ) {
        val agent = agentFor(agentKey)
        if (agent == null) {
            onDone(false, "这个 Agent 不可用")
            return
        }
        if (!agent.canListSkills) {
            onDone(false, "这个 Agent 不支持技能")
            return
        }
        val hostAgentId = homeView()?.device?.agentId.orEmpty()
        if (hostAgentId.isBlank()) {
            onDone(false, "电脑未连接")
            return
        }
        sendCommand(
            retryKey = listOf("fetchSkills", agent.key, projectPath, taskId).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.fetchSkills(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = hostAgentId,
                    connector = agent.source,
                    connectorId = agent.connectorId,
                    projectPath = projectPath,
                    taskId = taskId,
                )
            },
        ) { result ->
            when (result) {
                is BusSendResult.Confirmed -> onDone(result.ok, result.error)
                is BusSendResult.Failed -> onDone(false, operationFailure(result, "技能读取失败"))
                else -> onDone(false, result.message)
            }
        }
    }

    override fun readSkill(
        agentKey: String,
        projectPath: String,
        taskId: String,
        skillId: String,
        onDone: (content: BusChatSkillContent?, error: String?) -> Unit,
    ) {
        val agent = agentFor(agentKey)
        if (agent == null) {
            onDone(null, "这个 Agent 不可用")
            return
        }
        if (!agent.canListSkills) {
            onDone(null, "这个 Agent 不支持技能")
            return
        }
        val hostAgentId = homeView()?.device?.agentId.orEmpty()
        if (hostAgentId.isBlank()) {
            onDone(null, "电脑未连接")
            return
        }
        sendCommand(
            retryKey = listOf("readSkill", agent.key, projectPath, taskId, skillId).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.readSkill(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = hostAgentId,
                    connector = agent.source,
                    connectorId = agent.connectorId,
                    projectPath = projectPath,
                    taskId = taskId,
                    skillId = skillId,
                )
            },
        ) { result ->
            when (result) {
                is BusSendResult.Confirmed -> {
                    if (result.ok) {
                        pollSkillContent(skillId, 0, onDone)
                    } else {
                        onDone(null, result.error.ifBlank { "读取失败" })
                    }
                }
                is BusSendResult.Failed -> onDone(null, operationFailure(result, "读取失败"))
                else -> onDone(null, result.message)
            }
        }
    }

    private fun pollSkillContent(
        skillId: String,
        attempt: Int,
        onDone: (content: BusChatSkillContent?, error: String?) -> Unit,
    ) {
        val raw = runCatching { relay.busSkillContent(skillId) }.getOrNull()
        val root = raw?.let { runCatching { busParse(it).jsonObject }.getOrNull() }
        if (root != null) {
            onDone(
                BusChatSkillContent(
                    skillId = root.string("skillId"),
                    name = root.string("name"),
                    body = root.string("body"),
                    truncated = root.string("truncated").toBooleanStrictOrNull() == true,
                ),
                null,
            )
            return
        }
        if (attempt >= 20) {
            onDone(null, "电脑没有返回技能正文")
            return
        }
        setTimeout(pagerId, 250) { pollSkillContent(skillId, attempt + 1, onDone) }
    }

    override fun workspaces(): List<BusChatWorkspace> =
        homeView()?.projects.orEmpty().map { BusChatWorkspace(path = it.path, name = it.name) }

    override fun artifacts(taskId: String): List<BusChatArtifact> {
        val raw = runCatching { relay.busHomeJson() }.getOrNull() ?: return emptyList()
        val root = runCatching { busParse(raw).jsonObject }.getOrNull() ?: return emptyList()
        val task = (root["tasks"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.string("id") == taskId }
            ?: return emptyList()
        val artifacts = task["artifacts"] as? JsonArray ?: return emptyList()
        return artifacts.mapNotNull { item ->
            val objectItem = item as? JsonObject ?: return@mapNotNull null
            val id = objectItem.string("id")
            if (id.isEmpty()) return@mapNotNull null
            BusChatArtifact(
                id = id,
                kind = objectItem.string("kind"),
                title = objectItem.string("title"),
                size = objectItem.string("size").toLongOrNull() ?: 0L,
                contentType = objectItem.string("contentType"),
                url = objectItem.string("url"),
            )
        }
    }

    override fun history(taskId: String): BusChatHistory? {
        val raw = relay.busTaskMessages(taskId) ?: return null
        val root = runCatching { busParse(raw).jsonObject }.getOrNull() ?: return null
        val messages = (root["messages"] as? JsonArray)?.mapNotNull { item ->
            val objectItem = item as? JsonObject ?: return@mapNotNull null
            val id = objectItem.string("id")
            if (id.isBlank()) return@mapNotNull null
            val role = objectItem.string("role")
            DshMessage(
                id = id,
                role = when (role) {
                    "user" -> DshMessageRole.USER
                    "tool" -> DshMessageRole.TOOL
                    else -> DshMessageRole.ASSISTANT
                },
                content = objectItem.string("text"),
                toolName = if (role == "tool") "tool" else null,
            )
        } ?: return null
        return BusChatHistory(
            messages = messages,
            hasMore = (root["hasMore"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    override fun fetchMessages(agentId: String, taskId: String) {
        if (taskId.isBlank()) return
        val commandId = BusCommands.newCommandId()
        val json = BusCommands.fetchMessages(
            commandId = commandId,
            createdAt = BusCommands.createdAt(),
            agentId = agentId,
            taskId = taskId,
        )
        relay.busCommand(json) { }
    }

    override fun startTask(request: BusChatStartRequest, onDone: (taskId: String?, error: String?) -> Unit) {
        sendCommand(
            retryKey = listOf(
                "startTask",
                request.agentId,
                request.source,
                request.connectorId.orEmpty(),
                request.projectPath,
                request.prompt,
                request.model,
                request.effort,
                request.autoApprove?.toString().orEmpty(),
            ).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.startTask(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = request.agentId,
                    source = request.source,
                    connectorId = request.connectorId,
                    projectPath = request.projectPath,
                    prompt = request.prompt,
                    model = request.model,
                    effort = request.effort,
                    autoApprove = request.autoApprove,
                )
            },
        ) { result ->
            when (result) {
                is BusSendResult.Confirmed -> when {
                    !result.ok -> onDone(null, result.error.ifBlank { "创建失败" })
                    result.taskId.isBlank() -> onDone(null, "电脑没有返回任务 id")
                    else -> onDone(result.taskId, null)
                }
                is BusSendResult.Unconfirmed -> onDone(null, "电脑没有确认，请回首页查看任务")
                else -> onDone(null, result.message)
            }
        }
    }

    override fun followUp(
        agentId: String,
        taskId: String,
        prompt: String,
        model: String,
        effort: String,
        autoApprove: Boolean?,
        onDone: (ok: Boolean, error: String) -> Unit,
    ) {
        sendCommand(
            retryKey = listOf(
                "followUp",
                agentId,
                taskId,
                prompt,
                model,
                effort,
                autoApprove?.toString().orEmpty(),
            ).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.followUp(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = agentId,
                    taskId = taskId,
                    prompt = prompt,
                    model = model,
                    effort = effort,
                    autoApprove = autoApprove,
                )
            },
        ) { result ->
            when (result) {
                is BusSendResult.Confirmed -> onDone(result.ok, result.error)
                is BusSendResult.Failed -> onDone(false, operationFailure(result, "发送失败"))
                else -> onDone(false, result.message)
            }
        }
    }

    override fun approve(
        agentId: String,
        taskId: String,
        requestId: String,
        allow: Boolean,
        answers: Map<String, List<String>>,
        onDone: (ok: Boolean, error: String) -> Unit,
    ) {
        sendCommand(
            retryKey = listOf(
                "approve",
                agentId,
                taskId,
                requestId,
                allow.toString(),
                answers.entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" },
            ).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.approve(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = agentId,
                    taskId = taskId,
                    requestId = requestId,
                    allow = allow,
                    answers = answers,
                )
            },
        ) { result ->
            when (result) {
                is BusSendResult.Confirmed -> onDone(result.ok, result.error)
                is BusSendResult.Failed -> onDone(false, operationFailure(result, "发送失败"))
                else -> onDone(false, result.message)
            }
        }
    }

    override fun interrupt(agentId: String, taskId: String, onDone: (ok: Boolean, error: String) -> Unit) {
        sendCommand(
            retryKey = listOf("interrupt", agentId, taskId).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.interrupt(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = agentId,
                    taskId = taskId,
                )
            },
        ) { result ->
            when (result) {
                is BusSendResult.Confirmed -> onDone(result.ok, result.error)
                is BusSendResult.Failed -> onDone(false, operationFailure(result, "停止失败"))
                else -> onDone(false, result.message)
            }
        }
    }

    override fun deleteTask(agentId: String, taskId: String, onDone: (ok: Boolean, error: String) -> Unit) {
        if (taskId.isBlank()) {
            onDone(false, "缺少任务 id")
            return
        }
        sendCommand(
            retryKey = listOf("deleteTask", agentId, taskId).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.deleteTask(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = agentId,
                    taskId = taskId,
                )
            },
        ) { result ->
            when (result) {
                is BusSendResult.Confirmed -> {
                    if (result.ok) onDone(true, "") else onDone(false, result.error.ifBlank { "删除失败" })
                }
                is BusSendResult.Failed -> onDone(false, operationFailure(result, "删除失败"))
                else -> onDone(false, result.message)
            }
        }
    }

    /**
     * 统一发送入口：先取该操作上一次可重试的命令（复用 id + createdAt），
     * 否则生成一次；结果可重试时留到下一次点击继续复用。
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

    private fun operationFailure(result: BusSendResult.Failed, fallback: String): String = when {
        result.body.isNotBlank() -> result.body
        result.status == 0 -> result.message
        else -> "$fallback（${result.status}）"
    }
}

/** 把 Kuikly relay 适配成与 UI/平台无关的发送边界，便于 commonTest 用假 transport 覆盖。 */
private class RelayCommandTransport(
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

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
