package com.example.dsh.bus

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BusCommandSenderTest {
    private class FakeClock(var now: Long = 0L)

    private class FakeTransport(
        private val clock: FakeClock,
        var ack: BusCommandAck = BusCommandAck(202),
        var snapshot: String? = null,
    ) : BusCommandTransport {
        val sent = mutableListOf<String>()
        var schedules = 0

        override fun send(json: String, onAck: (BusCommandAck) -> Unit) {
            sent += json
            onAck(ack)
        }

        override fun snapshotJson(): String? = snapshot

        override fun schedule(delayMs: Long, action: () -> Unit) {
            schedules += 1
            clock.now += delayMs
            action()
        }
    }

    private fun resultJson(commandId: String, ok: Boolean, taskId: String = "", error: String = ""): String {
        val extra = buildString {
            if (taskId.isNotBlank()) append(",\"taskId\":\"$taskId\"")
            if (error.isNotBlank()) append(",\"error\":\"$error\"")
        }
        return """{"recentResults":[{"commandId":"$commandId","ok":$ok$extra}]}"""
    }

    private fun sender(
        transport: FakeTransport,
        clock: FakeClock,
        id: String = "cmd-1",
        createdAt: String = "2026-10-30T00:00:00Z",
        onNewId: () -> Unit = {},
        onNewCreatedAt: () -> Unit = {},
        tracker: BusCommandTracker = BusCommandTracker(nowMs = { clock.now }),
    ): BusCommandSender = BusCommandSender(
        transport = transport,
        tracker = tracker,
        pollIntervalMs = 300,
        newId = { onNewId(); id },
        newCreatedAt = { onNewCreatedAt(); createdAt },
    )

    private fun BusCommandSender.run(command: BusCommand): BusSendResult {
        var result: BusSendResult? = null
        send(command) { result = it }
        return requireNotNull(result)
    }

    @Test
    fun exactlySixteenKiBIsSentButOneByteMoreIsRejectedLocally() {
        val clock = FakeClock()
        val tracker = BusCommandTracker(nowMs = { clock.now })

        val exactTransport = FakeTransport(
            clock = clock,
            ack = BusCommandAck(202),
            snapshot = resultJson("cmd-exact", ok = true, taskId = "codex:s1"),
        )
        val exactSender = sender(exactTransport, clock, id = "cmd-exact", tracker = tracker)
        val exact = exactSender.prepare { _, _ -> "x".repeat(BUS_COMMAND_MAX_BYTES) }
        val exactResult = exactSender.run(exact)
        assertIs<BusSendResult.Confirmed>(exactResult)
        assertEquals(1, exactTransport.sent.size)

        val overTransport = FakeTransport(clock)
        val overSender = sender(overTransport, clock, id = "cmd-over", tracker = tracker)
        val over = overSender.prepare { _, _ -> "x".repeat(BUS_COMMAND_MAX_BYTES + 1) }
        val overResult = overSender.run(over)
        assertIs<BusSendResult.TooLong>(overResult)
        assertEquals(BUS_COMMAND_TOO_LONG, overResult.message)
        assertTrue(overTransport.sent.isEmpty())
        assertEquals(0, overTransport.schedules)
        assertFalse(tracker.isTracking("cmd-over"))
    }

    @Test
    fun retryReusesTheSameIdAndCreatedAt() {
        val clock = FakeClock()
        val transport = FakeTransport(clock, ack = BusCommandAck(0))
        var idCalls = 0
        var createdCalls = 0
        val sender = sender(
            transport = transport,
            clock = clock,
            id = "cmd-retry",
            createdAt = "2026-10-30T01:02:03Z",
            onNewId = { idCalls += 1 },
            onNewCreatedAt = { createdCalls += 1 },
        )
        val command = sender.prepare { id, createdAt ->
            BusCommands.interrupt(id, createdAt, agentId = "agent-1", taskId = "task-1")
        }

        val first = sender.run(command)
        assertIs<BusSendResult.Failed>(first)
        assertTrue(first.retryable)

        val second = sender.run(command)
        assertIs<BusSendResult.Failed>(second)

        assertEquals(1, idCalls)
        assertEquals(1, createdCalls)
        assertEquals(2, transport.sent.size)
        val ids = transport.sent.map { busParse(it).jsonObject["id"]?.jsonPrimitive?.content }
        val createdAts = transport.sent.map { busParse(it).jsonObject["createdAt"]?.jsonPrimitive?.content }
        assertEquals(listOf("cmd-retry", "cmd-retry"), ids)
        assertEquals(listOf("2026-10-30T01:02:03Z", "2026-10-30T01:02:03Z"), createdAts)
    }

    @Test
    fun networkFailureReturnsImmediatelyWithoutEnteringWaiting() {
        val clock = FakeClock()
        val transport = FakeTransport(clock, ack = BusCommandAck(0, "offline"))
        val tracker = BusCommandTracker(nowMs = { clock.now })
        val sender = sender(transport, clock, tracker = tracker)
        val command = sender.prepare { id, createdAt ->
            BusCommands.followUp(id, createdAt, "agent-1", "task-1", "hello")
        }

        val result = sender.run(command)

        assertIs<BusSendResult.Failed>(result)
        assertEquals(0, result.status)
        assertTrue(result.retryable)
        assertEquals(1, transport.sent.size)
        assertEquals(0, transport.schedules)
        assertFalse(tracker.isTracking(command.id))
    }

    @Test
    fun unrelatedResultsKeepWaitingThenTimeoutAfterSixtySeconds() {
        val clock = FakeClock()
        val transport = FakeTransport(
            clock = clock,
            ack = BusCommandAck(202),
            snapshot = resultJson("someone-else", ok = true, taskId = "codex:other"),
        )
        val tracker = BusCommandTracker(nowMs = { clock.now }, timeoutMs = 60_000)
        val sender = sender(transport, clock, tracker = tracker)
        val command = sender.prepare { id, createdAt ->
            BusCommands.followUp(id, createdAt, "agent-1", "task-1", "hello")
        }

        val result = sender.run(command)

        assertIs<BusSendResult.Unconfirmed>(result)
        assertEquals(BUS_COMMAND_UNCONFIRMED, result.message)
        assertTrue(result.retryable)
        assertTrue(clock.now >= 60_000)
        assertTrue(transport.schedules > 0)
        assertFalse(tracker.isTracking(command.id))
    }

    @Test
    fun matchingResultClearsTheTracker() {
        val clock = FakeClock()
        val transport = FakeTransport(
            clock = clock,
            ack = BusCommandAck(202),
            snapshot = resultJson("cmd-ok", ok = true, taskId = "codex:s1"),
        )
        val tracker = BusCommandTracker(nowMs = { clock.now })
        val sender = sender(transport, clock, id = "cmd-ok", tracker = tracker)
        val command = sender.prepare { id, createdAt ->
            BusCommands.startTask(
                commandId = id,
                createdAt = createdAt,
                agentId = "agent-1",
                source = "codex",
                connectorId = null,
                projectPath = "/tmp/project",
                prompt = "hello",
            )
        }

        val result = sender.run(command)

        assertIs<BusSendResult.Confirmed>(result)
        assertTrue(result.ok)
        assertEquals("codex:s1", result.taskId)
        assertFalse(tracker.isTracking(command.id))
        assertEquals(0, transport.schedules)
    }

    @Test
    fun hostFailureResultAlsoClearsTheTracker() {
        val clock = FakeClock()
        val transport = FakeTransport(
            clock = clock,
            ack = BusCommandAck(202),
            snapshot = resultJson("cmd-fail", ok = false, error = "会话不存在"),
        )
        val tracker = BusCommandTracker(nowMs = { clock.now })
        val sender = sender(transport, clock, id = "cmd-fail", tracker = tracker)
        val command = sender.prepare { id, createdAt ->
            BusCommands.deleteTask(id, createdAt, "agent-1", "task-1")
        }

        val result = sender.run(command)

        assertIs<BusSendResult.Confirmed>(result)
        assertFalse(result.ok)
        assertEquals("会话不存在", result.error)
        assertFalse(tracker.isTracking(command.id))
    }
}
