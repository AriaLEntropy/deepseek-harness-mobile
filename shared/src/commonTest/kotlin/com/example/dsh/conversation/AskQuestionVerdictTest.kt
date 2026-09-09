package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 提问（ask_user_question）完成/取消/中断后的卡片语义，对齐原版 AskQuestionRow：
 * ASK_CANCELLED → 正常态「已取消」；ASK_ABORTED / interrupted → stopped 态「已中断」；
 * 只有真正的执行失败才进入 error（红色）。
 */
class AskQuestionVerdictTest {

    private fun wireCall(callId: String, name: String, arguments: JSONObject): JSONObject {
        val data = JSONObject()
        data.put("callId", callId)
        data.put("name", name)
        data.put("arguments", arguments)
        val event = JSONObject()
        event.put("type", "tool/call")
        event.put("data", data)
        return JSONObject().put("event", event)
    }

    private fun askCall(): DshRemoteToolCallModel? {
        val q1 = JSONObject()
        q1.put("id", "q1")
        q1.put("prompt", "研究主题")
        val q2 = JSONObject()
        q2.put("id", "q2")
        q2.put("prompt", "交付物")
        val questions = JSONArray()
        questions.put(q1)
        questions.put(q2)
        val arguments = JSONObject().put("questions", questions)
        return DshRemoteToolCallModels.fromLiveCall(wireCall("ask-1", "ask_user_question", arguments))
    }

    private fun askResult(
        outputText: String,
        isError: Boolean,
        errorCode: String? = null,
    ): JSONObject {
        val textBlock = JSONObject()
        textBlock.put("type", "text")
        textBlock.put("text", outputText)
        val resultContent = JSONArray()
        resultContent.put(textBlock)
        val resultBlock = JSONObject()
        resultBlock.put("type", "tool-result")
        resultBlock.put("toolCallId", "ask-1")
        resultBlock.put("content", resultContent)
        if (isError) resultBlock.put("isError", true)

        val messageContent = JSONArray()
        messageContent.put(resultBlock)
        val source = JSONObject().put("callId", "ask-1")
        val message = JSONObject()
        message.put("content", messageContent)
        message.put("source", source)

        val data = JSONObject()
        data.put("message", message)
        if (errorCode != null) {
            val error = JSONObject()
            error.put("name", "UserQuestionError")
            error.put("code", errorCode)
            data.put("error", error)
        }
        val event = JSONObject()
        event.put("type", "tool/result")
        event.put("seq", 30)
        event.put("data", data)
        return JSONObject().put("event", event)
    }

    @Test
    fun askAnsweredShowsCountAndStaysNeutral() {
        val running = askCall()
        val output = """{"answers":[{"id":"q1","selected":["方案调研"]},{"id":"q2","selected":[]}]}"""
        val settled = DshRemoteToolCallModels.settleLiveResult(running, askResult(output, isError = false))

        assertEquals(DshRemoteToolKind.ASK_QUESTION, settled?.kind)
        assertEquals("1/2 已回答", settled?.summary)
        assertNull(settled?.error)
        assertFalse(settled?.stopped ?: true)
        assertEquals("研究主题：方案调研\n交付物", settled?.body)
        assertFalse(settled?.running ?: true)
    }

    @Test
    fun askCancelledRendersOkState() {
        val running = askCall()
        val settled = DshRemoteToolCallModels.settleLiveResult(
            running,
            askResult("Error: ask_user_question was cancelled", isError = true, errorCode = "ASK_CANCELLED"),
        )

        assertEquals("已取消", settled?.summary)
        assertNull(settled?.error)
        assertFalse(settled?.stopped ?: true)
        assertEquals("本轮已取消，未提交回答", settled?.body)
    }

    @Test
    fun askAbortedRendersStoppedState() {
        val running = askCall()
        val settled = DshRemoteToolCallModels.settleLiveResult(
            running,
            askResult("Error: ask_user_question was aborted before the user answered", isError = true, errorCode = "ASK_ABORTED"),
        )

        assertEquals("已中断", settled?.summary)
        assertNull(settled?.error)
        assertTrue(settled?.stopped == true)
        assertEquals("本轮已中断，未提交回答", settled?.body)
    }

    @Test
    fun askInterruptedByStopRendersStoppedState() {
        val running = askCall()
        val settled = DshRemoteToolCallModels.settleLiveResult(
            running,
            askResult("Error: ask_user_question was interrupted", isError = true, errorCode = "interrupted"),
        )

        assertEquals("已中断", settled?.summary)
        assertNull(settled?.error)
        assertTrue(settled?.stopped == true)
        assertEquals("本轮已中断，未提交回答", settled?.body)
    }

    @Test
    fun genericToolInterruptedRendersStoppedWithoutAskCopy() {
        val arguments = JSONObject().put("command", "pnpm test")
        val running = DshRemoteToolCallModels.fromLiveCall(wireCall("bash-1", "bash", arguments))

        val textBlock = JSONObject()
        textBlock.put("type", "text")
        textBlock.put("text", "Error: interrupted")
        val resultContent = JSONArray()
        resultContent.put(textBlock)
        val resultBlock = JSONObject()
        resultBlock.put("type", "tool-result")
        resultBlock.put("toolCallId", "bash-1")
        resultBlock.put("content", resultContent)
        resultBlock.put("isError", true)

        val messageContent = JSONArray()
        messageContent.put(resultBlock)
        val message = JSONObject()
        message.put("content", messageContent)
        message.put("source", JSONObject().put("callId", "bash-1"))

        val data = JSONObject()
        data.put("message", message)
        val error = JSONObject()
        error.put("name", "Interrupted")
        error.put("code", "interrupted")
        data.put("error", error)

        val event = JSONObject()
        event.put("type", "tool/result")
        event.put("seq", 31)
        event.put("data", data)
        val result = JSONObject().put("event", event)

        val settled = DshRemoteToolCallModels.settleLiveResult(running, result)

        assertNull(settled?.error)
        assertTrue(settled?.stopped == true)
        assertEquals("pnpm test", settled?.summary)
    }

    @Test
    fun realFailureStillRendersErrorState() {
        val running = askCall()
        val settled = DshRemoteToolCallModels.settleLiveResult(
            running,
            askResult("Permission denied", isError = true, errorCode = null),
        )

        assertEquals("Permission denied", settled?.error)
        assertFalse(settled?.stopped ?: true)
        assertEquals("Permission denied", settled?.body)
    }
}
