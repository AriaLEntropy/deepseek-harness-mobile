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
     * 统计全部下沉到 SQLite 聚合（COUNT / GROUP BY / DISTINCT），不再为计数整表逐行读取，
     * 因此打开、切筛选与低频对账都只付出少量聚合查询 + 一页明细的成本。
     */
    fun readPage(
        source: DshLogWriteBehind,
        offset: Int,
        limit: Int,
        includeCatalog: Boolean = false,
        isCancelled: () -> Boolean = { false },
    ): DshLogPageResult {
        // 级别计数取「级别过滤前」的口径（levels = null），total 由选中级别求和得出。
        val stats = source.logStats(baseFilter(null), includeCatalog, isCancelled)
        val selected = filter.levels
        val total = if (selected.isNullOrEmpty()) {
            stats.levels.values.sum()
        } else {
            stats.levels.entries.sumOf { if (it.key in selected) it.value else 0L }
        }
        val rows = source.readSlice(baseFilter(filter.levels), limit, isCancelled, offset)
        val catalog = if (includeCatalog) {
            DshLogCatalog(
                sessions = stats.sessions.map { it.ifEmpty { DshLogFilters.UNASSOCIATED } }.distinct().sorted(),
                types = stats.types.sorted(),
                retained = stats.retained.toInt(),
            )
        } else null
        return DshLogPageResult(rows, total.toInt(), stats.levels.mapValues { it.value.toInt() }, catalog)
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
