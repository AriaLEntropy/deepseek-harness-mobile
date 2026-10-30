package com.example.dsh.connection

import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

enum class DshRelayPhase {
    IDLE, SCANNING, PAIRING, CONNECTING, HANDSHAKING, READY, RECONNECTING, ERROR, STOPPED,
}

data class DshRelayState(
    val phase: DshRelayPhase = DshRelayPhase.IDLE,
    val message: String = "",
    val localPort: Int = 0,
    val localToken: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val relayOrigin: String = "",
    val paired: Boolean = false,
    val generation: Long = 0,
)

data class DshBusCommandAck(
    val status: Int,
    val body: String = "",
)

data class DshRelayPairResult(
    val ok: Boolean,
    val message: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val relayOrigin: String = "",
    val pairedAt: Long = 0,
)

class DshRelayModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    fun scanAndPair(onResult: (DshRelayPairResult) -> Unit) {
        toNative(true, "scanAndPair", null, { value ->
            onResult(
                DshRelayPairResult(
                    ok = value?.optBoolean("ok") == true,
                    message = value?.optString("message").orEmpty(),
                    hostId = value?.optString("hostId").orEmpty(),
                    hostName = value?.optString("hostName").orEmpty(),
                    relayOrigin = value?.optString("relayOrigin").orEmpty(),
                    pairedAt = value?.optLong("pairedAt") ?: 0L,
                ),
            )
        }, false)
    }

    fun connect(onState: (DshRelayState) -> Unit) {
        toNative(true, "connect", null, { value -> onState(value.toRelayState()) }, false)
    }

    fun disconnect() {
        toNative(false, "disconnect", null, null, false)
    }

    fun forget(onDone: (Boolean) -> Unit) {
        toNative(false, "forget", null, { value ->
            onDone(value?.optBoolean("ok") == true)
        }, false)
    }

    fun status(): DshRelayState {
        val raw = toNative(false, "status", null, null, true).toString()
        return runCatching { JSONObject(raw).toRelayState() }.getOrDefault(DshRelayState())
    }

    /** 当前 agent-host 快照；没有连接或还没收到快照时返回 null。 */
    fun busHomeJson(): String? {
        val raw = toNative(false, "busHome", null, null, true).toString()
        return runCatching { JSONObject(raw).optString("json") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** "agent-host" / "dsh-plugin" / "unknown"；还没探测完时返回 null。 */
    fun busHostKind(): String? {
        val raw = toNative(false, "busHostKind", null, null, true).toString()
        return runCatching { JSONObject(raw).optString("kind") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** POST /bus/commands；202 表示电脑已接收，结果通过快照的 recentResults 返回。 */
    fun busCommand(json: String, onResult: (DshBusCommandAck) -> Unit) {
        val params = JSONObject().apply { put("json", json) }
        toNative(true, "busCommand", params.toString(), { value ->
            onResult(
                DshBusCommandAck(
                    status = value?.optInt("status") ?: 0,
                    body = value?.optString("body").orEmpty(),
                ),
            )
        }, false)
    }

    /** 读取某任务通过 `taskMessages` 事件缓存下来的消息 JSON。 */
    fun busTaskMessages(taskId: String): String? {
        val params = JSONObject().apply { put("taskId", taskId) }
        val raw = toNative(false, "busTaskMessages", params.toString(), null, true).toString()
        return runCatching { JSONObject(raw).optString("json") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun subscribe(onState: (DshRelayState) -> Unit) {
        toNative(true, "subscribe", null, { value -> onState(value.toRelayState()) }, false)
    }

    companion object {
        const val MODULE_NAME = "DshRelayModule"
    }
}

private fun JSONObject?.toRelayState(): DshRelayState {
    val value = this ?: return DshRelayState()
    return DshRelayState(
        phase = runCatching { DshRelayPhase.valueOf(value.optString("phase", "IDLE")) }
            .getOrDefault(DshRelayPhase.IDLE),
        message = value.optString("message"),
        localPort = value.optInt("localPort"),
        localToken = value.optString("localToken"),
        hostId = value.optString("hostId"),
        hostName = value.optString("hostName"),
        relayOrigin = value.optString("relayOrigin"),
        paired = value.optBoolean("paired"),
        generation = value.optLong("generation"),
    )
}
