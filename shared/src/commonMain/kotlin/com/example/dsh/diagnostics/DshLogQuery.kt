package com.example.dsh.diagnostics

import com.example.dsh.infrastructure.*

/** Immutable worker input. Type and keyword filters can be combined: type:rpc.* timeout. */
internal data class DshLogQuery(val filter: LogFilter = LogFilter(), val search: String = "") {
    private val typeToken = Regex("(?:^|\\s)type:([^\\s]+)", RegexOption.IGNORE_CASE)
    private val match = typeToken.find(search)
    val databaseFilter: LogFilter get() = filter.copy(typeQuery = match?.groupValues?.get(1))
    private val keyword = typeToken.replace(search, "").trim()
    private val regex = keyword.takeIf { it.isNotEmpty() }?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
    fun matches(event: LogEvent): Boolean = keyword.isEmpty() ||
        (regex?.containsMatchIn(event.message) ?: event.message.contains(keyword, ignoreCase = true))

    fun readPage(source: DshLogWriteBehind, offset: Int, limit: Int): DshLogPageResult {
        val rows = mutableListOf<LogEvent>()
        val counts = mutableMapOf<LogLevel, Int>()
        var total = 0
        source.forEachPage(databaseFilter.copy(levels = null)) { page ->
            page.forEach { event ->
                if (matches(event)) {
                    counts[event.level] = (counts[event.level] ?: 0) + 1
                    if (filter.levels.isNullOrEmpty() || event.level in filter.levels) {
                        if (total >= offset && rows.size < limit) rows.add(event)
                        total++
                    }
                }
            }
        }
        return DshLogPageResult(rows, total, counts)
    }

    fun export(source: DshLogWriteBehind, dir: String, filename: String, header: String): String {
        val path = writeExportFile(dir, filename, LogSanitizer.sanitize(header) + "\n")
        source.forEachPage(databaseFilter) { page ->
            val matching = page.filter(::matches)
            if (matching.isNotEmpty()) appendExportFile(path, LogExporter.toText(matching))
        }
        return path
    }
}

internal data class DshLogPageResult(val rows: List<LogEvent>, val total: Int, val levels: Map<LogLevel, Int>)
