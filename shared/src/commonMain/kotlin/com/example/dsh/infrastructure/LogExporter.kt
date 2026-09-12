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

/** 将临时导出文件发布为最终文件；同名目标应被替换。用于「成功后才出现最终文件」。 */
internal expect fun renameExportFile(fromPath: String, toPath: String)

/** 删除导出文件；不存在时静默忽略。用于失败/取消时清理半成品。 */
internal expect fun deleteExportFile(path: String)

/** 列出目录下的文件绝对路径；不支持目录列举的平台返回空列表。 */
internal expect fun listExportFiles(dir: String): List<String>

/** Actual file length, including allocated SQLite auxiliary files; missing files contribute zero. */
internal expect fun fileSizeBytes(path: String): Long

/** Serializes publish/prune across pages, independently of the SQLite writer lock. */
internal val exportPublishLock = DshLock()
private val exportRegistryLock = DshLock()
private val activePartials = mutableSetOf<String>()
internal fun beginExport(filename: String) = exportRegistryLock.withLock {
    check(activePartials.add("$filename.partial")) { "同名文件正在导出，请稍后重试" }
}
internal fun endExport(filename: String) = exportRegistryLock.withLock { activePartials.remove("$filename.partial") }

internal fun databaseDiskBytes(path: String): Long =
    listOf("", "-wal", "-shm", "-journal").sumOf { fileSizeBytes(path + it) }

/**
 * 应用内部导出缓存回收：只保留最近一次有效导出（[keepPath]）与用户自有文件，
 * 删除更早的应用导出及其残留半成品。用户主动保存到外部位置的文件不在本目录内，
 * 不会被回收。
 */
internal fun pruneExportCache(dir: String, keepPath: String) {
    if (dir.isBlank()) return
    runCatching {
        for (path in listExportFiles(dir)) {
            if (path == keepPath) continue
            val name = path.substringAfterLast('/').substringAfterLast('\\')
            val readable = keepPath.substringAfterLast('/').substringAfterLast('\\').startsWith("dsh-session-")
            val active = exportRegistryLock.withLock { name in activePartials }
            if (!active && isAppManagedExport(name) && name.startsWith("dsh-session-") == readable) runCatching { deleteExportFile(path) }
        }
    }
}

internal fun publishReadableExport(dir: String, filename: String, content: String, isCancelled: () -> Boolean): String {
    beginExport(filename)
    var temporary: String? = null
    try {
        if (isCancelled()) throw kotlinx.coroutines.CancellationException()
        val path = writeExportFile(dir, "$filename.partial", content)
        temporary = path
        if (isCancelled()) throw kotlinx.coroutines.CancellationException()
        val final = path.removeSuffix(".partial")
        exportPublishLock.withLock {
            renameExportFile(path, final)
            pruneExportCache(dir, final)
        }
        return final
    } catch (t: Throwable) {
        temporary?.let { runCatching { deleteExportFile(it) } }
        throw t
    } finally { endExport(filename) }
}

private val appExportSuffixes = listOf(".txt", ".md", ".html")
private fun isAppManagedExport(fileName: String): Boolean =
    fileName.startsWith("dsh-") &&
        appExportSuffixes.any { fileName.endsWith(it) || fileName.endsWith("$it.partial") }

/** Open the system share sheet for a file at the given path. */
internal expect fun shareExportFile(path: String)
