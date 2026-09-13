package com.example.dsh.export

import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.example.dsh.export.DshExportSelection

class DshExportSelectionTest {

    private fun user(id: String) =
        DshMessage(id = id, role = DshMessageRole.USER, content = "prompt-$id")

    private fun assistant(id: String, streaming: Boolean = false, hidden: Boolean = false) =
        DshMessage(id = id, role = DshMessageRole.ASSISTANT, content = "answer-$id", streaming = streaming, hidden = hidden)

    private val turn = listOf(user("u1"), assistant("a1"))

    @Test
    fun groupsPairPromptWithSettledReply() {
        val groups = DshExportSelection.groups(turn)
        assertEquals(1, groups.size)
        assertEquals("u:u1", groups[0].key)
        assertEquals(listOf("u1", "a1"), groups[0].selectableIds)
    }

    @Test
    fun selectedMessagesReturnsOnlyChosenGroups() {
        val key = DshExportSelection.groups(turn)[0].key
        assertEquals(listOf("u1", "a1"), DshExportSelection.selectedMessages(turn, setOf(key)).map { it.id })
        assertTrue(DshExportSelection.selectedMessages(turn, emptySet()).isEmpty())
    }

    @Test
    fun toggledGroupKeyTogglesByAnyMemberMessage() {
        assertEquals(setOf("u:u1"), DshExportSelection.toggledGroupKey(turn, emptySet(), "a1"))
        assertEquals(setOf("u:u1"), DshExportSelection.toggledGroupKey(turn, emptySet(), "u1"))
        assertEquals(emptySet(), DshExportSelection.toggledGroupKey(turn, setOf("u:u1"), "a1"))
    }

    @Test
    fun toggledGroupKeyReturnsNullForUnknownOrBlank() {
        assertNull(DshExportSelection.toggledGroupKey(turn, emptySet(), "nope"))
        assertNull(DshExportSelection.toggledGroupKey(turn, emptySet(), ""))
    }

    @Test
    fun toggleAllSelectsThenClears() {
        val groups = DshExportSelection.groups(turn)
        val all = DshExportSelection.toggleAll(groups, emptySet())
        assertEquals(setOf("u:u1"), all)
        assertEquals(emptySet(), DshExportSelection.toggleAll(groups, all))
        assertTrue(DshExportSelection.allSelected(groups, all))
        assertTrue(!DshExportSelection.allSelected(emptyList(), emptySet()))
    }

    @Test
    fun selectedCountIgnoresStaleKeys() {
        val groups = DshExportSelection.groups(turn)
        assertEquals(1, DshExportSelection.selectedCount(groups, setOf("u:u1", "stale")))
        assertEquals(0, DshExportSelection.selectedCount(groups, setOf("stale")))
    }

    @Test
    fun streamingReplyIsNotSelectable() {
        val messages = listOf(user("u1"), assistant("a1", streaming = true))
        assertEquals(listOf("u1"), DshExportSelection.groups(messages)[0].selectableIds)
        assertEquals(listOf("u1"), DshExportSelection.selectedMessages(messages, setOf("u:u1")).map { it.id })
    }
}
