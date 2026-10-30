package com.example.dsh.bus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class BusHostKind { AgentHost, DshPlugin }

data class BusHost(
    val kind: BusHostKind,
    val agentId: String,
    val protocolVersion: String,
    val forwardsDsh: Boolean,
)

/**
 * Pure data layer for M2: turns describe/snapshot/event payloads into the
 * host model the pages read. No UI imports, no platform dependencies.
 */
class BusStore {
    var host: BusHost? = null
        private set
    var seq: Long = -1
        private set
    var agents: List<JsonObject> = emptyList()
        private set
    var tasks: List<JsonObject> = emptyList()
        private set
    var projects: List<JsonObject> = emptyList()
        private set
    var recentResults: List<JsonObject> = emptyList()
        private set
    var messagesByTask: Map<String, List<JsonObject>> = emptyMap()
        private set
    private var hasMoreByTask: Map<String, Boolean> = emptyMap()
    private var generatedAt: String = ""

    fun bindDescribe(decision: DescribeDecision) {
        when (decision) {
            is DescribeDecision.AgentHost -> {
                host = BusHost(
                    kind = BusHostKind.AgentHost,
                    agentId = decision.agentId,
                    protocolVersion = decision.protocolVersion,
                    forwardsDsh = decision.forwardsDsh,
                )
            }
            DescribeDecision.Plugin -> {
                host = BusHost(
                    kind = BusHostKind.DshPlugin,
                    agentId = "",
                    protocolVersion = "",
                    forwardsDsh = false,
                )
                agents = listOf(dshOnlyAgent())
            }
            DescribeDecision.Unknown -> Unit
        }
    }

    fun applySnapshot(element: JsonElement): SnapshotReceipt? {
        val snapshot = busCleanSnapshot(element) ?: return null
        val nextSeq = snapshot.string("seq")?.toLongOrNull() ?: return null
        seq = nextSeq
        generatedAt = snapshot.string("generatedAt").orEmpty()
        agents = snapshot.objects("agents")
        tasks = snapshot.objects("tasks")
        projects = snapshot.objects("projects")
        recentResults = snapshot.objects("recentResults")
        return SnapshotReceipt(seq, tasks.size)
    }

    fun applyEvent(element: JsonElement): SnapshotReceipt? {
        val event = element as? JsonObject ?: return null
        return when (event.string("kind")) {
            "snapshot" -> event["snapshot"]?.let { applySnapshot(it) }
            "taskUpdated" -> {
                val task = event["task"]?.let { busTaskOrNull(it) } ?: return null
                val id = task.string("id") ?: return null
                tasks = listOf(task) + tasks.filter { it.string("id") != id }
                null
            }
            "taskRemoved" -> {
                val id = event.string("taskId") ?: return null
                tasks = tasks.filter { it.string("id") != id }
                messagesByTask = messagesByTask - id
                hasMoreByTask = hasMoreByTask - id
                null
            }
            "taskMessages" -> {
                val payload = event["taskMessages"] as? JsonObject ?: return null
                val taskId = payload.string("taskId") ?: return null
                val agentId = payload.string("agentId") ?: return null
                val task = taskById(taskId) ?: return null
                if (task.string("agentId") != agentId) return null
                messagesByTask = messagesByTask + (taskId to payload.objects("messages"))
                hasMoreByTask = hasMoreByTask + (
                    taskId to ((payload["hasMore"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true)
                    )
                null
            }
            "commandResult" -> {
                val result = event["commandResult"] as? JsonObject ?: return null
                val id = result.string("commandId") ?: return null
                recentResults =
                    (listOf(result) + recentResults.filter { it.string("commandId") != id })
                        .take(MAX_RECENT_RESULTS)
                null
            }
            else -> null
        }
    }

    fun taskById(id: String): JsonObject? = tasks.firstOrNull { it.string("id") == id }

    fun messages(taskId: String): List<JsonObject> = messagesByTask[taskId].orEmpty()

    fun messagesHasMore(taskId: String): Boolean = hasMoreByTask[taskId] == true

    /**
     * 导出当前完整状态，供 Android 侧跨线程缓存后交给页面。
     * 页面重新解析时仍走 applySnapshot，协议校验不会绕过。
     */
    fun snapshotJson(): String = buildJsonObject {
        put("agents", JsonArray(agents))
        put("tasks", JsonArray(tasks))
        put("projects", JsonArray(projects))
        put("recentResults", JsonArray(recentResults))
        put("recentMessages", buildJsonArray { })
        put("seq", JsonPrimitive(seq))
        put("generatedAt", JsonPrimitive(generatedAt))
    }.toString()

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun dshOnlyAgent(): JsonObject = buildJsonObject {
        put("agentId", JsonPrimitive("dsh-plugin"))
        put("name", JsonPrimitive("DeepSeek Harness"))
        put("platform", JsonPrimitive("unknown"))
        put("online", JsonPrimitive(true))
        put("lastSeenAt", JsonPrimitive(""))
        put("appVersion", JsonPrimitive(""))
        put("connectors", buildJsonArray {
            add(buildJsonObject {
                put("kind", JsonPrimitive("dsh"))
                put("displayName", JsonPrimitive("DeepSeek Harness"))
                put("available", JsonPrimitive(true))
                put("enabled", JsonPrimitive(true))
                put("status", JsonPrimitive("ok"))
                put("taskCount", JsonPrimitive(0))
            })
        })
    }

    companion object {
        const val MAX_RECENT_RESULTS = 20
    }
}
