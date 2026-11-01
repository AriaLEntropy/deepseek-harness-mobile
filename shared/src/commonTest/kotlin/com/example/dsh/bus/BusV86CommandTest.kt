package com.example.dsh.bus

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 审批回答与 Agent 开关/重启命令的序列化断言。
 * 对得上的目标是 botbus-protocol/protocol-fixtures/plain 里的明文 fixture：
 * 键一个不多、一个不少，可选字段该省略时必须省略。
 */
class BusV86CommandTest {

    @Test
    fun approveWithAnswersMatchesThePlainFixture() {
        val fixture = """
            {"id":"c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f","createdAt":"2026-09-26T07:46:00Z","agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","kind":"approve","approve":{"taskId":"claude:3f9a1c2e-5d6b-4e7f-8a90-b1c2d3e4f5a6","requestId":"toolu_01ExampleAskQuestion","decision":"allow","answers":{"0":["发新版"],"1":["手机","手表"]}}}
        """.trimIndent()
        val built = BusCommands.approve(
            commandId = "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f",
            createdAt = "2026-09-26T07:46:00Z",
            agentId = "hV3nQ7pLxK2mR8sTfW4bZQ",
            taskId = "claude:3f9a1c2e-5d6b-4e7f-8a90-b1c2d3e4f5a6",
            requestId = "toolu_01ExampleAskQuestion",
            allow = true,
            answers = mapOf("0" to listOf("发新版"), "1" to listOf("手机", "手表")),
        )
        assertEquals(busParse(fixture).jsonObject, busParse(built).jsonObject)
    }

    @Test
    fun approveWithoutAnswersKeepsThePre214Shape() {
        val fixture = """
            {"id":"9f8e7d6c-5b4a-4392-8170-6f5e4d3c2b1a","createdAt":"2026-09-17T08:03:00Z","agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","kind":"approve","approve":{"taskId":"codex:01a0b000-0000-7000-8000-000000000001","requestId":"req-7f3a","decision":"allow"}}
        """.trimIndent()
        val built = BusCommands.approve(
            commandId = "9f8e7d6c-5b4a-4392-8170-6f5e4d3c2b1a",
            createdAt = "2026-09-17T08:03:00Z",
            agentId = "hV3nQ7pLxK2mR8sTfW4bZQ",
            taskId = "codex:01a0b000-0000-7000-8000-000000000001",
            requestId = "req-7f3a",
            allow = true,
        )
        assertEquals(busParse(fixture).jsonObject, busParse(built).jsonObject)
    }

    @Test
    fun answersAreOmittedOnDenyAndWhenEmptyOrBlank() {
        val deny = busParse(
            BusCommands.approve(
                commandId = "cmd-1", createdAt = "2026-09-26T07:46:00Z", agentId = "agent-1",
                taskId = "task-1", requestId = "req-1", allow = false,
                answers = mapOf("0" to listOf("只这一次")),
            ),
        ).jsonObject
        assertEquals("deny", deny.payload("approve").string("decision"))
        assertTrue("answers" !in deny.payload("approve"))

        val empty = busParse(
            BusCommands.approve(
                commandId = "cmd-2", createdAt = "2026-09-26T07:46:00Z", agentId = "agent-1",
                taskId = "task-1", requestId = "req-1", allow = true,
                answers = mapOf("" to listOf("x"), "1" to emptyList()),
            ),
        ).jsonObject
        assertTrue("answers" !in empty.payload("approve"))
    }

