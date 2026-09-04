package com.example.dsh.conversation

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DshStreamingTurnTest {
    @Test
    fun newTurnDoesNotResumeIntoPreviousAssistant() {
        val messages = listOf(
            DshMessage("user-1", DshMessageRole.USER, "你是谁"),
            DshMessage("text-162-0", DshMessageRole.ASSISTANT, "我是 DeepSeek Harness。"),
            DshMessage("user-2", DshMessageRole.USER, "协议号"),
        )
        assertNull(dshAssistantTailForCurrentTurn(messages))
        assertNull(dshHistoryTailToResume(messages, "text-162-0"))
    }

    @Test
    fun staleHistoryWithoutNewUserStillRefusesPreviousAssistant() {
        val messages = listOf(
            DshMessage("user-1", DshMessageRole.USER, "你是谁"),
            DshMessage("text-162-0", DshMessageRole.ASSISTANT, "我是 DeepSeek Harness。"),
        )
        assertEquals("text-162-0", dshAssistantTailForCurrentTurn(messages)?.id)
        assertNull(dshHistoryTailToResume(messages, "text-162-0"))
    }

    @Test
    fun reconnectResumesCurrentTurnAssistant() {
        val messages = listOf(
            DshMessage("user-1", DshMessageRole.USER, "你是谁"),
            DshMessage("text-162-0", DshMessageRole.ASSISTANT, "我是 DeepSeek Harness。"),
            DshMessage("user-2", DshMessageRole.USER, "协议号"),
            DshMessage("text-163-0", DshMessageRole.ASSISTANT, "抱歉，"),
        )
        val live = dshHistoryTailToResume(messages, "text-162-0")
        assertEquals("text-163-0", live?.id)
        assertEquals("抱歉，", live?.content)
    }

    @Test
    fun reconnectWithoutLocalSendUsesCurrentTurnTail() {
        val messages = listOf(
            DshMessage("user-1", DshMessageRole.USER, "你是谁"),
            DshMessage("text-162-0", DshMessageRole.ASSISTANT, "partial"),
        )
        assertEquals("text-162-0", dshHistoryTailToResume(messages, "")?.id)
    }

    @Test
    fun reasoningAndImagesAreNotLiveTextTargets() {
        val messages = listOf(
            DshMessage("user-1", DshMessageRole.USER, "画一张图"),
            DshMessage("think-1", DshMessageRole.ASSISTANT, "thinking", isReasoning = true),
            DshMessage("image-1", DshMessageRole.ASSISTANT, "", attachmentId = "att-1"),
        )
        assertNull(dshAssistantTailForCurrentTurn(messages))
    }

    @Test
    fun hostIdRemapIsVisuallyEqual() {
        val local = listOf(
            DshMessage("user-1", DshMessageRole.USER, "介绍一下kotlin"),
            DshMessage("assistant-9", DshMessageRole.ASSISTANT, "看起来你想深入了解更多。", streaming = true),
        )
        val host = listOf(
            DshMessage("text-200-0", DshMessageRole.USER, "介绍一下kotlin"),
            DshMessage("text-201-0", DshMessageRole.ASSISTANT, "看起来你想深入了解更多。"),
        )
        assertEquals(true, dshMessagesVisuallyEqual(local, host))
        assertEquals(false, local == host)
    }

    @Test
    fun differentContentIsNotVisuallyEqual() {
        val left = listOf(DshMessage("a", DshMessageRole.ASSISTANT, "old"))
        val right = listOf(DshMessage("b", DshMessageRole.ASSISTANT, "new"))
        assertEquals(false, dshMessagesVisuallyEqual(left, right))
    }

    @Test
    fun liveBufferWinsOverFirstFlushSnapshot() {
        val stored = "I'll write some Kotlin coroutine code examples for you.\n\n```kotlin\nfun main"
        val live = stored + "() = runBlocking {\n    delay(1)\n}\n```"
        assertEquals(
            live,
            dshDisplayedAssistantContent(stored, live, isLiveRow = true),
        )
    }

    @Test
    fun emptyPlaceholderUsesLiveUntilSettle() {
        assertEquals(
            "full reply",
            dshDisplayedAssistantContent(stored = "", live = "full reply", isLiveRow = true),
        )
    }

    @Test
    fun settledRowUsesStoredOnceLiveIsCleared() {
        val full = "complete markdown with a code fence"
        assertEquals(
            full,
            dshDisplayedAssistantContent(stored = full, live = "", isLiveRow = false),
        )
    }

    @Test
    fun neverFallsBackToShorterStoredWhenLiveRemains() {
        assertEquals(
            "abcdef",
            dshDisplayedAssistantContent(stored = "abc", live = "abcdef", isLiveRow = false),
        )
    }

    @Test
    fun toolPrecedingTextIsNotTheTurnTail() {
        val messages = listOf(
            DshMessage("user-3", DshMessageRole.USER, "diaoyongwentigongju"),
            DshMessage("text-1428-1", DshMessageRole.ASSISTANT, "明白了——您想让我调用提问工具。好的，我来问："),
            DshMessage("tool-1429", DshMessageRole.TOOL, "正在执行 ask_user_question", toolName = "ask_user_question"),
            DshMessage("text-1646-1", DshMessageRole.ASSISTANT, "提问工具已经正常调用了一次，但这次没有收到您的选择或文字回复。"),
        )
        assertEquals("text-1646-1", dshTurnTailAssistant(messages)?.id)
    }

    @Test
    fun onlyLastSegmentOfSplitAnswerIsTheTurnTail() {
        val messages = listOf(
            DshMessage("user-1", DshMessageRole.USER, "介绍一下"),
            DshMessage("assistant-7", DshMessageRole.ASSISTANT, "第一段"),
            DshMessage("assistant-7-segment-1", DshMessageRole.ASSISTANT, "第二段"),
            DshMessage("assistant-7-segment-2", DshMessageRole.ASSISTANT, "第三段"),
        )
        assertEquals("assistant-7-segment-2", dshTurnTailAssistant(messages)?.id)
    }

    @Test
    fun reasoningAndContextAndStreamingAreNotTurnTails() {
        val messages = listOf(
            DshMessage("user-1", DshMessageRole.USER, "继续"),
            DshMessage("think-1", DshMessageRole.ASSISTANT, "thinking", isReasoning = true),
            DshMessage("ctx-1", DshMessageRole.TOOL, "剧本文本", isContextInjection = true),
            DshMessage("assistant-8", DshMessageRole.ASSISTANT, "还在生成", streaming = true),
        )
        assertNull(dshTurnTailAssistant(messages))
    }
}
