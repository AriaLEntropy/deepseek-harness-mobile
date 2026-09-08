package com.example.dsh.infrastructure

internal data class LogFilter(
    val types: List<String>? = null,
    val levels: List<LogLevel>? = null,
    val sessionId: String? = null,
    val rpcId: String? = null,
    val text: String? = null,
    val fromTime: Long? = null,
    val toTime: Long? = null,
)

/** Log persistence API. Implemented per platform so DB drivers (e.g. kuiklysqlite) stay off commonMain. */
internal interface DshLogStore {
    fun appendBatch(events: List<LogEvent>)
    fun query(filter: LogFilter, limit: Int, offset: Int): List<LogEvent>
    fun clear()
    fun sizeBytes(): Long
    fun dropOldest(keepBytes: Long)

    /** 当前库内最大 seq；空库返回 0。用于初始化写入序号，避免与已有记录重复。 */
    fun maxSeq(): Long
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
        whereClauses.add(typeConditions.joinToString(" OR "))
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
        args.add("%${filter.text}%")
        whereClauses.add("message LIKE ?")
    }
    if (filter.fromTime != null) {
        args.add(filter.fromTime.toString())
        whereClauses.add("time >= ?")
    }
    if (filter.toTime != null) {
        args.add(filter.toTime.toString())
        whereClauses.add("time <= ?")
    }

    val where = if (whereClauses.isEmpty()) "" else "WHERE ${whereClauses.joinToString(" AND ")}"
    args.add(limit.toString())
    args.add(offset.toString())
    val sql = "SELECT seq, time, level, type, session_id, rpc_id, message, size FROM dsh_log_events $where ORDER BY seq DESC LIMIT ? OFFSET ?"
    return LogSelect(sql, args)
}
