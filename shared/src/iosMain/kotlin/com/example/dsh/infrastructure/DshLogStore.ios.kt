package com.example.dsh.infrastructure

import net.shantu.kuiklysqlite.ColumnType
import net.shantu.kuiklysqlite.DatabaseManager
import net.shantu.kuiklysqlite.SqlDriver
import net.shantu.kuiklysqlite.SqlSchema
import net.shantu.kuiklysqlite.SqlStatement

internal actual fun createDshLogStore(path: String): DshLogStore = DshIosLogStore(path)

private class DshIosLogStore(private val path: String) : DshLogStore {
    private val driver: SqlDriver by lazy {
        DatabaseManager(path, NoOpSchema).driver
    }

    init {
        driver.execute(DshLogSql.CREATE_TABLE)
        driver.execute(DshLogSql.CREATE_INDEX_TIME)
        driver.execute(DshLogSql.CREATE_INDEX_TYPE)
        driver.execute(DshLogSql.CREATE_INDEX_SESSION)
        driver.execute(DshLogSql.CREATE_STATE)
    }

    override fun appendBatch(events: List<LogEvent>) {
        if (events.isEmpty()) return
        driver.transaction {
            for (event in events) {
                driver.execute(DshLogSql.insertEvent(event))
            }
        }
    }

    override fun query(filter: LogFilter, limit: Int, offset: Int): List<LogEvent> {
        val select = buildLogSelect(filter, limit, offset)
        val s = driver.prepare(DshLogSql.checkedSelect(select.sql))
        return try {
            select.args.forEachIndexed { i, v -> s.bindString(i + 1, v) }
            buildList {
                var complete = false
                while (s.step()) {
                    if (s.getColumnType(0) == ColumnType.NULL) { complete = true; break }
                    add(mapEvent(s))
                }
                check(complete) { "日志查询未完成" }
            }
        } finally {
            s.close()
        }
    }

    override fun clear() = clearAndMarkCrash(null)
    override fun clearAndMarkCrash(id: String?) {
        driver.transaction {
            driver.execute(DshLogSql.CLEAR)
            id?.let { driver.execute(DshLogSql.markCrash(it)) }
        }
    }
    override fun appendCrashOnce(id: String, event: LogEvent): Boolean = driver.transaction {
        val previous = queryOne(DshLogSql.LAST_CRASH, emptyList()) { it.getColumnString(0) }
            ?: error("无法读取崩溃导入标记")
        if (previous == id) false else {
            driver.execute(DshLogSql.insertEvent(event))
            driver.execute(DshLogSql.markCrash(id))
            true
        }
    }
    override fun close() = driver.close()

    override fun maxSeq(): Long =
        queryOne(DshLogSql.MAX_SEQ, emptyList()) { it.getColumnLong(0) } ?: error("无法读取日志序号")

    override fun diskBytes(): Long {
        return databaseDiskBytes(path)
    }

    override fun count(): Long =
        queryOne(DshLogSql.COUNT_ALL, emptyList()) { it.getColumnLong(0) } ?: error("无法读取日志数量")

    override fun countLowLevel(): Long =
        queryOne(DshLogSql.COUNT_LOW_LEVEL, emptyList()) { it.getColumnLong(0) } ?: error("无法读取日志数量")

    override fun deleteOldest(limit: Int, lowLevelOnly: Boolean): Int {
        val sql = if (lowLevelOnly) DshLogSql.DELETE_OLDEST_LOW_LEVEL else DshLogSql.DELETE_OLDEST_ANY
        driver.execute(sql.replace("?", limit.coerceAtLeast(0).toString()))
        return driver.getChanges()
    }

    override fun compact() {
        driver.execute(DshLogSql.VACUUM)
        check((queryOne(DshLogSql.CHECKPOINT, emptyList()) { it.getColumnLong(0) } ?: error("日志 WAL 回收失败")) == 0L) { "日志 WAL 回收忙，稍后重试" }
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
