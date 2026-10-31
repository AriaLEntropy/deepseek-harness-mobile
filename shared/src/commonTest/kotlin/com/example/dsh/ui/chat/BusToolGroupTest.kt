package com.example.dsh.ui.chat

import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BusToolGroupTest {

    private fun message(id: String, role: DshMessageRole, text: String = id) =
        DshMessage(id = id, role = role, content = text)

    private fun user(id: String) = message(id, DshMessageRole.USER)
    private fun agent(id: String) = message(id, DshMessageRole.ASSISTANT)
    private fun tool(id: String) = message(id, DshMessageRole.TOOL, "工具 $id")

    @Test
    fun twoConsecutiveToolsFoldIntoOneGroup() {
        val rows = busChatRows(listOf(user("u1"), tool("t1"), tool("t2"), agent("a1")))

        assertEquals(3, rows.size)
        assertIs<BusChatRow.Message>(rows[0])
        val group = assertIs<BusChatRow.ToolGroup>(rows[1])
        assertEquals(listOf("t1", "t2"), group.tools.map { it.id })
        assertEquals("2 次工具调用", busToolGroupLabel(group.tools.size))
        assertIs<BusChatRow.Message>(rows[2])
    }

    @Test
    fun threeConsecutiveToolsFoldIntoOneGroup() {
        val rows = busChatRows(listOf(tool("t1"), tool("t2"), tool("t3")))

        assertEquals(1, rows.size)
        val group = assertIs<BusChatRow.ToolGroup>(rows.single())
        assertEquals(3, group.tools.size)
        assertEquals("3 次工具调用", busToolGroupLabel(group.tools.size))
    }

    @Test
    fun mixedRolesKeepOrderAndSplitNonConsecutiveToolRuns() {
        val rows = busChatRows(
            listOf(
                user("u1"),
                tool("t1"),
                tool("t2"),
                agent("a1"),
                tool("t3"),
                user("u2"),
                agent("a2"),
            ),
        )

        assertEquals(6, rows.size)
        assertEquals("u1", (rows[0] as BusChatRow.Message).message.id)
        assertEquals(listOf("t1", "t2"), (rows[1] as BusChatRow.ToolGroup).tools.map { it.id })
        assertEquals("a1", (rows[2] as BusChatRow.Message).message.id)
        assertEquals("t3", (rows[3] as BusChatRow.Message).message.id)
        assertEquals("u2", (rows[4] as BusChatRow.Message).message.id)
        assertEquals("a2", (rows[5] as BusChatRow.Message).message.id)
    }

    @Test
    fun singleToolRendersAsNormalSummaryRow() {
        val rows = busChatRows(listOf(user("u1"), tool("t1"), agent("a1")))

        assertEquals(3, rows.size)
        assertEquals("t1", (rows[1] as BusChatRow.Message).message.id)
    }

    @Test
    fun groupKeyStaysStableWhenMoreToolsArrive() {
        val first = busChatRows(listOf(user("u1"), tool("t1"), tool("t2")))
        val second = busChatRows(listOf(user("u1"), tool("t1"), tool("t2"), tool("t3")))

        val firstKey = (first[1] as BusChatRow.ToolGroup).key
        val secondKey = (second[1] as BusChatRow.ToolGroup).key
        assertEquals(firstKey, secondKey)
        assertEquals(3, (second[1] as BusChatRow.ToolGroup).tools.size)
    }

    @Test
    fun expansionStateTogglesPerGroupKeyWithoutTouchingOthers() {
        val rows = busChatRows(
            listOf(tool("t1"), tool("t2"), agent("a1"), tool("t3"), tool("t4")),
        )
        val firstKey = (rows[0] as BusChatRow.ToolGroup).key
        val secondKey = (rows[2] as BusChatRow.ToolGroup).key

        var expanded = emptySet<String>()
        assertFalse(firstKey in expanded)

        expanded = toggleBusToolGroup(expanded, firstKey)
        assertTrue(firstKey in expanded)
        assertFalse(secondKey in expanded)

        expanded = toggleBusToolGroup(expanded, secondKey)
        assertTrue(firstKey in expanded)
        assertTrue(secondKey in expanded)

        expanded = toggleBusToolGroup(expanded, firstKey)
        assertFalse(firstKey in expanded)
        assertTrue(secondKey in expanded)
    }

    @Test
    fun emptyConversationHasNoRows() {
        assertTrue(busChatRows(emptyList()).isEmpty())
    }

    @Test
    fun twoHundredMessagesBuildRowsWithinBudget() {
        // 200 条消息 + 连续 tool 折叠：M2-11 的规模，只测纯模型，不依赖设备。
        val messages = List(200) { index ->
            if (index % 4 == 2 || index % 4 == 3) tool("t$index") else user("u$index")
        }
        var rows = emptyList<BusChatRow>()
        val elapsed = measureTimeMillis {
            repeat(10) { rows = busChatRows(messages) }
        }
        // 50 个工具组 + 100 个普通消息（每 4 条里 2 条 tool）。
        assertEquals(150, rows.size)
        assertTrue(elapsed < 2_000, "busChatRows(200) took ${elapsed}ms")
    }
}
