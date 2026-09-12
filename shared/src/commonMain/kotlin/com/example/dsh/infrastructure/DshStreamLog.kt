package com.example.dsh.infrastructure

import com.tencent.kuikly.core.log.KLog
import kotlin.concurrent.Volatile

/** Logcat filter: `DshStream`. */
internal object DshStreamLog {
    private const val TAG = "DshStream"
    private val sessionField = Regex("(?:^|\\s)session(?:Id)?=([^\\s]+)")
    private val rpcField = Regex("(?:^|\\s)rpcId=([^\\s]+)")

    /**
     * 应用级唯一写入器（由 [DshLogService] 持有）。Nullable，初始化前的早期调用安全跳过。
     * 写入器的创建/替换/关闭由服务作为唯一所有者管理，页面不得直接改写。
     */
    internal val writeBehind: DshLogWriteBehind? get() = DshLogService.current

    /** DSH 事件摘要 + App 关键状态。DEBUG 保留结构性 chunk；不采集 token、解析和渲染过程。 */
    @Volatile
    internal var minLevel: LogLevel = LogLevel.DEBUG

    /** 是否持久化到数据库；关闭后仅输出控制台。 */
    @Volatile
    internal var persistEnabled: Boolean = true

    /**
     * 控制台按级别路由到 KLog。
     * KLog 仅提供 d/i/e 三档，WARN 归并到 info 通道（附 `[WARN]` 前缀）。
     */
    private fun consoleSink(level: LogLevel, tag: String, msg: String) {
        when (level) {
            LogLevel.DEBUG -> KLog.d(tag, msg)
            LogLevel.ERROR -> KLog.e(tag, msg)
            else -> KLog.i(tag, msg)
        }
    }

    private fun enabled(level: LogLevel): Boolean = level.value >= minLevel.value

    /** Debug-level log. Persisted to DB when writeBehind is available. */
    fun d(message: String) = log(LogLevel.DEBUG, inferType(message), message)

    /** Info-level log. Persisted to DB when writeBehind is available. */
    fun i(message: String) = log(LogLevel.INFO, inferType(message), message)

    /** Structured log with explicit type and correlation IDs. Always persisted when available. */
    fun log(level: LogLevel, type: String, message: String, sessionId: String? = null, rpcId: String? = null) {
        if (!enabled(level)) return
        val context = sessionId?.let { " session=$it" }.orEmpty() + rpcId?.let { " rpcId=$it" }.orEmpty()
        consoleSink(level, TAG, LogSanitizer.sanitize("[$level] [$type]$context ${message.removePrefix("$type ")}"))
        persist(level, type, message, sessionId, rpcId)
    }

    /** Warning-level log. Persisted to DB. */
    fun w(message: String) = log(LogLevel.WARN, inferType(message), message)

    /** Error-level log. Persisted to DB. */
    fun e(message: String) = log(LogLevel.ERROR, inferType(message), message)

    fun preview(text: String, max: Int = 96): String {
        val flat = text.replace("\r", "\\r").replace("\n", "\\n")
        return if (flat.length <= max) flat else "${flat.take(max)}…(+${flat.length - max})"
    }

    /**
     * Infer a log type from the message prefix.
     * Convention: messages start with "domain.action details..." — we take the first token.
     * Falls back to "generic" when the message has no recognizable prefix.
     */
    private fun inferType(message: String): String {
        val firstSpace = message.indexOfFirst { it == ' ' || it == '\t' }
        val prefix = if (firstSpace > 0) message.substring(0, firstSpace) else message
        return if (prefix.length in 2..64 && prefix.any { it == '.' }) prefix else "generic"
    }

    private fun persist(level: LogLevel, type: String, message: String, sessionId: String?, rpcId: String?) {
        if (!persistEnabled) return
        val wb = writeBehind ?: return
        val safeMessage = LogSanitizer.sanitize(message).take(16_384)
        wb.enqueue(LogEvent(
            seq = 0,
            timestamp = currentTimeMillis(),
            level = level,
            type = type,
            sessionId = sessionId ?: sessionField.find(message)?.groupValues?.get(1)?.takeIf { it != "null" },
            rpcId = rpcId ?: rpcField.find(message)?.groupValues?.get(1)?.takeIf { it != "null" },
            message = safeMessage,
            size = safeMessage.length,
        ))
    }
}
