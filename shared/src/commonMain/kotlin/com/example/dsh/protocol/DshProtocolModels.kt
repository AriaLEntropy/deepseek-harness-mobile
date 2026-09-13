package com.example.dsh.protocol


internal enum class DshConnectionMode {
    LOCAL,
    RELAY,
    SSH,
}


internal enum class DshHostRuntimePhase {
    DISCONNECTED,
    CONNECTING,
    HOST_HANDSHAKE,
    SYNCING,
    READY,
    RECONNECTING,
    ERROR,
    STOPPED,
}


internal data class DshHostRuntimeState(
    val phase: DshHostRuntimePhase,
    val generation: Long,
    val muxOpen: Boolean = false,
    val hostOpen: Boolean = false,
    val message: String = "",
)


internal enum class DshEventStream {
    MUX,
    HOST,
}

/** A raw downlink frame. Reducers must route mux frames by sessionId. */

/** A raw downlink frame. Reducers must route mux frames by sessionId. */
internal data class DshDownlinkFrame(
    val generation: Long,
    val stream: DshEventStream,
    val raw: String,
)


internal data class DshRpcError(
    val code: String,
    val message: String,
    val details: String = "{}",
)


internal fun dshIsTransportInterrupt(code: String, message: String = ""): Boolean {
    if (code == "generation-cancelled" || code == "cancelled") return true
    if (code.startsWith("transport-")) return true
    return message.contains("世代已失效") || message.contains("连接已停止")
}


internal enum class DshRemoteFailure {
    KEY_MISSING,
    AUTH_FAILED,
    HOST_FINGERPRINT_REQUIRED,
    SSH_UNREACHABLE,
    SSH_PORT_IN_USE,
    DSH_UNAVAILABLE,
}

