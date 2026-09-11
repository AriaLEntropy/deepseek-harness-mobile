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
        "INSERT OR REPLACE INTO dsh_log_events (seq, time, level, type, session_id, rpc_id, message, size) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

    const val CLEAR = "DELETE FROM dsh_log_events"

    const val SIZE_BYTES =
        "SELECT COALESCE(SUM(length(message) + length(type) + 32), 0) FROM dsh_log_events"

    const val MAX_SEQ = "SELECT COALESCE(MAX(seq), 0) FROM dsh_log_events"

    /** 第一阶段淘汰：仅删除最旧的 DEBUG/INFO（level IN 0,1）的一半，保护 WARN/ERROR。 */
    const val DROP_OLDEST_LOW_LEVEL =
        "DELETE FROM dsh_log_events WHERE seq IN (" +
            "SELECT seq FROM dsh_log_events WHERE level IN (0, 1) ORDER BY seq ASC LIMIT " +
            "(SELECT COUNT(*) / 2 FROM dsh_log_events WHERE level IN (0, 1))" +
            ")"

    /** 第二阶段淘汰：仍超限时，跨全部级别删除最旧的四分之一。 */
    const val DROP_OLDEST_ALL =
        "DELETE FROM dsh_log_events WHERE seq IN (" +
            "SELECT seq FROM dsh_log_events ORDER BY seq ASC LIMIT " +
            "(SELECT COUNT(*) / 4 FROM dsh_log_events)" +
            ")"
}
