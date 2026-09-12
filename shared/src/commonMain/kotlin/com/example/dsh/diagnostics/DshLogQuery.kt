package com.example.dsh.diagnostics

import com.example.dsh.infrastructure.*

/** Immutable worker input. Keywords are literal; event types have their own filter. */
internal data class DshLogQuery(val filter: LogFilter = LogFilter(), val search: String = "") {
    private val keyword = search.trim()

    /** 库查询条件：关键词下沉到 SQL LIKE，级别过滤由调用方按口径决定。 */
    private fun baseFilter(levels: List<LogLevel>?): LogFilter =
        filter.copy(levels = levels, text = keyword.takeIf { it.isNotEmpty() })

    /**
     * 首屏/全量刷新：统计 total、级别计数，可选地重建会话/类型目录，并取第一页。
     * 统计口径需要遍历一次匹配集合，因此只应在打开、切筛选或低频对账时调用；
     * 懒加载与轮询走 [readOlder]/[readNewer]，不重复全表扫描。
     */
    fun readPage(
        source: DshLogWriteBehind,
        offset: Int,
        limit: Int,
        includeCatalog: Boolean = false,
        isCancelled: () -> Boolean = { false },
    ): DshLogPageResult {
        // Read the complete retained catalog, independent of filters and the visible page.
        val catalog = if (includeCatalog) {
            val sessions = mutableSetOf<String>()
            val types = mutableSetOf<String>()
            var retained = 0
            source.forEachPage(LogFilter(), isCancelled) { page ->
                retained += page.size
                page.forEach {
                    sessions.add(it.sessionId?.takeIf(String::isNotEmpty) ?: DshLogFilters.UNASSOCIATED)
                    types.add(it.type)
                }
            }
            DshLogCatalog(sessions.sorted(), types.sorted(), retained)
        } else null
        val rows = mutableListOf<LogEvent>()
        val counts = mutableMapOf<LogLevel, Int>()
        var total = 0
        source.forEachPage(baseFilter(null), isCancelled) { page ->
            page.forEach { event ->
                counts[event.level] = (counts[event.level] ?: 0) + 1
                if (filter.levels.isNullOrEmpty() || event.level in filter.levels) {
                    if (total >= offset && rows.size < limit) rows.add(event)
                    total++
                }
            }
        }
        return DshLogPageResult(rows, total, counts, catalog)
    }

    /**
     * 比 [afterSeq] 更新的记录（含关键词/会话/时间/类型条件，不含级别过滤），
     * 只取前 [limit] 条，用于增量轮询新日志。
     */
    fun readNewer(
        source: DshLogWriteBehind,
        afterSeq: Long,
        limit: Int,
        isCancelled: () -> Boolean = { false },
    ): List<LogEvent> = source.readSlice(baseFilter(null).copy(afterSeq = afterSeq), limit, isCancelled)

    /** 比 [beforeSeq] 更旧的下一页（含全部筛选条件），只取 [limit] 条，用于触底懒加载。 */
    fun readOlder(
        source: DshLogWriteBehind,
        beforeSeq: Long,
        limit: Int,
        isCancelled: () -> Boolean = { false },
    ): List<LogEvent> = source.readSlice(baseFilter(filter.levels).copy(beforeSeq = beforeSeq), limit, isCancelled)

    fun export(
        source: DshLogWriteBehind,
        dir: String,
        filename: String,
        header: String,
        isCancelled: () -> Boolean = { false },
    ): String {
        // 先写临时文件，全部落盘成功后再发布为最终名称；失败/取消时删除半成品，
        // 保证目录里不会出现只有抬头或部分内容的最终文件。
        var tempPath: String? = null
        beginExport(filename)
        try {
            if (isCancelled()) throw kotlinx.coroutines.CancellationException("日志导出已取消")
            val partial = writeExportFile(dir, filename + EXPORT_PARTIAL_SUFFIX, LogSanitizer.sanitize(header) + "\n")
            tempPath = partial
            val finalPath = partial.removeSuffix(EXPORT_PARTIAL_SUFFIX)
            source.forEachPage(baseFilter(filter.levels), isCancelled) { page ->
                if (page.isNotEmpty()) appendExportFile(partial, LogExporter.toText(page))
            }
            if (isCancelled()) throw kotlinx.coroutines.CancellationException("日志导出已取消")
            exportPublishLock.withLock {
                renameExportFile(partial, finalPath)
                pruneExportCache(dir, finalPath)
            }
            return finalPath
        } catch (t: Throwable) {
            tempPath?.let { runCatching { deleteExportFile(it) } }
            throw t
        } finally { endExport(filename) }
    }

    internal companion object {
        /** 导出临时文件后缀；完成写入后去掉该后缀得到最终文件。 */
        const val EXPORT_PARTIAL_SUFFIX = ".partial"
    }
}

internal data class DshLogCatalog(val sessions: List<String>, val types: List<String>, val retained: Int)
internal data class DshLogPageResult(
    val rows: List<LogEvent>, val total: Int, val levels: Map<LogLevel, Int>, val catalog: DshLogCatalog? = null,
)
