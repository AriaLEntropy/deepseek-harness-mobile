package com.example.dsh.infrastructure

import kotlin.test.*

class DshRpcLogTest {
    @Test
    fun completionAndDisconnectHaveExactlyOneTerminalEventWithCorrelation() {
        var time = 100L
        val events = mutableListOf<LogEvent>()
        val log = DshRpcLog({ time }) { level, type, message, sid, rid ->
            events.add(LogEvent(0, time, level, type, sid, rid, message, message.length))
        }
        log.start("r1", "session.rename", "s1")
        time += 25
        log.finish("r1", status = 200)
        log.finish("r1", "late-error")
        assertEquals(listOf("rpc.start", "rpc.complete"), events.map { it.type })
        assertTrue(events.last().message.contains("durationMs=25"))
        assertEquals("s1", events.last().sessionId)
        log.start("r2", "session.history", "s2")
        log.cancelAll("disconnected")
        log.finish("r2")
        assertEquals("rpc.failed", events.last().type)
        assertEquals("r2", events.last().rpcId)
        assertEquals(4, events.size)
    }
}
