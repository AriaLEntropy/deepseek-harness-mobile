package com.example.dsh.infrastructure

/** One terminal record per dispatched RPC, including disconnect/cancellation. Never receives request bodies. */
internal class DshRpcLog(
    private val clock: () -> Long = ::currentTimeMillis,
    private val emit: (LogLevel, String, String, String?, String?) -> Unit = DshStreamLog::log,
) {
    private data class Pending(val method: String, val sessionId: String?, val startedAt: Long)
    private val pending = mutableMapOf<String, Pending>()

    fun start(rpcId: String, method: String, sessionId: String?) {
        pending[rpcId] = Pending(method, sessionId, clock())
        emit(LogLevel.INFO, "rpc.start", "method=$method rpcId=$rpcId", sessionId, rpcId)
    }

    fun finish(rpcId: String, code: String? = null, message: String = "", status: Int? = null) {
        val request = pending.remove(rpcId) ?: return
        val failed = code != null
        emit(if (failed) LogLevel.ERROR else LogLevel.INFO, if (failed) "rpc.failed" else "rpc.complete",
            "method=${request.method} rpcId=$rpcId durationMs=${(clock() - request.startedAt).coerceAtLeast(0)}" +
                " status=${status ?: 0}" + if (failed) " code=$code error=${LogSanitizer.sanitize(message).take(512)}" else "",
            request.sessionId, rpcId)
    }

    fun cancelAll(reason: String) {
        pending.keys.toList().forEach { finish(it, "generation-cancelled", reason) }
    }
}
