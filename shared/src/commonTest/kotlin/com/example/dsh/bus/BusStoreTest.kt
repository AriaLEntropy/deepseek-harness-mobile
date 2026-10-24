package com.example.dsh.bus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BusStoreTest {
    private val snapshot = """
        {
          "agents":[{"agentId":"host-1","name":"PC","platform":"windows","online":true,"lastSeenAt":"2026-10-24T00:00:00Z","appVersion":"1.0.0","connectors":[{"kind":"acp","connectorId":"claude-code","displayName":"Claude Code","available":true,"enabled":true,"status":"ok","taskCount":1}]}],
          "tasks":[
            {"id":"acp:claude-code:s1","agentId":"host-1","source":"acp","connectorId":"claude-code","title":"ok","projectPath":"C:\\work","projectName":"work","status":"running","origin":"watch","controllable":true,"startedAt":"2026-10-24T00:00:00Z","updatedAt":"2026-10-24T00:01:00Z"},
            {"id":"unknown:1","agentId":"host-1","source":"unknown","title":"bad","projectPath":"C:\\work","projectName":"work","status":"running","origin":"watch","controllable":true,"startedAt":"2026-10-24T00:00:00Z","updatedAt":"2026-10-24T00:01:00Z"}
          ],
          "projects":[],
          "recentResults":[],
          "seq":5,
          "generatedAt":"2026-10-24T00:01:00Z"
        }
    """.trimIndent()

    @Test
    fun snapshotKeepsOnlyValidTasks() {
        val store = BusStore()
        val receipt = store.applySnapshot(busParse(snapshot))
        assertEquals(5, receipt?.seq)
        assertEquals(1, receipt?.taskCount)
        assertEquals(5, store.seq)
        assertEquals(1, store.agents.size)
        assertEquals(1, store.tasks.size)
        assertTrue(store.taskById("acp:claude-code:s1") != null)
        assertNull(store.taskById("unknown:1"))
    }

    @Test
    fun describePluginCreatesDshOnlyHost() {
        val store = BusStore()
        store.bindDescribe(DescribeDecision.Plugin)
        assertEquals(BusHostKind.DshPlugin, store.host?.kind)
        assertEquals(1, store.agents.size)
        assertTrue(store.agents.first().toString().contains("\"kind\":\"dsh\""))
    }

    @Test
    fun describeAgentHostKeepsIdentity() {
        val store = BusStore()
        store.bindDescribe(DescribeDecision.AgentHost("host-1", "3.9", forwardsDsh = true))
        assertEquals(BusHostKind.AgentHost, store.host?.kind)
        assertEquals("host-1", store.host?.agentId)
        assertEquals("3.9", store.host?.protocolVersion)
        assertTrue(store.host?.forwardsDsh == true)
    }

    @Test
    fun eventsReplaceRemovedAndResultEntries() {
        val store = BusStore()
        store.applySnapshot(busParse(snapshot))

        val updated = """
            {"kind":"taskUpdated","task":{"id":"acp:claude-code:s1","agentId":"host-1","source":"acp","connectorId":"claude-code","title":"ok","projectPath":"C:\\work","projectName":"work","status":"completed","origin":"watch","controllable":true,"startedAt":"2026-10-24T00:00:00Z","updatedAt":"2026-10-24T00:02:00Z"}}
        """.trimIndent()
        store.applyEvent(busParse(updated))
        assertEquals(1, store.tasks.size)
        assertTrue(store.taskById("acp:claude-code:s1").toString().contains("\"status\":\"completed\""))

        val removed = """{"kind":"taskRemoved","taskId":"acp:claude-code:s1"}"""
        store.applyEvent(busParse(removed))
        assertEquals(0, store.tasks.size)

        val result = """
            {"kind":"commandResult","commandResult":{"commandId":"cmd-1","ok":true,"finishedAt":"2026-10-24T00:03:00Z"}}
        """.trimIndent()
        store.applyEvent(busParse(result))
        store.applyEvent(busParse(result))
        assertEquals(1, store.recentResults.size)
    }

    @Test
    fun unknownDescribeDoesNotResetState() {
        val store = BusStore()
        store.applySnapshot(busParse(snapshot))
        store.bindDescribe(DescribeDecision.Unknown)
        assertNull(store.host)
        assertEquals(1, store.agents.size)
    }
}
