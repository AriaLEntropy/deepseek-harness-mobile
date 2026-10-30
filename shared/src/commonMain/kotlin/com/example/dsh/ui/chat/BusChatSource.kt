package com.example.dsh.ui.chat

import com.example.dsh.bus.BusCommand
import com.example.dsh.bus.BusCommandAck
import com.example.dsh.bus.BusCommandSender
import com.example.dsh.bus.BusCommandTransport
import com.example.dsh.bus.BusCommands
import com.example.dsh.bus.BusSendResult
import com.example.dsh.bus.BusStore
import com.example.dsh.bus.busParse
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.ui.home.BusHomeModelOption
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
)

/**
 * 对话页依赖的最小接口。页面只认这一层，不认 Bus / DSH / 传输方式；
 * 接旧 DSH 会话时实现同一接口，UI 不用改。
 */
internal interface ConversationSource {
    fun taskInfo(taskId: String): BusChatTaskInfo?
    fun models(agentKey: String): List<BusHomeModelOption>
    fun history(taskId: String): BusChatHistory?
    fun fetchMessages(agentId: String, taskId: String)
    fun startTask(request: BusChatStartRequest, onDone: (taskId: String?, error: String?) -> Unit)
    fun followUp(
        agentId: String,
        taskId: String,
        prompt: String,
        model: String,
        effort: String,
        onDone: (ok: Boolean, error: String) -> Unit,
    )
    fun interrupt(agentId: String, taskId: String, onDone: (ok: Boolean, error: String) -> Unit)

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
        )
    }

    override fun models(agentKey: String): List<BusHomeModelOption> =
        homeView()?.agents?.firstOrNull { it.key == agentKey }?.models.orEmpty()

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
        onDone: (ok: Boolean, error: String) -> Unit,
    ) {
        sendCommand(
            retryKey = listOf("followUp", agentId, taskId, prompt, model, effort).joinToString("|"),
            build = { id, createdAt ->
                BusCommands.followUp(
                    commandId = id,
                    createdAt = createdAt,
                    agentId = agentId,
                    taskId = taskId,
                    prompt = prompt,
                    model = model,
                    effort = effort,
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
