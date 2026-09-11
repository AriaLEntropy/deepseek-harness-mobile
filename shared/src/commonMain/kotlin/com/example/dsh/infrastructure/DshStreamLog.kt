package com.example.dsh.infrastructure

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuiklybase.streaming.MarkdownBlock
import kotlin.concurrent.Volatile

/** Logcat filter: `DshStream`. */
internal object DshStreamLog {
    private const val TAG = "DshStream"
    private val sessionField = Regex("(?:^|\\s)session(?:Id)?=([^\\s]+)")
    private val rpcField = Regex("(?:^|\\s)rpcId=([^\\s]+)")

    /** Set by DshHomePage after initialization. Nullable so early calls before initialization are safe. */
    @Volatile
    internal var writeBehind: DshLogWriteBehind? = null

    /** 全局最小日志级别：低于该级别的日志既不落库也不输出到控制台。默认 INFO，DEBUG 仅用于排查 UI 渲染细节。 */
    @Volatile
    internal var minLevel: LogLevel = LogLevel.INFO

    /** 是否持久化到数据库；关闭后仅输出控制台。 */
    @Volatile
    internal var persistEnabled: Boolean = true

    /**
     * 控制台输出接收器，可注入以便测试。默认按级别路由到 KLog。
     * KLog 仅提供 d/i/e 三档，WARN 归并到 info 通道（附 `[WARN]` 前缀）。
     */
    @Volatile
    internal var consoleSink: (LogLevel, String, String) -> Unit = { level, tag, msg ->
        when (level) {
            LogLevel.DEBUG -> KLog.d(tag, msg)
            LogLevel.ERROR -> KLog.e(tag, msg)
            else -> KLog.i(tag, msg)
        }
    }

    private fun enabled(level: LogLevel): Boolean = level.value >= minLevel.value

    /** Debug-level log. Persisted to DB when writeBehind is available. */
    fun d(message: String) {
        if (!enabled(LogLevel.DEBUG)) return
        consoleSink(LogLevel.DEBUG, TAG, LogSanitizer.sanitize(message))
        persist(LogLevel.DEBUG, inferType(message), message, null, null)
    }

    /** Info-level log. Persisted to DB when writeBehind is available. */
    fun i(message: String) {
        if (!enabled(LogLevel.INFO)) return
        consoleSink(LogLevel.INFO, TAG, LogSanitizer.sanitize(message))
        persist(LogLevel.INFO, inferType(message), message, null, null)
    }

    /** Structured log with explicit type and correlation IDs. Always persisted when available. */
    fun log(level: LogLevel, type: String, message: String, sessionId: String? = null, rpcId: String? = null) {
        if (!enabled(level)) return
        consoleSink(level, TAG, LogSanitizer.sanitize(message))
        persist(level, type, message, sessionId, rpcId)
    }

    /** Warning-level log. Persisted to DB. */
    fun w(message: String) {
        if (!enabled(LogLevel.WARN)) return
        consoleSink(LogLevel.WARN, TAG, LogSanitizer.sanitize("[WARN] $message"))
        persist(LogLevel.WARN, inferType(message), message, null, null)
    }

    /** Error-level log. Persisted to DB. */
    fun e(message: String) {
        if (!enabled(LogLevel.ERROR)) return
        consoleSink(LogLevel.ERROR, TAG, LogSanitizer.sanitize(message))
        persist(LogLevel.ERROR, inferType(message), message, null, null)
    }

    fun question(message: String) {
        if (!enabled(LogLevel.INFO)) return
        consoleSink(LogLevel.INFO, "DshQuestion", LogSanitizer.sanitize(message))
        persist(LogLevel.INFO, "question", "question.$message", null, null)
    }

    fun preview(text: String, max: Int = 96): String {
        val flat = text.replace("\r", "\\r").replace("\n", "\\n")
        return if (flat.length <= max) flat else "${flat.take(max)}…(+${flat.length - max})"
    }

    fun blocks(blocks: List<MarkdownBlock>): String {
        if (blocks.isEmpty()) return "blockCount=0"
        val items = blocks.joinToString("; ") { block ->
            val kind = blockKind(block.blockContent)
            "#${block.blockIndex} kind=$kind id=${block.id} chars=${block.blockContent.length}"
        }
        return "blockCount=${blocks.size} [$items]"
    }

    fun blockKind(content: String): String {
        val line = content.trimStart()
        return when {
            line.startsWith("```") -> "code"
            line.startsWith("~~~") -> "code"
            line.startsWith("# ") -> "h1"
            line.startsWith("## ") -> "h2"
            line.startsWith("### ") -> "h3"
            line.startsWith("> ") -> "quote"
            line.startsWith("- ") || line.startsWith("* ") -> "list"
            line.firstOrNull()?.isDigit() == true && line.contains(". ") -> "olist"
            line.startsWith("|") -> "table"
            line.isEmpty() -> "empty"
            else -> "paragraph"
        }
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
