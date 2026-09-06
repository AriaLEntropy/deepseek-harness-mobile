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

/** Logcat filter: `DshStream`. */
internal object DshStreamLog {
    private const val TAG = "DshStream"

    /** Set by DshHomePage after initialization. Nullable so early calls before initialization are safe. */
    internal var writeBehind: DshLogWriteBehind? = null

    /** Info-level log. Persisted to DB when writeBehind is available. */
    fun i(message: String) {
        KLog.i(TAG, message)
        persist(LogLevel.INFO, inferType(message), message, null, null)
    }

    /** Structured log with explicit type and correlation IDs. Always persisted when available. */
    fun log(level: LogLevel, type: String, message: String, sessionId: String? = null, rpcId: String? = null) {
        KLog.i(TAG, message)
        persist(level, type, message, sessionId, rpcId)
    }

    /** Warning-level log. Persisted to DB. */
    fun w(message: String) {
        KLog.i(TAG, "[WARN] $message")
        persist(LogLevel.WARN, inferType(message), message, null, null)
    }

    /** Error-level log. Persisted to DB. */
    fun e(message: String) {
        KLog.e(TAG, message)
        persist(LogLevel.ERROR, inferType(message), message, null, null)
    }

    fun question(message: String) {
        KLog.i("DshQuestion", message)
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
            "#${block.blockIndex} kind=$kind id=${block.id} chars=${block.blockContent.length} '${preview(block.blockContent, 48)}'"
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
        val wb = writeBehind ?: return
        val sanitized = LogSanitizer.sanitize(message)
        wb.enqueue(LogEvent(
            seq = 0,
            timestamp = currentTimeMillis(),
            level = level,
            type = type,
            sessionId = sessionId,
            rpcId = rpcId,
            message = sanitized,
            size = message.length,
        ))
    }
}
