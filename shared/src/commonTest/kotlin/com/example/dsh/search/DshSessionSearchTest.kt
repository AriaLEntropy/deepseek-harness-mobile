package com.example.dsh.search

import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.session.DshSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.example.dsh.search.DshSessionSearch

class DshSessionSearchTest {

    private fun session(id: String, title: String, updatedAt: Long = 0L) =
        DshSession(id = id, title = title, workspace = "w", updatedLabel = "", updatedAt = updatedAt)

    private fun msg(
        content: String,
        hidden: Boolean = false,
        reasoning: Boolean = false,
        context: Boolean = false,
    ) = DshMessage(
        id = content,
        role = DshMessageRole.ASSISTANT,
        content = content,
        hidden = hidden,
        isReasoning = reasoning,
        isContextInjection = context,
    )

    private fun build(
        sessions: List<DshSession>,
        messages: Map<String, List<DshMessage>>,
        query: String,
        hitLimit: Int = 80,
    ) = DshSessionSearch.build(
        sessions = sessions,
        messagesFor = { messages[it] },
        rawQuery = query,
        hitLimit = hitLimit,
        dateLabel = { "D$it" },
    )

    @Test
    fun blankQueryReturnsNothing() {
        val sessions = listOf(session("s1", "hello"))
        val messages = mapOf("s1" to listOf(msg("hello")))
        assertTrue(build(sessions, messages, "   ").isEmpty())
        assertTrue(build(sessions, messages, "").isEmpty())
    }

    @Test
    fun titleOnlyMatchDegradesToSingleRow() {
        val hits = build(listOf(session("s1", "Release Notes", updatedAt = 7)), emptyMap(), "release")
        assertEquals(1, hits.size)
        assertEquals("s1", hits[0].sessionId)
        assertEquals("D7", hits[0].dateLabel)
        assertEquals("", hits[0].snippetMatch)
        assertEquals("", hits[0].matchBadge)
    }

    @Test
    fun contentMatchBuildsSnippetAndIsCaseInsensitive() {
        val hits = build(
            listOf(session("s1", "title")),
            mapOf("s1" to listOf(msg("say Hello World now"))),
            "hello",
        )
        assertEquals(1, hits.size)
        assertEquals("Hello", hits[0].snippetMatch)
        assertTrue(hits[0].snippetBefore.endsWith("say "), hits[0].snippetBefore)
        assertTrue(hits[0].snippetAfter.startsWith(" World now"), hits[0].snippetAfter)
    }

    @Test
    fun multipleMatchesGetOrderedBadges() {
        val hits = build(
            listOf(session("s1", "t")),
            mapOf("s1" to listOf(msg("alpha"), msg("beta"), msg("gamma"))),
            "a",
        )
        assertEquals(3, hits.size)
        assertEquals(listOf("1/3", "2/3", "3/3"), hits.map { it.matchBadge })
    }

    @Test
    fun skipsHiddenReasoningAndContextMessages() {
        val messages = listOf(
            msg("needle", hidden = true),
            msg("needle", reasoning = true),
            msg("needle", context = true),
        )
        assertTrue(build(listOf(session("s1", "t")), mapOf("s1" to messages), "needle").isEmpty())
    }

    @Test
    fun hitLimitCapsResults() {
        val hits = build(
            listOf(session("s1", "t")),
            mapOf("s1" to listOf(msg("x1"), msg("x2"), msg("x3"))),
            "x",
            hitLimit = 2,
        )
        assertEquals(2, hits.size)
    }

    @Test
    fun preservesSessionOrder() {
        val hits = build(
            listOf(session("s1", "one"), session("s2", "two")),
            mapOf("s1" to listOf(msg("zebra")), "s2" to listOf(msg("zebra"))),
            "zebra",
        )
        assertEquals(listOf("s1", "s2"), hits.map { it.sessionId })
    }
}
