package com.example.dsh.rendering

import kotlin.test.Test
import kotlin.test.assertEquals

class DshStreamingMarkdownTest {

    @Test
    fun displayTextFallsBackToPlaceholderWhileStreaming() {
        assertEquals(DshStreamingMarkdown.PLACEHOLDER, DshStreamingMarkdown.displayText("", streaming = true))
        assertEquals("", DshStreamingMarkdown.displayText("", streaming = false))
        assertEquals("hi", DshStreamingMarkdown.displayText("hi", streaming = true))
    }

    @Test
    fun closesUnterminatedBacktickFence() {
        assertEquals("```\ncode\n```", DshStreamingMarkdown.closeOpenFence("```\ncode"))
        assertEquals("```\ncode\n```", DshStreamingMarkdown.closeOpenFence("```\ncode\n"))
    }

    @Test
    fun closesUnterminatedTildeFence() {
        assertEquals("~~~\nfoo\n~~~", DshStreamingMarkdown.closeOpenFence("~~~\nfoo"))
    }

    @Test
    fun leavesClosedFencesAndPlainTextUntouched() {
        assertEquals("```\ncode\n```", DshStreamingMarkdown.closeOpenFence("```\ncode\n```"))
        assertEquals("plain text", DshStreamingMarkdown.closeOpenFence("plain text"))
    }

    @Test
    fun closesOnlyTheLastOpenFence() {
        assertEquals("```\na\n```\n~~~\nb\n~~~", DshStreamingMarkdown.closeOpenFence("```\na\n```\n~~~\nb"))
    }
}
