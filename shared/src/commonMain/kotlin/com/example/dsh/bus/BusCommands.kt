package com.example.dsh.bus

import com.example.dsh.platform.currentTimeMillis
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Command payloads that match the BotBus 3.9 plain fixtures key for key. */
object BusCommands {
    fun newCommandId(): String = "cmd-${currentTimeMillis()}-${Random.nextInt(100_000, 999_999)}"

    fun createdAt(): String = currentTimeMillis().toString()

    fun startTask(
        commandId: String,
        createdAt: String,
        agentId: String,
        source: String,
        connectorId: String?,
        projectPath: String,
        prompt: String,
        model: String? = null,
        effort: String? = null,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("startTask"))
        put("startTask", buildJsonObject {
            put("source", JsonPrimitive(source))
            if (!connectorId.isNullOrBlank()) put("connectorId", JsonPrimitive(connectorId))
            put("projectPath", JsonPrimitive(projectPath))
            put("prompt", JsonPrimitive(prompt))
            if (!model.isNullOrBlank()) put("model", JsonPrimitive(model))
            if (!effort.isNullOrBlank()) put("effort", JsonPrimitive(effort))
        })
    }.toString()

    fun followUp(
        commandId: String,
        createdAt: String,
        agentId: String,
        taskId: String,
        prompt: String,
        model: String? = null,
        effort: String? = null,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("followUp"))
        put("followUp", buildJsonObject {
            put("taskId", JsonPrimitive(taskId))
            put("prompt", JsonPrimitive(prompt))
            if (!model.isNullOrBlank()) put("model", JsonPrimitive(model))
            if (!effort.isNullOrBlank()) put("effort", JsonPrimitive(effort))
        })
    }.toString()

    /**
     * 审批决定。`answers`（协议 2.14 起）回答 `PendingRequest.questions`：
     * 问题 id -> 选中的 label（或自填的文字）。协议规定它只和 `allow` 一起出现，
     * 所以拒绝时整个键省略；空答案同样省略，保持和 2.14 之前的旧客户端一致。
     */
    fun approve(
        commandId: String,
        createdAt: String,
        agentId: String,
        taskId: String,
        requestId: String,
        allow: Boolean,
        answers: Map<String, List<String>> = emptyMap(),
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("approve"))
        put("approve", buildJsonObject {
            put("taskId", JsonPrimitive(taskId))
            put("requestId", JsonPrimitive(requestId))
            put("decision", JsonPrimitive(if (allow) "allow" else "deny"))
            if (allow) {
                val cleaned = answers.filterKeys { it.isNotBlank() }
                    .filterValues { it.isNotEmpty() }
                if (cleaned.isNotEmpty()) {
                    put("answers", buildJsonObject {
                        cleaned.forEach { (questionId, labels) ->
                            put(
                                questionId,
                                JsonArray(labels.map { JsonPrimitive(it) }),
                            )
                        }
                    })
                }
            }
        })
    }.toString()

    /**
     * 协议 2.x 起：开关一个 Agent 的启用状态（手机上的「停止」「启动」）。
     * `connector = acp` 时必须带 `connectorId`，其余 kind 必须省略。
     */
    fun setConnectorEnabled(
        commandId: String,
        createdAt: String,
        agentId: String,
        connector: String,
        enabled: Boolean,
        connectorId: String? = null,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("setConnectorEnabled"))
        put("setConnectorEnabled", buildJsonObject {
            put("connector", JsonPrimitive(connector))
            if (!connectorId.isNullOrBlank()) put("connectorId", JsonPrimitive(connectorId))
            put("enabled", JsonPrimitive(enabled))
        })
    }.toString()

    /**
     * 协议 3.9 起：在电脑上重启一个 Agent（先停用、等收尾，再启用）。
     * 只发给报了 `AgentInfo.canRestartConnectors` 的电脑；`connectorId` 规则同上。
     */
    fun restartConnector(
        commandId: String,
        createdAt: String,
        agentId: String,
        connector: String,
        connectorId: String? = null,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("restartConnector"))
        put("restartConnector", buildJsonObject {
            put("connector", JsonPrimitive(connector))
            if (!connectorId.isNullOrBlank()) put("connectorId", JsonPrimitive(connectorId))
        })
    }.toString()

    fun interrupt(
        commandId: String,
        createdAt: String,
        agentId: String,
        taskId: String,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("interrupt"))
        put("interrupt", buildJsonObject {
            put("taskId", JsonPrimitive(taskId))
        })
    }.toString()

    /** 协议 3.8：永久删除一条已停止的会话。 */
    fun deleteTask(
        commandId: String,
        createdAt: String,
        agentId: String,
        taskId: String,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("deleteTask"))
        put("deleteTask", buildJsonObject {
            put("taskId", JsonPrimitive(taskId))
        })
    }.toString()

    /** 协议 3.8：把项目从电脑端 BotBus 列表移出；不删除目录或会话记录。 */
    fun removeProject(
        commandId: String,
        createdAt: String,
        agentId: String,
        projectPath: String,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("removeProject"))
        put("removeProject", buildJsonObject {
            put("projectPath", JsonPrimitive(projectPath))
        })
    }.toString()

    fun fetchMessages(
        commandId: String,
        createdAt: String,
        agentId: String,
        taskId: String,
        limit: Int = 40,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("fetchMessages"))
        put("fetchMessages", buildJsonObject {
            put("taskId", JsonPrimitive(taskId))
            put("limit", JsonPrimitive(limit))
        })
    }.toString()
}
