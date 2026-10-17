package com.example.dsh.bus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray

class BusProtocolTest {
    private val task = """
        {"id":"acp:my-agent:sess_7f3a2c","agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","source":"acp","connectorId":"my-agent","title":"整理发布说明","projectPath":"/Users/me/Projects/lesson-creator","projectName":"lesson-creator","status":"waitingApproval","lastMessage":"准备跑一遍测试","origin":"watch","controllable":true,"startedAt":"2026-09-26T08:00:00Z","updatedAt":"2026-09-26T08:02:00Z"}
    """.trim()

    private val badStatus = """
        {"id":"codex:bad","agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","source":"codex","title":"坏状态","projectPath":"/Users/me/Projects/x","projectName":"x","status":"sleeping","origin":"desktop","controllable":false,"startedAt":"2026-09-17T08:00:00Z","updatedAt":"2026-09-17T08:00:00Z"}
    """.trim()

    @Test
    fun aValidTaskRoundTripsKeyForKey() {
        val parsed = busParse(task)
        val cleaned = busTaskOrNull(parsed)
        assertEquals<kotlinx.serialization.json.JsonElement>(parsed, cleaned!!)
        assertEquals(parsed, busParse(busEncode(cleaned)))
    }

    @Test
    fun anUnknownTaskStatusRejectsThatRecordOnly() {
        assertNull(busTaskOrNull(busParse(badStatus)))
        val snapshot = busParse(
            """
            {"agents":[{"agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","name":"pc","platform":"windows","online":true,"lastSeenAt":"2026-10-16T00:00:00Z","appVersion":"1.0.0","connectors":[{"kind":"nope","displayName":"x","available":true,"enabled":true,"status":"ok","taskCount":0},{"kind":"dsh","displayName":"DSH","available":false,"enabled":true,"status":"degraded","taskCount":0}]}],"tasks":[$task,$badStatus],"recentResults":[],"recentMessages":[],"seq":3,"generatedAt":"2026-10-16T00:00:00Z"}
            """.trim(),
        )
        val cleaned = busCleanSnapshot(snapshot)!!
        val tasks = cleaned["tasks"] as JsonArray
        assertEquals(1, tasks.size)
        assertTrue(tasks.toString().contains("sess_7f3a2c"))
        assertTrue(!tasks.toString().contains("sleeping"))
        val connectors = cleaned["agents"].toString()
        assertTrue(connectors.contains("dsh"))
        assertTrue(!connectors.contains("nope"))
    }

    @Test
    fun describeRetriesThenStopsGuessing() {
        val attempts = DescribeAttempts()
        assertNull(attempts.onTransportFailure())
        assertNull(attempts.onHttp(500, ""))
        assertEquals(DescribeDecision.Unknown, attempts.onHttp(200, """{"hostKind":"something-else"}"""))
        val found = DescribeAttempts()
        val decision = found.onHttp(
            200,
            """{"protocolVersion":"3.9","agentId":"host-1","hostKind":"agent-host","forwardsDsh":false}""",
        )
        assertIs<DescribeDecision.AgentHost>(decision)
        assertEquals("host-1", decision.agentId)
        assertEquals(DescribeDecision.Plugin, DescribeAttempts().onHttp(404, ""))
    }

    @Test
    fun socketFallsBackToLongPollAndComesBackAfterAMinute() {
        val sync = BusSync()
        assertEquals("/bus/ws?since=-1", sync.socketPath())
        sync.onSocketFrame(
            """{"type":"snapshot","snapshot":{"agents":[],"tasks":[$task],"seq":4,"generatedAt":"2026-10-16T00:00:00Z"}}""",
        )
        assertEquals(4, sync.seq)
        assertEquals(1, sync.tasks().size)
        assertEquals("/bus/ws?since=4", sync.socketPath())
        sync.onSocketFrame(
            """{"type":"event","event":{"kind":"taskRemoved","taskId":"acp:my-agent:sess_7f3a2c"}}""",
        )
        assertEquals(0, sync.tasks().size)
        sync.onHandshakeFailure()
        assertEquals(BusChannel.WebSocket, sync.channel)
        sync.onHandshakeFailure()
        assertEquals(BusChannel.LongPoll, sync.channel)
        assertEquals("/bus/snapshot?since=4", sync.pollPath())
        assertTrue(!sync.onPollTick(59_000))
        assertTrue(sync.onPollTick(1_000))
        assertEquals(BusChannel.WebSocket, sync.channel)
        sync.onHandshakeOpen()
        assertEquals(0, sync.tasks().size)
    }
}
