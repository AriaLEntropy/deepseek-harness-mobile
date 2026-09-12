package com.example.dsh.infrastructure

internal data class LogFilter(
    val types: List<String>? = null,
    val levels: List<LogLevel>? = null,
    val sessionId: String? = null,
    val rpcId: String? = null,
    val text: String? = null,
    val fromTime: Long? = null,
    val toTime: Long? = null,
    val sessionIds: List<String>? = null,
    val beforeSeq: Long? = null,
    /** 只取 seq 大于该值的记录；用于增量拉取新日志。 */
    val afterSeq: Long? = null,
    val typeQuery: String? = null,
)

/**
 * Log persistence API. Implemented per platform so DB drivers (e.g. kuiklysqlite) stay off commonMain.
 *
 * 容量预算以**实际磁盘占用**为准，因此这里暴露的是物理与行数原语，而不是逻辑字符长度估算；
 * 分批淘汰的循环与优先级策略放在 commonMain（[DshLogWriteBehind]），便于回归测试。
 */
internal interface DshLogStore {
    fun appendBatch(events: List<LogEvent>)
    fun query(filter: LogFilter, limit: Int, offset: Int): List<LogEvent>
    fun clear()
    /** Both operations commit their crash marker in the same transaction as the log mutation. */
    fun appendCrashOnce(id: String, event: LogEvent): Boolean = error("崩溃幂等存储不可用")
    fun clearAndMarkCrash(id: String?) {
        check(id == null) { "崩溃清除标记不可用" }
        clear()
    }
    fun close() = Unit

    /** 当前库内最大 seq；空库返回 0。用于初始化写入序号，避免与已有记录重复。 */
    fun maxSeq(): Long

    /** 主文件及 WAL/SHM/journal 实际字节数。 */
    fun diskBytes(): Long

    /** 当前保留的记录总数。 */
    fun count(): Long

    /** 当前保留的 DEBUG/INFO 记录数。 */
    fun countLowLevel(): Long

    /** 删除最旧的一批记录；[lowLevelOnly] 为 true 时只删 DEBUG/INFO。返回实际删除条数。 */
    fun deleteOldest(limit: Int, lowLevelOnly: Boolean): Int

    /** 回收 DELETE 后未释放的物理空间（VACUUM）；不支持时可为空操作。 */
    fun compact()
}

internal expect fun createDshLogStore(path: String): DshLogStore

/** Pure-SQL SELECT alongside its bind args, built from a [LogFilter]. Shared across platforms. */
internal data class LogSelect(
    val sql: String,
    val args: List<String?>,
)

internal fun buildLogSelect(filter: LogFilter, limit: Int, offset: Int): LogSelect {
    val whereClauses = mutableListOf<String>()
    val args = mutableListOf<String?>()

    if (!filter.types.isNullOrEmpty()) {
        val typeConditions = filter.types.map { t ->
            if (t.endsWith(".*")) {
                args.add(t.removeSuffix(".*") + "%")
                "type LIKE ?"
            } else {
                args.add(t)
                "type = ?"
            }
        }
        whereClauses.add("(" + typeConditions.joinToString(" OR ") + ")")
    }
    if (!filter.levels.isNullOrEmpty()) {
        val levelValues = filter.levels.map { it.value.toString() }
        val placeholders = levelValues.joinToString(",") { "?" }
        args.addAll(levelValues)
        whereClauses.add("level IN ($placeholders)")
    }
    if (filter.sessionId != null) {
        args.add(filter.sessionId)
        whereClauses.add("session_id = ?")
    }
    if (filter.rpcId != null) {
        args.add(filter.rpcId)
        whereClauses.add("rpc_id = ?")
    }
    if (filter.text != null) {
        // 关键词按普通文本匹配：转义 LIKE 通配符，避免 % / _ 扩大命中范围。
        val escaped = filter.text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        args.add("%$escaped%")
        whereClauses.add("message LIKE ? ESCAPE '\\'")
    }
    if (filter.fromTime != null) {
        args.add(filter.fromTime.toString())
        whereClauses.add("time >= ?")
    }
    if (filter.toTime != null) {
        args.add(filter.toTime.toString())
        whereClauses.add("time <= ?")
    }
    if (!filter.sessionIds.isNullOrEmpty()) {
        val conditions = filter.sessionIds.map {
            if (it == "__mobile__") "(session_id IS NULL OR session_id = '')"
            else { args.add(it); "session_id = ?" }
        }
        whereClauses.add("(" + conditions.joinToString(" OR ") + ")")
    }
    filter.beforeSeq?.let { args.add(it.toString()); whereClauses.add("seq < ?") }
    filter.afterSeq?.let { args.add(it.toString()); whereClauses.add("seq > ?") }
    filter.typeQuery?.takeIf { it.isNotBlank() }?.let {
        val escaped = it.removeSuffix(".*").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        args.add(if (it.endsWith(".*")) "$escaped%" else "%$escaped%")
        whereClauses.add("type LIKE ? ESCAPE '\\'")
    }

    val where = if (whereClauses.isEmpty()) "" else "WHERE ${whereClauses.joinToString(" AND ")}"
    args.add(limit.toString())
    args.add(offset.toString())
    val sql = "SELECT seq, time, level, type, session_id, rpc_id, message, size FROM dsh_log_events $where ORDER BY seq DESC LIMIT ? OFFSET ?"
    return LogSelect(sql, args)
}
