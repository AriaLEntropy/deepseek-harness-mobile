package com.example.dsh.bus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { prettyPrint = false }

val busTaskStatuses = setOf(
    "running",
    "waitingApproval",
    "waitingInput",
    "completed",
    "failed",
    "interrupted",
    "idle",
)

val busConnectorKinds = setOf("codex", "claude", "hermes", "pi", "openclaw", "acp", "dsh")

val busSources = busConnectorKinds

fun busParse(text: String): JsonElement = json.parseToJsonElement(text)

fun busEncode(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

/** A single task with an unknown closed value is rejected. Inside a snapshot, only that task is dropped. */
fun busTaskOrNull(element: JsonElement): JsonObject? {
    val task = element as? JsonObject ?: return null
    val status = task.string("status") ?: return null
    val source = task.string("source") ?: return null
    if (status !in busTaskStatuses || source !in busSources) return null
    if (task.string("id").isNullOrEmpty()) return null
    if (source == "acp" && task.string("connectorId").isNullOrEmpty()) return null
    if (source != "acp" && task.containsKey("connectorId")) return null
    return task
}

fun busCleanSnapshot(element: JsonElement): JsonObject? {
    val root = element as? JsonObject ?: return null
    val agents = root["agents"] as? JsonArray ?: return null
    if (!root.containsKey("seq") || !root.containsKey("generatedAt")) return null
    val cleanedAgents = JsonArray(agents.mapNotNull { busCleanAgent(it) })
    val tasks = root["tasks"] as? JsonArray ?: JsonArray(emptyList())
    val cleanedTasks = JsonArray(tasks.mapNotNull { busTaskOrNull(it) })
    return buildJsonObject {
        root.forEach { (key, value) ->
            when (key) {
                "agents" -> put(key, cleanedAgents)
                "tasks" -> put(key, cleanedTasks)
                else -> put(key, value)
            }
        }
    }
}

private fun busCleanAgent(element: JsonElement): JsonObject? {
    val agent = element as? JsonObject ?: return null
    if (agent.string("agentId").isNullOrEmpty()) return null
    val connectors = agent["connectors"] as? JsonArray ?: return null
    val cleaned = JsonArray(connectors.mapNotNull { item ->
        val connector = item as? JsonObject ?: return@mapNotNull null
        val kind = connector.string("kind") ?: return@mapNotNull null
        if (kind !in busConnectorKinds) null else connector
    })
    return buildJsonObject {
        agent.forEach { (key, value) ->
            if (key == "connectors") put(key, cleaned) else put(key, value)
        }
    }
}

sealed class DescribeDecision {
    data class AgentHost(
        val agentId: String,
        val protocolVersion: String,
        val forwardsDsh: Boolean,
    ) : DescribeDecision()
    data object Plugin : DescribeDecision()
    data object Unknown : DescribeDecision()
}

/** Baseline required by the phone is 3.9. A blank or non-numeric version is too old. */
fun busVersionAtLeast(version: String, minimum: String = "3.9"): Boolean {
    if (version.isBlank()) return false
    val left = version.split('.')
    val right = minimum.split('.')
    val count = maxOf(left.size, right.size)
    for (index in 0 until count) {
        val part = left.getOrNull(index)?.toIntOrNull() ?: if (index < left.size) return false else 0
        val floor = right.getOrNull(index)?.toIntOrNull() ?: if (index < right.size) return false else 0
        if (part != floor) return part > floor
    }
    return true
}

class DescribeAttempts {
    private var failures = 0

    /** Null means try again. Three failures end as Unknown, without guessing a host type. */
    fun onHttp(status: Int, body: String): DescribeDecision? {
        if (status == 404) return DescribeDecision.Plugin
        if (status == 200) {
            val host = runCatching {
                val root = busParse(body).jsonObject
                val agentId = root.string("agentId")
                if (root.string("hostKind") == "agent-host" && !agentId.isNullOrEmpty()) {
                    DescribeDecision.AgentHost(
                        agentId,
                        root.string("protocolVersion").orEmpty(),
                        root.string("forwardsDsh") == "true",
                    )
                } else {
                    null
                }
            }.getOrNull()
            if (host != null) return host
        }
        return fail()
    }

    fun onTransportFailure(): DescribeDecision? = fail()

    private fun fail(): DescribeDecision? {
        failures += 1
        return if (failures >= 3) DescribeDecision.Unknown else null
    }
}

enum class BusChannel { WebSocket, LongPoll }

/** A snapshot the phone accepted. `taskCount` can be 0. */
class SnapshotReceipt(val seq: Long, val taskCount: Int)

class BusSync {
    var seq: Long = -1
        private set
    var channel: BusChannel = BusChannel.WebSocket
        private set
    var agentId: String = ""
        private set
    private var handshakeFailures = 0
    private var sinceSocketMs = 0L
    private val tasks = linkedMapOf<String, JsonObject>()

    fun tasks(): List<JsonObject> = tasks.values.toList()

    fun bind(agentId: String) {
        this.agentId = agentId
    }

    /** Keep the last snapshot sequence across a tunnel reconnect. */
    fun resume(seq: Long) {
        if (seq >= 0) this.seq = seq
    }

    fun socketPath(): String = "/bus/ws?since=${if (seq < 0) -1 else seq}"

    fun pollPath(): String = "/bus/snapshot?since=$seq"

    fun onHandshakeOpen() {
        handshakeFailures = 0
        channel = BusChannel.WebSocket
        sinceSocketMs = 0
    }

    fun onHandshakeFailure() {
        handshakeFailures += 1
        if (handshakeFailures >= 2) channel = BusChannel.LongPoll
    }

    /** Returns true when a minute of long polling should try the websocket again. */
    fun onPollTick(elapsedMs: Long): Boolean {
        if (channel != BusChannel.LongPoll) return false
        sinceSocketMs += elapsedMs
        if (sinceSocketMs < 60_000) return false
        sinceSocketMs = 0
        handshakeFailures = 0
        channel = BusChannel.WebSocket
        return true
    }

    fun onSocketFrame(text: String): SnapshotReceipt? {
        val frame = runCatching { busParse(text).jsonObject }.getOrNull() ?: return null
        return when (frame.string("type")) {
            "snapshot" -> frame["snapshot"]?.let { replaceSnapshot(it) }
            "event" -> frame["event"]?.let { applyEvent(it) }
            else -> null
        }
    }

    fun onSnapshotResponse(text: String): SnapshotReceipt? {
        return runCatching { replaceSnapshot(busParse(text)) }.getOrNull()
    }

    private fun replaceSnapshot(element: JsonElement): SnapshotReceipt? {
        val snapshot = busCleanSnapshot(element) ?: return null
        val seqValue = snapshot["seq"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        if (seqValue != null) seq = seqValue
        tasks.clear()
        val list = snapshot["tasks"] as? JsonArray ?: return null
        list.forEach { item ->
            val task = item as? JsonObject ?: return@forEach
            val id = task.string("id") ?: return@forEach
            tasks[id] = task
        }
        return SnapshotReceipt(seq, tasks.size)
    }

    private fun applyEvent(element: JsonElement): SnapshotReceipt? {
        val event = element as? JsonObject ?: return null
        return when (event.string("kind")) {
            "taskUpdated" -> {
                val task = event["task"]?.let { busTaskOrNull(it) } ?: return null
                val id = task.string("id") ?: return null
                tasks[id] = task
                null
            }
            "taskRemoved" -> {
                event.string("taskId")?.let { tasks.remove(it) }
                null
            }
            "snapshot" -> event["snapshot"]?.let { replaceSnapshot(it) }
            else -> null
        }
    }
}

private fun JsonObject.string(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    return value.contentOrNull
}
