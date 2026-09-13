package com.example.dsh.protocol

import com.example.dsh.infrastructure.LogLevel
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.example.dsh.protocol.attachmentIdsFromBlocks
import com.example.dsh.protocol.contextSourceLabel
import com.example.dsh.protocol.contextSummary
import com.example.dsh.protocol.dshEncodeQueryComponent
import com.example.dsh.protocol.dshSessionEventLevel
import com.example.dsh.protocol.dshShouldLogSessionEvent
import com.example.dsh.protocol.imagePreviewsFromBlocks
import com.example.dsh.protocol.inlineImageDataUrl
import com.example.dsh.protocol.parseRespondReceipt
import com.example.dsh.protocol.pendingInteractionRpcId
import com.example.dsh.protocol.textFromBlocks
import com.example.dsh.protocol.toolCardType
import com.example.dsh.models.DshToolCardType

class DshHostProtocolTest {

    @Test
    fun sessionEventLevels() {
        assertEquals(LogLevel.DEBUG, dshSessionEventLevel("assistant/chunk", JSONObject()))
        assertEquals(
            LogLevel.WARN,
            dshSessionEventLevel("tool/result", JSONObject().put("error", JSONObject())),
        )
        assertEquals(
            LogLevel.WARN,
            dshSessionEventLevel("turn/end", JSONObject().put("reason", "Error: boom")),
        )
        assertEquals(LogLevel.INFO, dshSessionEventLevel("tool/result", JSONObject()))
        assertEquals(LogLevel.INFO, dshSessionEventLevel("turn/start", JSONObject()))
    }

    @Test
    fun chunkDeltasAreNotLogged() {
        assertTrue(dshShouldLogSessionEvent("turn/start", JSONObject()))
        listOf("text", "text_delta", "reasoning-delta", "tool_call_delta").forEach { type ->
            val data = JSONObject().put("chunk", JSONObject().put("type", type))
            assertFalse(dshShouldLogSessionEvent("assistant/chunk", data), type)
        }
        val other = JSONObject().put("chunk", JSONObject().put("type", "block-start"))
        assertTrue(dshShouldLogSessionEvent("assistant/chunk", other))
    }

