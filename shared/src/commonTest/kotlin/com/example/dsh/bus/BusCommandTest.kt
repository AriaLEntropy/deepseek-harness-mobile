package com.example.dsh.bus

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusCommandTest {
    private val fixture = """
        {"id":"5f2f3a1c-7f2e-4f3a-9a11-2b6d0c1e7a90","createdAt":"2026-09-20T15:40:00Z","agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","kind":"fetchMessages","fetchMessages":{"taskId":"codex:thr_01JB8M0Q4R","limit":40}}
    """.trimIndent()

    @Test
    fun fetchMessagesMatchesTheFixtureKeyForKey() {
        val built = BusCommands.fetchMessages(
            commandId = "5f2f3a1c-7f2e-4f3a-9a11-2b6d0c1e7a90",
            createdAt = "2026-09-20T15:40:00Z",
            agentId = "hV3nQ7pLxK2mR8sTfW4bZQ",
            taskId = "codex:thr_01JB8M0Q4R",
            limit = 40,
        )
        assertEquals(busParse(fixture).jsonObject, busParse(built).jsonObject)
    }

    @Test
    fun trackerExpiresAndRetriesWithTheSameId() {
        var now = 0L
        val tracker = BusCommandTracker(nowMs = { now }, timeoutMs = 1_000)
        tracker.track("cmd-1")
        assertFalse(tracker.isExpired("cmd-1"))

        now = 1_000
        assertTrue(tracker.isExpired("cmd-1"))
        assertEquals(listOf("cmd-1"), tracker.expired())

        tracker.retry("cmd-1")
        assertFalse(tracker.isExpired("cmd-1"))

        tracker.onResult("cmd-1")
        assertFalse(tracker.isExpired("cmd-1"))
        assertTrue(tracker.expired().isEmpty())
    }
}
