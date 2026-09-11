package com.example.dsh.infrastructure

internal expect fun localTimezoneOffsetMillis(): Long

/**
 * 设备本地时区偏移缓存（毫秒，UTC→本地为正）。嵌入式 JS 引擎（QuickJS）无系统时区数据，
 * Date.getTimezoneOffset() 恒为 0；由 BasePager 页面创建时向原生侧同步查询一次写入。
 */
internal var cachedLocalTimezoneOffsetMillis: Long? = null

internal object LogExporter {

    fun toJson(events: List<LogEvent>): String = buildString {
        appendLine("[")
        for ((i, original) in events.withIndex()) {
            val e = LogSanitizer.sanitize(original)
            appendLine("  {")
            appendLine("    \"seq\": ${e.seq},")
            appendLine("    \"time\": ${e.timestamp},")
            appendLine("    \"level\": \"${e.level.name}\",")
            appendLine("    \"type\": ${jsonString(e.type)},")
            appendLine("    \"sessionId\": ${jsonNullable(e.sessionId)},")
            appendLine("    \"rpcId\": ${jsonNullable(e.rpcId)},")
            appendLine("    \"message\": ${jsonString(e.message)},")
            appendLine("    \"size\": ${e.size}")
            append("  }")
            if (i < events.size - 1) appendLine(",") else appendLine()
        }
        appendLine("]")
    }

    fun toText(events: List<LogEvent>): String = buildString {
        for (original in events) {
            val e = LogSanitizer.sanitize(original)
            val ts = formatTimestamp(e.timestamp)
            val sid = e.sessionId?.let { "[$it]" }.orEmpty()
            appendLine("[$ts][${e.level.name}][${e.type}]$sid seq=${e.seq} rpcId=${e.rpcId ?: "-"} ${e.message}")
        }
    }

    private fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < '\u0020') {
                    // 其余控制字符按 \uXXXX 转义，保证输出为合法 JSON。
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    private fun jsonNullable(s: String?): String = s?.let { jsonString(it) } ?: "null"

    /** 将毫秒时间戳格式化为 `yyyy-MM-ddTHH:mm:ss.SSS`（纯算术实现，可跨平台）。 */
    fun formatTimestamp(epochMs: Long): String {
        val adjusted = epochMs + localTimezoneOffsetMillis()
        val totalSeconds = adjusted / 1000
        val ms = (epochMs % 1000).toInt()
        var days = totalSeconds / 86400
        val timeOfDay = (totalSeconds % 86400).toInt()
        var year = 1970
        while (true) {
            val daysInYear = if (isLeapYear(year)) 366 else 365
            if (days < daysInYear) break
            days -= daysInYear
            year++
        }
        val md = monthDays(year)
        var month = 1
        for (mdVal in md) {
            if (days < mdVal) break
            days -= mdVal
            month++
        }
        val day = (days + 1).toInt()
        val h = timeOfDay / 3600
        val m = (timeOfDay % 3600) / 60
        val s = timeOfDay % 60
        return "${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}T${pad(h, 2)}:${pad(m, 2)}:${pad(s, 2)}.${pad(ms, 3)}"
    }

    private fun isLeapYear(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    private fun monthDays(year: Int): IntArray = if (isLeapYear(year))
        intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    else
        intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')

    /** 灏?y/m/d h:m 缁勮鍏嬩负 epoch 姣锛堢函绠楁湳锛屼笌 formatTimestamp 浜掗€嗭級銆?*/
    fun parseEpoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        var days = 0
        for (y in 1970 until year) days += if (isLeapYear(y)) 366 else 365
        val md = monthDays(year)
        for (i in 0 until month - 1) days += md[i]
        days += day - 1
        return (days * 86400L + hour * 3600L + minute * 60L) * 1000L - localTimezoneOffsetMillis()
    }
}

internal expect fun writeExportFile(dir: String, filename: String, content: String): String
internal expect fun appendExportFile(path: String, content: String)

/** Open the system share sheet for a file at the given path. */
internal expect fun shareExportFile(path: String)
