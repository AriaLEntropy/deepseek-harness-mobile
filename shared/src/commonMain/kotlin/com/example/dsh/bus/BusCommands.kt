package com.example.dsh.bus

import com.example.dsh.platform.currentTimeMillis
import kotlin.random.Random
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

    fun approve(
        commandId: String,
        createdAt: String,
        agentId: String,
        taskId: String,
        requestId: String,
        allow: Boolean,
    ): String = buildJsonObject {
        put("id", JsonPrimitive(commandId))
        put("createdAt", JsonPrimitive(createdAt))
        put("agentId", JsonPrimitive(agentId))
        put("kind", JsonPrimitive("approve"))
        put("approve", buildJsonObject {
            put("taskId", JsonPrimitive(taskId))
            put("requestId", JsonPrimitive(requestId))
            put("decision", JsonPrimitive(if (allow) "allow" else "deny"))
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
