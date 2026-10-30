package com.example.dsh.ui.chat

import com.example.dsh.bus.BusCommands
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
        val commandId = BusCommands.newCommandId()
        val json = BusCommands.startTask(
            commandId = commandId,
            createdAt = BusCommands.createdAt(),
            agentId = request.agentId,
            source = request.source,
            connectorId = request.connectorId,
            projectPath = request.projectPath,
            prompt = request.prompt,
            model = request.model,
            effort = request.effort,
        )
        relay.busCommand(json) { ack ->
            if (ack.status != 200 && ack.status != 202) {
                onDone(null, if (ack.body.isBlank()) "创建失败（${ack.status}）" else "创建失败：${ack.body}")
                return@busCommand
            }
            awaitCommand(commandId) { outcome ->
                when {
                    outcome == null -> onDone(null, "电脑没有确认，请回首页查看任务")
                    !outcome.ok -> onDone(null, outcome.error.ifBlank { "创建失败" })
                    outcome.taskId.isBlank() -> onDone(null, "电脑没有返回任务 id")
                    else -> onDone(outcome.taskId, null)
                }
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
        val commandId = BusCommands.newCommandId()
        val json = BusCommands.followUp(
            commandId = commandId,
            createdAt = BusCommands.createdAt(),
            agentId = agentId,
            taskId = taskId,
            prompt = prompt,
            model = model,
            effort = effort,
        )
        relay.busCommand(json) { ack ->
            if (ack.status != 200 && ack.status != 202) {
                onDone(false, if (ack.body.isBlank()) "发送失败（${ack.status}）" else ack.body)
                return@busCommand
            }
            awaitCommand(commandId) { outcome ->
                if (outcome == null) onDone(false, "电脑没有确认，请稍后重试")
                else onDone(outcome.ok, outcome.error)
            }
        }
    }

    override fun interrupt(agentId: String, taskId: String, onDone: (ok: Boolean, error: String) -> Unit) {
        val commandId = BusCommands.newCommandId()
        val json = BusCommands.interrupt(
            commandId = commandId,
            createdAt = BusCommands.createdAt(),
            agentId = agentId,
            taskId = taskId,
        )
        relay.busCommand(json) { ack ->
            if (ack.status != 200 && ack.status != 202) {
                onDone(false, if (ack.body.isBlank()) "停止失败（${ack.status}）" else ack.body)
                return@busCommand
            }
            awaitCommand(commandId) { outcome ->
                if (outcome == null) onDone(false, "电脑没有确认，请稍后重试")
                else onDone(outcome.ok, outcome.error)
            }
        }
    }

    override fun deleteTask(agentId: String, taskId: String, onDone: (ok: Boolean, error: String) -> Unit) {
        if (taskId.isBlank()) {
            onDone(false, "缺少任务 id")
            return
        }
        val commandId = BusCommands.newCommandId()
        val json = BusCommands.deleteTask(
            commandId = commandId,
            createdAt = BusCommands.createdAt(),
            agentId = agentId,
            taskId = taskId,
        )
        relay.busCommand(json) { ack ->
            if (ack.status != 200 && ack.status != 202) {
                onDone(false, if (ack.body.isBlank()) "删除失败（${ack.status}）" else ack.body)
                return@busCommand
            }
            awaitCommand(commandId) { outcome ->
                when {
                    outcome == null -> onDone(false, "电脑没有确认，请稍后重试")
                    !outcome.ok -> onDone(false, outcome.error.ifBlank { "删除失败" })
                    else -> onDone(true, "")
                }
            }
        }
    }

    private data class CommandOutcome(val ok: Boolean, val taskId: String, val error: String)

    /**
     * 202 只表示电脑收下命令；真正的 ok / taskId 在后续快照的 recentResults 里。
     * 300ms 轮询快照，最多约 15 秒。
     */
    private fun awaitCommand(
        commandId: String,
        attempt: Int = 0,
        onDone: (CommandOutcome?) -> Unit,
    ) {
        val outcome = commandOutcome(commandId)
        if (outcome != null) {
            onDone(outcome)
            return
        }
        if (attempt >= 50) {
            onDone(null)
            return
        }
        setTimeout(pagerId, 300) { awaitCommand(commandId, attempt + 1, onDone) }
    }

    private fun commandOutcome(commandId: String): CommandOutcome? {
        val root = runCatching {
            val json = relay.busHomeJson() ?: return null
            busParse(json).jsonObject
        }.getOrNull() ?: return null
        val results = root["recentResults"] as? JsonArray ?: return null
        for (item in results) {
            val objectItem = item as? JsonObject ?: continue
            if (objectItem.string("commandId") != commandId) continue
            if (objectItem.string("error") == "pending") return null
            return CommandOutcome(
                ok = (objectItem["ok"] as? JsonPrimitive)?.booleanOrNull == true,
                taskId = objectItem.string("taskId"),
                error = objectItem.string("error"),
            )
        }
        return null
    }
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
