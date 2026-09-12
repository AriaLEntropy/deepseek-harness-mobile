package com.example.dsh.infrastructure

/**
 * 日志表的建表与维护 SQL，集中定义。
 *
 * 三端（Android/iOS/OHOS）的 [DshLogStore] 实现依赖各自平台的 SQLite 驱动类型，
 * 无法共享绑定/映射代码，但可共用同一份 schema 与语句常量，避免列顺序、表名、
 * 淘汰策略在多端之间产生漂移。
 */
internal object DshLogSql {
    const val CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS dsh_log_events (seq INTEGER PRIMARY KEY, time INTEGER NOT NULL, level INTEGER NOT NULL, type TEXT NOT NULL, session_id TEXT, rpc_id TEXT, message TEXT NOT NULL, size INTEGER NOT NULL)"

    const val CREATE_INDEX_TIME =
        "CREATE INDEX IF NOT EXISTS idx_dsh_log_time ON dsh_log_events(time)"

    const val CREATE_INDEX_TYPE =
        "CREATE INDEX IF NOT EXISTS idx_dsh_log_type ON dsh_log_events(type)"
    const val CREATE_INDEX_SESSION =
        "CREATE INDEX IF NOT EXISTS idx_dsh_log_session_seq ON dsh_log_events(session_id, seq)"

    const val INSERT =
        "INSERT INTO dsh_log_events (seq, time, level, type, session_id, rpc_id, message, size) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

    const val CREATE_STATE = "CREATE TABLE IF NOT EXISTS dsh_log_state (slot INTEGER PRIMARY KEY CHECK(slot=1), crash_id TEXT NOT NULL)"
    const val LAST_CRASH = "SELECT COALESCE((SELECT crash_id FROM dsh_log_state WHERE slot=1), '')"

    // kuiklySqlite 1.0.0 statement.step() hides sqlite3_step errors. execute() checks rc.
    // Numeric values are typed; text literals escape quotes and embedded NUL before C interop.
    fun literal(value: String?): String = value?.let { "'${it.replace("\u0000", "\\0").replace("'", "''")}'" } ?: "NULL"
    fun insertEvent(event: LogEvent): String =
        "INSERT INTO dsh_log_events (seq,time,level,type,session_id,rpc_id,message,size) VALUES (" +
            "${event.seq},${event.timestamp},${event.level.value},${literal(event.type)},${literal(event.sessionId)}," +
            "${literal(event.rpcId)},${literal(event.message)},${event.size})"
    fun markCrash(id: String): String = "INSERT OR REPLACE INTO dsh_log_state(slot,crash_id) VALUES(1,${literal(id)})"

    // A terminal row distinguishes a legitimate empty result from a swallowed step() failure.
    fun checkedSelect(sql: String): String =
        "SELECT * FROM ($sql) UNION ALL SELECT NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL"

    const val CLEAR = "DELETE FROM dsh_log_events"

    const val MAX_SEQ = "SELECT COALESCE(MAX(seq), 0) FROM dsh_log_events"

    const val COUNT_ALL = "SELECT COUNT(*) FROM dsh_log_events"

    const val COUNT_LOW_LEVEL = "SELECT COUNT(*) FROM dsh_log_events WHERE level IN (0, 1)"

    /** 删除最旧的一批 DEBUG/INFO；`LIMIT ?` 由调用方给出，保证每次至少推进 1 条。 */
    const val DELETE_OLDEST_LOW_LEVEL =
        "DELETE FROM dsh_log_events WHERE seq IN (" +
            "SELECT seq FROM dsh_log_events WHERE level IN (0, 1) ORDER BY seq ASC LIMIT ?)"

    /** 删除最旧的一批任意级别记录；用于低级别已删空或全为高等级时的兜底推进。 */
    const val DELETE_OLDEST_ANY =
        "DELETE FROM dsh_log_events WHERE seq IN (" +
            "SELECT seq FROM dsh_log_events ORDER BY seq ASC LIMIT ?)"

    /** 回收 DELETE 后未释放的物理页，使实际磁盘占用下降。 */
    const val VACUUM = "VACUUM"
    const val CHECKPOINT = "PRAGMA wal_checkpoint(TRUNCATE)"

    /** 实际已分配页数与页大小；两者相乘约等于数据库文件实际字节数。 */
    const val PAGE_COUNT = "PRAGMA page_count"
    const val PAGE_SIZE = "PRAGMA page_size"
}