    @Test
    fun setConnectorEnabledMatchesThePlainFixtures() {
        val codex = """
            {"id":"5e4d3c2b-1a09-4f8e-8d7c-6b5a49382716","createdAt":"2026-09-17T08:25:00Z","agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","kind":"setConnectorEnabled","setConnectorEnabled":{"connector":"codex","enabled":false}}
        """.trimIndent()
        val built = BusCommands.setConnectorEnabled(
            commandId = "5e4d3c2b-1a09-4f8e-8d7c-6b5a49382716",
            createdAt = "2026-09-17T08:25:00Z",
            agentId = "hV3nQ7pLxK2mR8sTfW4bZQ",
            connector = "codex",
            enabled = false,
        )
        assertEquals(busParse(codex).jsonObject, busParse(built).jsonObject)

        val acp = """
            {"id":"8b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e","createdAt":"2026-09-26T08:05:00Z","agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","kind":"setConnectorEnabled","setConnectorEnabled":{"connector":"acp","connectorId":"gemini","enabled":false}}
        """.trimIndent()
        val builtAcp = BusCommands.setConnectorEnabled(
            commandId = "8b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e",
            createdAt = "2026-09-26T08:05:00Z",
            agentId = "hV3nQ7pLxK2mR8sTfW4bZQ",
            connector = "acp",
            enabled = false,
            connectorId = "gemini",
        )
        assertEquals(busParse(acp).jsonObject, busParse(builtAcp).jsonObject)
    }

    @Test
    fun restartConnectorMatchesThePlainFixture() {
        val fixture = """
            {"id":"9d3e4f50-6172-4c83-9d94-b5c6d7e8f902","createdAt":"2026-10-04T09:00:00Z","agentId":"hV3nQ7pLxK2mR8sTfW4bZQ","kind":"restartConnector","restartConnector":{"connector":"acp","connectorId":"gemini"}}
        """.trimIndent()
        val built = BusCommands.restartConnector(
            commandId = "9d3e4f50-6172-4c83-9d94-b5c6d7e8f902",
            createdAt = "2026-10-04T09:00:00Z",
            agentId = "hV3nQ7pLxK2mR8sTfW4bZQ",
            connector = "acp",
            connectorId = "gemini",
        )
        assertEquals(busParse(fixture).jsonObject, busParse(built).jsonObject)
    }

    @Test
    fun restartConnectorOmitsConnectorIdForNonAcp() {
        val built = busParse(
            BusCommands.restartConnector(
                commandId = "cmd-3", createdAt = "2026-10-04T09:00:00Z", agentId = "agent-1",
                connector = "codex", connectorId = null,
            ),
        ).jsonObject
        assertEquals("restartConnector", built.string("kind"))
        val payload = built.payload("restartConnector")
        assertEquals("codex", payload.string("connector"))
        assertTrue("connectorId" !in payload)
    }

    @Test
    fun connectorSizingStaysWithinTheCommandBudget() {
        val command = BusCommand(
            id = "cmd-4",
            createdAt = "2026-10-04T09:00:00Z",
            json = BusCommands.approve(
                commandId = "cmd-4",
                createdAt = "2026-10-04T09:00:00Z",
                agentId = "agent-1",
                taskId = "task-1",
                requestId = "req-1",
                allow = true,
                answers = mapOf("0" to listOf("只这一次"), "1" to listOf("手机", "手表")),
            ),
        )
        assertTrue(!command.tooLong)
    }

    @Test
    fun agentKeySplitsIntoConnectorTarget() {
        assertEquals("acp" to "gemini", busConnectorTarget("acp:gemini"))
        assertEquals("codex" to null, busConnectorTarget("codex"))
        assertEquals("dsh" to null, busConnectorTarget("dsh"))
        // acp 前缀但没有 id：kind 仍为 acp，id 为空（协议会拒绝，交给调用方兜底）。
        assertEquals("acp" to null, busConnectorTarget("acp:"))
        assertNull(busConnectorTarget("hermes").second)
    }

    @Test
    fun restartCapabilityGatesTheRestartAction() {
        assertTrue(busCanRestartConnector(canRestartConnectors = true))
        assertTrue(!busCanRestartConnector(canRestartConnectors = false))
        assertTrue(BUS_RESTART_CONNECTOR_CONFIRM_TITLE.isNotBlank())
        assertTrue(BUS_RESTART_CONNECTOR_CONFIRM_MESSAGE.contains("中断") ||
            BUS_RESTART_CONNECTOR_CONFIRM_MESSAGE.contains("停"),
        )
    }

    /** 取出与 kind 同名的 payload 对象。 */
    private fun JsonObject.payload(kind: String): JsonObject = this[kind] as JsonObject

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}