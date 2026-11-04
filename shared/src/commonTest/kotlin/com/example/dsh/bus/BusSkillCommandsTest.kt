package com.example.dsh.bus

import com.example.dsh.ui.home.toHomeView
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 协议 3.10 的 fetchSkills / readSkill 序列化与缓存键 */
class BusSkillCommandsTest {
    @Test
    fun fetchSkillsForATaskCarriesTheSessionId() {
        val built = BusCommands.fetchSkills(
            commandId = "cmd-1",
            createdAt = "2026-11-03T00:00:00Z",
            agentId = "host-1",
            connector = "acp",
            connectorId = "qwen-code",
            projectPath = "/work/demo",
            taskId = "acp:qwen-code:s1",
        )
        val expected = """
            {"id":"cmd-1","createdAt":"2026-11-03T00:00:00Z","agentId":"host-1","kind":"fetchSkills","fetchSkills":{"connector":"acp","connectorId":"qwen-code","projectPath":"/work/demo","taskId":"acp:qwen-code:s1"}}
        """.trimIndent()
        assertEquals(busParse(expected).jsonObject, busParse(built).jsonObject)
    }

    @Test
    fun draftFetchSkillsOmitsConnectorIdAndTaskId() {
        val built = BusCommands.fetchSkills(
            commandId = "cmd-2",
            createdAt = "2026-11-03T00:00:00Z",
            agentId = "host-1",
            connector = "claude",
            connectorId = null,
            projectPath = "",
            taskId = "",
        )
        val payload = busParse(built).jsonObject.payload("fetchSkills")
        assertEquals("claude", payload.string("connector"))
        assertTrue("connectorId" !in payload)
        assertTrue("taskId" !in payload)
        assertEquals("", payload.string("projectPath"))
    }

    @Test
    fun readSkillCarriesTheSameContextAndSkillId() {
        val built = BusCommands.readSkill(
            commandId = "cmd-3",
            createdAt = "2026-11-03T00:00:00Z",
            agentId = "host-1",
            connector = "acp",
            connectorId = "qwen-code",
            projectPath = "/work/demo",
            taskId = "acp:qwen-code:s1",
            skillId = "ac_abc",
        )
        val payload = busParse(built).jsonObject.payload("readSkill")
        assertEquals("acp", payload.string("connector"))
        assertEquals("qwen-code", payload.string("connectorId"))
        assertEquals("ac_abc", payload.string("skillId"))
        assertEquals("acp:qwen-code:s1", payload.string("taskId"))
    }

    @Test
    fun skillListKeyUsesTaskForTasksAndProjectForDrafts() {
        assertEquals(
            "task|acp|qwen-code|acp:qwen-code:s1",
            busSkillListKey("acp", "qwen-code", "/work/demo", "acp:qwen-code:s1"),
        )
        assertEquals(
            "project|claude||/work/demo",
            busSkillListKey("claude", null, "/work/demo", ""),
        )
    }

    @Test
    fun canListSkillsIsParsedFromTheSnapshot() {
        val store = BusStore()
        store.applySnapshot(
            busParse(
                """
                {"agents":[{"agentId":"host-1","name":"pc","platform":"windows","online":true,"lastSeenAt":"2026-11-03T00:00:00Z","appVersion":"1.0.0","connectors":[{"kind":"acp","connectorId":"qwen-code","displayName":"Qwen","available":true,"enabled":true,"status":"ok","taskCount":0,"canListSkills":true}]}],"tasks":[],"projects":[],"recentResults":[],"seq":1,"generatedAt":"2026-11-03T00:00:00Z"}
                """.trimIndent(),
            ),
        )
        assertEquals(true, store.toHomeView().agents.first().canListSkills)
    }

    @Test
    fun skillEventsAreCachedByContextAndSkillId() {
        val store = BusStore()
        store.applyEvent(
            busParse(
                """
                {"kind":"skillList","skillList":{"commandId":"cmd-1","agentId":"host-1","connector":"acp","connectorId":"qwen-code","projectPath":"/work/demo","taskId":"acp:qwen-code:s1","revision":"r1","skills":[{"id":"ac_1","name":"release","description":"ship","kind":"command","source":"session","hasBody":false}],"truncated":false,"fetchedAt":"2026-11-03T00:00:00Z"}}
                """.trimIndent(),
            ),
        )
        val list = store.skillList("task|acp|qwen-code|acp:qwen-code:s1")
        assertNotNull(list)
        assertEquals("release", (list["skills"] as kotlinx.serialization.json.JsonArray).first().jsonObject.string("name"))

        store.applyEvent(
            busParse(
                """
                {"kind":"skillContent","skillContent":{"commandId":"cmd-2","agentId":"host-1","skillId":"ac_1","name":"release","contentType":"text/markdown","body":"# Release","truncated":false,"fetchedAt":"2026-11-03T00:00:00Z"}}
                """.trimIndent(),
            ),
        )
        assertEquals("# Release", store.skillContent("ac_1")?.string("body"))
    }
}

private fun JsonObject.payload(key: String): JsonObject = this[key] as JsonObject

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