    @Test
    fun textFromBlocksJoinsTextOnly() {
        assertEquals("", textFromBlocks(null))
        val blocks = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "a"))
            .put(JSONObject().put("type", "image").put("data", "x"))
            .put(JSONObject().put("type", "text").put("text", "b"))
        assertEquals("ab", textFromBlocks(blocks))
    }

    @Test
    fun inlineImageDataUrlBuildsDataUri() {
        val raw = JSONObject().put("type", "image").put("data", "AAAA").put("mediaType", "image/jpeg")
        assertEquals("data:image/jpeg;base64,AAAA", inlineImageDataUrl(raw))

        val alreadyData = JSONObject().put("data", "data:image/png;base64,BBBB")
        assertEquals("data:image/png;base64,BBBB", inlineImageDataUrl(alreadyData))

        val byUrl = JSONObject().put("url", "https://host/a.png")
        assertEquals("https://host/a.png", inlineImageDataUrl(byUrl))

        assertNull(inlineImageDataUrl(JSONObject().put("type", "image")))
    }

    @Test
    fun extractsImagePreviewsAndAttachmentIds() {
        val blocks = JSONArray()
            .put(JSONObject().put("type", "image").put("data", "AAAA").put("mediaType", "image/png"))
            .put(JSONObject().put("type", "image").put("attachment", JSONObject().put("attachmentId", "id-1")))
            .put(JSONObject().put("type", "text").put("text", "ignored"))
        assertEquals(listOf("data:image/png;base64,AAAA"), imagePreviewsFromBlocks(blocks))
        assertEquals(listOf("id-1"), attachmentIdsFromBlocks(blocks))
    }

    @Test
    fun mapsToolCardTypes() {
        assertEquals(DshToolCardType.TERMINAL, toolCardType(JSONObject().put("card", "terminal")))
        assertEquals(DshToolCardType.READ, toolCardType(JSONObject().put("card", "read")))
        assertEquals(DshToolCardType.DIFF, toolCardType(JSONObject().put("card", "diff")))
        assertEquals(DshToolCardType.SEARCH, toolCardType(JSONObject().put("card", "search")))
        assertEquals(DshToolCardType.WEB, toolCardType(JSONObject().put("card", "web")))
        assertEquals(DshToolCardType.GENERIC, toolCardType(JSONObject().put("card", "whatever")))
    }

    @Test
    fun contextLabelsFollowSourceKind() {
        assertEquals("未知来源", contextSourceLabel(null))
        assertEquals("calc", contextSourceLabel(JSONObject().put("kind", "skill-invocation").put("name", "calc")))
        assertEquals("web", contextSourceLabel(JSONObject().put("kind", "plugin").put("plugin", "web")))

        val refs = JSONObject().put("kind", "session-reference").put(
            "references",
            JSONArray()
                .put(JSONObject().put("label", "x"))
                .put(JSONObject().put("label", "y"))
                .put(JSONObject().put("label", "x")),
        )
        assertEquals("x, y", contextSourceLabel(refs))

        val changes = JSONObject().put("kind", "agent-instructions").put(
            "changes",
            JSONArray().put(JSONObject().put("path", "a")).put(JSONObject().put("path", "b")),
        )
        assertEquals("a, b", contextSourceLabel(changes))

        assertEquals("foo", contextSourceLabel(JSONObject().put("kind", "foo")))
        assertEquals("bar", contextSourceLabel(JSONObject().put("name", "bar")))
        assertEquals("未知来源", contextSourceLabel(JSONObject()))
    }

    @Test
    fun noticeSummaryPrefersExplicitSummary() {
        val notice = JSONObject().put("form", "notice").put("summary", "S")
        assertEquals("S", contextSummary(notice))
        val noSummary = JSONObject().put("form", "notice").put("kind", "plugin").put("plugin", "web")
        assertEquals("web", contextSummary(noSummary))
    }

    @Test
    fun pendingInteractionRpcIdPrefersEnvelopeThenPayload() {
        assertEquals("a", pendingInteractionRpcId(JSONObject().put("rpcId", "a"), JSONObject().put("rpcId", "b")))
        assertEquals("b", pendingInteractionRpcId(JSONObject(), JSONObject().put("rpcId", "b")))
        assertEquals(
            "c",
            pendingInteractionRpcId(JSONObject(), JSONObject().put("payload", JSONObject().put("rpcId", "c"))),
        )
        assertEquals("", pendingInteractionRpcId(JSONObject(), JSONObject()))
    }

    @Test
    fun parseRespondReceiptReadsAcceptedAndReason() {
        assertEquals(true to "", parseRespondReceipt(JSONObject().put("accepted", true)))
        assertEquals(true to "", parseRespondReceipt(JSONObject().put("accepted", "true")))
        assertEquals(false to "nope", parseRespondReceipt(JSONObject().put("accepted", false).put("reason", "nope")))
        assertEquals(false to "bad-response", parseRespondReceipt(JSONObject().put("accepted", 0)))

        val nested = JSONObject().put(
            "result",
            JSONObject().put("value", JSONObject().put("accepted", true)),
        )
        assertEquals(true to "", parseRespondReceipt(nested))
    }

    @Test
    fun encodesQueryComponents() {
        assertEquals("a%20b", dshEncodeQueryComponent("a b"))
        assertEquals("a%2Fb", dshEncodeQueryComponent("a/b"))
        assertEquals("a-_.~Z9", dshEncodeQueryComponent("a-_.~Z9"))
        assertEquals("%E4%B8%AD%E6%96%87", dshEncodeQueryComponent("中文"))
        assertEquals("", dshEncodeQueryComponent(""))
    }
}
