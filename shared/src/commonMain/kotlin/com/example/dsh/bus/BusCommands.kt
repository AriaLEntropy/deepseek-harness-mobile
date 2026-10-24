package com.example.dsh.bus

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Command payloads that match the BotBus 3.9 plain fixtures key for key. */
object BusCommands {
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
