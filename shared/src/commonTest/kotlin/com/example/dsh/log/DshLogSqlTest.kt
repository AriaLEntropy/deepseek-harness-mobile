package com.example.dsh.log

import kotlin.test.Test
import kotlin.test.assertEquals

class DshLogSqlTest {

    @Test
    fun literalEscapesQuotesAndNul() {
        assertEquals("NULL", DshLogSql.literal(null))
        assertEquals("'plain'", DshLogSql.literal("plain"))
        assertEquals("'it''s'", DshLogSql.literal("it's"))
        assertEquals("'a\\0b'", DshLogSql.literal("a\u0000b"))
    }

    @Test
    fun insertEventRendersTypedAndEscapedColumns() {
        val event = LogEvent(
            seq = 1,
            timestamp = 2,
            level = LogLevel.INFO,
            type = "turn/start",
            sessionId = null,
            rpcId = null,
            message = "msg",
            size = 3,
        )
        assertEquals(
            "INSERT INTO dsh_log_events (seq,time,level,type,session_id,rpc_id,message,size) " +
                "VALUES (1,2,1,'turn/start',NULL,NULL,'msg',3)",
            DshLogSql.insertEvent(event),
        )
    }

    @Test
    fun markCrashUpsertsSingleSlot() {
        assertEquals("INSERT OR REPLACE INTO dsh_log_state(slot,crash_id) VALUES(1,'c1')", DshLogSql.markCrash("c1"))
    }

    @Test
    fun checkedQueriesAppendSentinels() {
        assertEquals(
            "SELECT * FROM (S) UNION ALL SELECT NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL",
            DshLogSql.checkedSelect("S"),
        )
        assertEquals("SELECT * FROM (S) UNION ALL SELECT NULL", DshLogSql.checkedScalar("S"))
        assertEquals("SELECT * FROM (S) UNION ALL SELECT NULL,NULL", DshLogSql.checkedPairs("S"))
    }
}
