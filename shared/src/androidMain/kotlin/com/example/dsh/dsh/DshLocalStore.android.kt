package com.example.dsh.dsh

import net.shantu.kuiklysqlite.ColumnType
import net.shantu.kuiklysqlite.DatabaseManager
import net.shantu.kuiklysqlite.SqlDriver
import net.shantu.kuiklysqlite.SqlSchema
import net.shantu.kuiklysqlite.SqlStatement

internal actual fun createDshLocalStore(path: String): DshLocalStore = DshSqliteStore(path)

private class DshSqliteStore(path: String) : DshLocalStore {
    private val driver: SqlDriver by lazy {
        DatabaseManager(path, DshSchema).driver
    }

    override fun loadApiKey(): String = queryOne(
        "SELECT value FROM dsh_settings WHERE key = ?",
        listOf("deepseek_api_key"),
    ) { it.getColumnString(0) }.orEmpty()

    override fun saveApiKey(apiKey: String) {
        execute(
            "INSERT OR REPLACE INTO dsh_settings (key, value) VALUES (?, ?)",
            listOf("deepseek_api_key", apiKey),
        )
    }

    override fun loadSessions(): List<DshSession> = query(
        "SELECT id, title, workspace, updated_label, running FROM dsh_sessions ORDER BY updated_at DESC",
        emptyList(),
    ) { statement ->
        DshSession(
            id = statement.getColumnString(0),
            title = statement.getColumnString(1),
            workspace = statement.getColumnString(2),
            updatedLabel = statement.getColumnString(3),
            running = statement.getColumnLong(4) != 0L,
        )
    }

    override fun saveSessions(sessions: List<DshSession>) {
        driver.transaction {
            driver.execute("DELETE FROM dsh_sessions")
            sessions.forEach { session ->
                execute(
                    "INSERT OR REPLACE INTO dsh_sessions " +
                        "(id, title, workspace, updated_label, running, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    listOf(
                        session.id,
                        session.title,
                        session.workspace,
                        session.updatedLabel,
                        if (session.running) "1" else "0",
                        currentTimeMillis().toString(),
                    ),
                )
            }
        }
    }

    override fun loadMessages(sessionId: String): List<DshMessage> = query(
        "SELECT id, role, content, streaming, tool_name, hidden FROM dsh_messages " +
            "WHERE session_id = ? ORDER BY seq ASC",
        listOf(sessionId),
    ) { statement ->
        DshMessage(
            id = statement.getColumnString(0),
            role = DshMessageRole.valueOf(statement.getColumnString(1)),
            content = statement.getColumnString(2),
            streaming = statement.getColumnLong(3) != 0L,
            toolName = nullableString(statement, 4),
            hidden = statement.getColumnLong(5) != 0L,
        )
    }

    override fun saveMessages(sessionId: String, messages: List<DshMessage>) {
        driver.transaction {
            execute("DELETE FROM dsh_messages WHERE session_id = ?", listOf(sessionId))
            messages.forEachIndexed { index, message ->
                execute(
                    "INSERT OR REPLACE INTO dsh_messages " +
                        "(id, session_id, role, content, streaming, tool_name, hidden, seq) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    listOf(
                        message.id,
                        sessionId,
                        message.role.name,
                        message.content,
                        if (message.streaming) "1" else "0",
                        message.toolName,
                        if (message.hidden) "1" else "0",
                        index.toString(),
                    ),
                )
            }
        }
    }

    private fun execute(sql: String, args: List<String?>) {
        val statement = driver.prepare(sql)
        try {
            bind(statement, args)
            statement.step()
        } finally {
            statement.close()
        }
    }

    private fun <T> query(sql: String, args: List<String?>, mapper: (SqlStatement) -> T): List<T> {
        val statement = driver.prepare(sql)
        return try {
            bind(statement, args)
            buildList {
                while (statement.step()) add(mapper(statement))
            }
        } finally {
            statement.close()
        }
    }

    private fun <T> queryOne(sql: String, args: List<String?>, mapper: (SqlStatement) -> T): T? =
        query(sql, args, mapper).firstOrNull()

    private fun bind(statement: SqlStatement, args: List<String?>) {
        args.forEachIndexed { index, value -> statement.bindString(index + 1, value) }
    }

    private fun nullableString(statement: SqlStatement, index: Int): String? =
        if (statement.getColumnType(index) == ColumnType.NULL) null else statement.getColumnString(index)

    private fun currentTimeMillis(): Long = System.currentTimeMillis()
}

private object DshSchema : SqlSchema {
    override val version: Int = 1

    override fun create(driver: SqlDriver) {
        driver.execute(
            "CREATE TABLE IF NOT EXISTS dsh_settings (" +
                "key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        )
        driver.execute(
            "CREATE TABLE IF NOT EXISTS dsh_sessions (" +
                "id TEXT PRIMARY KEY, title TEXT NOT NULL, workspace TEXT NOT NULL, " +
                "updated_label TEXT NOT NULL, running INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
        )
        driver.execute(
            "CREATE TABLE IF NOT EXISTS dsh_messages (" +
                "id TEXT PRIMARY KEY, session_id TEXT NOT NULL, role TEXT NOT NULL, " +
                "content TEXT NOT NULL, streaming INTEGER NOT NULL, tool_name TEXT, " +
                "hidden INTEGER NOT NULL, seq INTEGER NOT NULL)",
        )
        driver.execute("CREATE INDEX IF NOT EXISTS idx_dsh_messages_session ON dsh_messages(session_id, seq)")
    }

    override fun migrate(driver: SqlDriver, oldVersion: Int, newVersion: Int) = Unit
}
