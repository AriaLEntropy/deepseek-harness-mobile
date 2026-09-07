package com.example.dsh.infrastructure

import net.shantu.kuiklysqlite.ColumnType
import net.shantu.kuiklysqlite.DatabaseManager
import net.shantu.kuiklysqlite.SqlDriver
import net.shantu.kuiklysqlite.SqlSchema
import net.shantu.kuiklysqlite.SqlStatement

internal actual fun createDshLogStore(path: String): DshLogStore = DshIosLogStore(path)

private class DshIosLogStore(path: String) : DshLogStore {
    private val driver: SqlDriver by lazy {
        DatabaseManager(path, NoOpSchema).driver
    }

    init {
        driver.execute("CREATE TABLE IF NOT EXISTS dsh_log_events (seq INTEGER PRIMARY KEY, time INTEGER NOT NULL, level INTEGER NOT NULL, type TEXT NOT NULL, session_id TEXT, rpc_id TEXT, message TEXT NOT NULL, size INTEGER NOT NULL)")
        driver.execute("CREATE INDEX IF NOT EXISTS idx_dsh_log_time ON dsh_log_events(time)")
        driver.execute("CREATE INDEX IF NOT EXISTS idx_dsh_log_type ON dsh_log_events(type)")
    }

    override fun appendBatch(events: List<LogEvent>) {
        if (events.isEmpty()) return
        driver.transaction {
            for (event in events) {
                val s = driver.prepare(INSERT_SQL)
                try {
                    s.bindString(1, event.seq.toString())
                    s.bindString(2, event.timestamp.toString())
                    s.bindString(3, event.level.value.toString())
                    s.bindString(4, event.type)
                    s.bindString(5, event.sessionId)
                    s.bindString(6, event.rpcId)
                    s.bindString(7, event.message)
                    s.bindString(8, event.size.toString())
                    s.step()
                } finally {
                    s.close()
                }
            }
        }
    }

    override fun query(filter: LogFilter, limit: Int, offset: Int): List<LogEvent> {
        val select = buildLogSelect(filter, limit, offset)
        val s = driver.prepare(select.sql)
        return try {
            select.args.forEachIndexed { i, v -> s.bindString(i + 1, v) }
            buildList {
                while (s.step()) add(mapEvent(s))
            }
        } finally {
            s.close()
        }
    }

    override fun clear() {
        driver.execute("DELETE FROM dsh_log_events")
    }

    override fun sizeBytes(): Long = queryOne(
        "SELECT COALESCE(SUM(length(message) + length(type) + 32), 0) FROM dsh_log_events",
        emptyList(),
    ) { it.getColumnLong(0) } ?: 0L

    override fun maxSeq(): Long = queryOne(
        "SELECT COALESCE(MAX(seq), 0) FROM dsh_log_events",
        emptyList(),
    ) { it.getColumnLong(0) } ?: 0L

    override fun dropOldest(keepBytes: Long) {
        var current = sizeBytes()
        if (current <= keepBytes) return
        // Phase 1: only evict DEBUG and INFO entries, protecting WARN/ERROR
        driver.execute(
            "DELETE FROM dsh_log_events WHERE seq IN (" +
                "SELECT seq FROM dsh_log_events WHERE level IN (0, 1) ORDER BY seq ASC LIMIT " +
                "(SELECT COUNT(*) / 2 FROM dsh_log_events WHERE level IN (0, 1))" +
                ")"
        )
        current = sizeBytes()
        if (current <= keepBytes) return
        // Phase 2: still over limit — evict oldest entries across all levels
        driver.execute(
            "DELETE FROM dsh_log_events WHERE seq IN (" +
                "SELECT seq FROM dsh_log_events ORDER BY seq ASC LIMIT " +
                "(SELECT COUNT(*) / 4 FROM dsh_log_events)" +
                ")"
        )
    }

    private fun mapEvent(s: SqlStatement): LogEvent {
        val levelValue = s.getColumnLong(2).toInt()
        return LogEvent(
            seq = s.getColumnLong(0),
            timestamp = s.getColumnLong(1),
            level = LogLevel.entries.firstOrNull { it.value == levelValue } ?: LogLevel.INFO,
            type = s.getColumnString(3),
            sessionId = nullableString(s, 4),
            rpcId = nullableString(s, 5),
            message = s.getColumnString(6),
            size = s.getColumnLong(7).toInt(),
        )
    }

    private fun <T> queryOne(sql: String, args: List<String?>, mapper: (SqlStatement) -> T): T? {
        val s = driver.prepare(sql)
        return try {
            args.forEachIndexed { i, v -> s.bindString(i + 1, v) }
            if (s.step()) mapper(s) else null
        } finally {
            s.close()
        }
    }

    private fun nullableString(s: SqlStatement, index: Int): String? =
        if (s.getColumnType(index) == ColumnType.NULL) null else s.getColumnString(index)

    private companion object {
        private const val INSERT_SQL = "INSERT OR REPLACE INTO dsh_log_events (seq, time, level, type, session_id, rpc_id, message, size) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    }
}

/**
 * Dummy schema so a second connection can open the same dsh.db without touching its versioning.
 * Must stay in sync with the main DshSchema version; when currentVersion equals this value,
 * DatabaseManager neither creates nor migrates (safe no-op).
 */
private object NoOpSchema : SqlSchema {
    override val version: Int = 7
    override fun create(driver: SqlDriver) = Unit
    override fun migrate(driver: SqlDriver, oldVersion: Int, newVersion: Int) = Unit
}
