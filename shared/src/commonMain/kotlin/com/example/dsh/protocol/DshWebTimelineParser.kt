package com.example.dsh.protocol

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.export.DshReadableContent
import com.example.dsh.tool.DshRemoteToolCallModel
import com.example.dsh.tool.DshRemoteToolCallModels
import com.example.dsh.models.DshToolCardType
import com.example.dsh.message.DshWebTimelineItem

internal object DshWebTimelineParser {
    fun parseWebTimeline(events: JSONArray): List<DshWebTimelineItem> {
        val result = mutableListOf<DshWebTimelineItem>()
        val toolModels = mutableMapOf<String, DshRemoteToolCallModel>()
        val partials = linkedMapOf<String, StringBuilder>()
        for (index in 0 until events.length()) {
            val entry = events.optJSONObject(index) ?: continue
            val event = entry.optJSONObject("event") ?: entry
            val seq = event.optInt("seq", index)
            val type = event.optString("type")
            val data = event.optJSONObject("data") ?: continue
            val firstNewItem = result.size
            when (type) {
                "user/message" -> {
                    val content = data.optJSONArray("content")
                    val text = textFromBlocks(content)
                    val imagePreviews = imagePreviewsFromBlocks(content)
                    val attachmentIds = attachmentIdsFromBlocks(content)
                    if (text.isEmpty() && imagePreviews.isEmpty() && attachmentIds.isEmpty()) continue
                    val source = data.optJSONObject("source")
                    val sourceKind = source?.optString("kind").orEmpty()
                    if (sourceKind == "user") {
                        result += DshWebTimelineItem("user-$seq", DshWebTimelineItem.Kind.USER, text, attachmentIds = attachmentIds,
                            imagePreviews = imagePreviews, readableContent = DshReadableContent.blocks(content))
                    } else {
                        result += DshWebTimelineItem(
                            key = "context-$seq",
                            kind = DshWebTimelineItem.Kind.CONTEXT,
                            text = text,
                            sourceLabel = contextSummary(source),
                            source = source,
                            attachmentIds = attachmentIds,
                            imagePreviews = imagePreviews,
                            readableContent = DshReadableContent.blocks(content),
                        )
                    }
                }
                "assistant/message" -> {
                    val message = data.optJSONObject("message") ?: data
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val blocks = message.optJSONArray("content") ?: JSONArray()
                    appendAssistantBlocks(result, seq, blocks)
                    partials.remove(key)
                }
                "assistant/chunk" -> {
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val chunk = data.optJSONObject("chunk") ?: JSONObject()
                    val text = chunk.optString("text").ifEmpty { chunk.optString("delta") }
                    if (text.isNotEmpty() &&
                        chunk.optString("type") in setOf("", "text", "text-delta", "text_delta")
                    ) {
                        partials.getOrPut(key) { StringBuilder() }.append(text)
                    }
                }
                "tool/call" -> {
                    val remoteTool = DshRemoteToolCallModels.fromHistoryCall(entry) ?: continue
                    if (remoteTool.callId.isNotEmpty()) toolModels[remoteTool.callId] = remoteTool
                    result += DshWebTimelineItem(
                        key = "tool-$seq",
                        kind = DshWebTimelineItem.Kind.TOOL,
                        toolName = remoteTool.toolName,
                        input = remoteTool.input,
                        running = remoteTool.running,
                        callId = remoteTool.callId,
                        callSeq = seq,
                        cardType = remoteTool.cardType,
                        cardTitle = remoteTool.title,
                        cardBody = remoteTool.body,
                        remoteTool = remoteTool,
                    )
                }
                "tool/result" -> {
                    val message = data.optJSONObject("message")
                    val resultBlock = message?.optJSONArray("content")?.optJSONObject(0)
                    val callId = resultBlock?.optString("toolCallId")
                        ?: message?.optJSONObject("source")?.optString("callId")
                        ?: data.optString("callId")
                    val previous = toolModels[callId]
                    val settled = DshRemoteToolCallModels.settleHistoryResult(previous, entry) ?: continue
                    if (settled.callId.isNotEmpty()) toolModels[settled.callId] = settled
                    val call = result.lastOrNull {
                        it.kind == DshWebTimelineItem.Kind.TOOL &&
                            it.callSeq < seq &&
                            (settled.callId.isEmpty() || it.callId == settled.callId)
                    }
                    if (call != null) {
                        val callIndex = result.indexOf(call)
                        result[callIndex] = call.copy(
                            toolName = settled.toolName,
                            input = settled.input,
                            output = settled.output,
                            running = settled.running,
                            stopped = settled.stopped,
                            error = settled.error,
                            cardType = settled.cardType,
                            cardTitle = settled.title,
                            cardBody = settled.body,
                            remoteTool = settled,
                        )
                    } else {
                        result += DshWebTimelineItem(
                            key = "tool-$seq",
                            kind = DshWebTimelineItem.Kind.TOOL,
                            toolName = settled.toolName,
                            input = settled.input,
                            output = settled.output,
                            running = settled.running,
                            stopped = settled.stopped,
                            callId = settled.callId,
                            callSeq = seq,
                            error = settled.error,
                            cardType = settled.cardType,
                            cardTitle = settled.title,
                            cardBody = settled.body,
                            remoteTool = settled,
                        )
                    }
                }
                "turn/end" -> {
                    data.optJSONObject("reason")?.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { result += DshWebTimelineItem("turn-error-$seq", DshWebTimelineItem.Kind.ERROR, it) }
                }
            }
            for (itemIndex in firstNewItem until result.size) {
                result[itemIndex] = result[itemIndex].copy(sourceSeq = event.optInt("seq", -1).takeIf { it >= 0 })
            }
        }
        partials.forEach { (key, text) ->
            if (text.isNotEmpty()) {
                result += DshWebTimelineItem(
                    key = "partial-$key",
                    kind = DshWebTimelineItem.Kind.ASSISTANT,
                    text = text.toString(),
                )
            }
        }
        return result.filterNot { it.kind == DshWebTimelineItem.Kind.USER && it.isRuntimeContextSnapshot() }
    }
}

internal fun DshWebTimelineItem.isRuntimeContextSnapshot(): Boolean {
    return text.startsWith("Current runtime context. This snapshot supersedes earlier runtime-context snapshots.")
}

internal fun contextSourceLabel(source: JSONObject?): String {
    if (source == null) return "未知来源"
    val kind = source.optString("kind")
    return when (kind) {
        "skill-invocation" -> source.optString("name").takeIf { it.isNotEmpty() } ?: kind
        "plugin" -> source.optString("plugin").takeIf { it.isNotEmpty() } ?: kind
        "session-reference" -> sourceLabels(source, "references", "label").takeIf { it.isNotEmpty() } ?: kind
        "agent-instructions" -> sourceLabels(source, "changes", "path").takeIf { it.isNotEmpty() } ?: kind
        else -> kind.takeIf { it.isNotEmpty() } ?: source.optString("name").takeIf { it.isNotEmpty() } ?: "未知来源"
    }
}

internal fun contextSummary(source: JSONObject?): String {
    if (source?.optString("form") == "notice") {
        source.optString("summary").takeIf { it.isNotEmpty() }?.let { return it }
    }
    return contextSourceLabel(source)
}

private fun sourceLabels(source: JSONObject, member: String, field: String): String {
    val values = source.optJSONArray(member) ?: return ""
    val labels = mutableListOf<String>()
    for (index in 0 until values.length()) {
        val value = values.optJSONObject(index) ?: continue
        val label = value.optString(field).takeIf { it.isNotEmpty() } ?: continue
        if (!labels.contains(label)) labels += label
    }
    return labels.joinToString(", ")
}

internal fun toolInputSummary(value: Any?): String = when (value) {
    null -> ""
    is String -> value
    else -> value.toString()
}

internal fun toolOutputSummary(value: Any?): String = when (value) {
    null -> ""
    is String -> value
    is JSONArray -> textFromBlocks(value)
    else -> value.toString()
}

internal fun toolCardType(view: JSONObject): DshToolCardType = when (view.optString("card")) {
    "terminal" -> DshToolCardType.TERMINAL
    "read" -> DshToolCardType.READ
    "diff" -> DshToolCardType.DIFF
    "search" -> DshToolCardType.SEARCH
    "web" -> DshToolCardType.WEB
    else -> DshToolCardType.GENERIC
}

internal fun diffBody(view: JSONObject): String {
    val diffs = view.optJSONArray("diffs") ?: JSONArray()
    return buildString {
        for (index in 0 until diffs.length()) {
            val diff = diffs.optJSONObject(index) ?: continue
            appendLine(diff.optString("path"))
            appendLine("--- old")
            appendLine("+++ new")
            appendLine(diff.optString("oldText"))
            appendLine(diff.optString("newText"))
        }
    }.trim()
}

internal fun toolResultBody(type: DshToolCardType, view: JSONObject, fallback: String): String {
    return when (type) {
        DshToolCardType.TERMINAL -> view.optString("output").ifEmpty { fallback }
        DshToolCardType.READ -> readBody(view)
        DshToolCardType.DIFF -> diffBody(view)
        DshToolCardType.SEARCH -> searchBody(view)
        DshToolCardType.WEB -> webBody(view)
        else -> fallback
    }
}

internal fun readBody(view: JSONObject): String {
    val lines = view.optJSONArray("lines") ?: JSONArray()
    return buildString {
        for (index in 0 until lines.length()) {
            val line = lines.optJSONObject(index) ?: continue
            appendLine("${line.optInt("number")}\t${line.optString("text")}")
        }
    }.trim()
}

internal fun searchBody(view: JSONObject): String {
    return when (view.optString("shape")) {
        "paths" -> {
            val paths = view.optJSONArray("paths") ?: JSONArray()
            buildString {
                for (index in 0 until paths.length()) appendLine(paths.optString(index))
            }.trim()
        }
        else -> {
            val files = view.optJSONArray("files") ?: JSONArray()
            buildString {
                for (index in 0 until files.length()) {
                    val file = files.optJSONObject(index) ?: continue
                    appendLine(file.optString("path"))
                    val matches = file.optJSONArray("matches") ?: JSONArray()
                    for (matchIndex in 0 until matches.length()) {
                        val match = matches.optJSONObject(matchIndex) ?: continue
                        appendLine("${match.optInt("lineNumber")}\t${match.optString("line")}")
                    }
                }
            }.trim()
        }
    }
}

internal fun webBody(view: JSONObject): String {
    return when (view.optString("kind")) {
        "fetch" -> "${view.optString("url")}\nHTTP ${view.optInt("statusCode")}"
        else -> {
            val sources = view.optJSONArray("sources") ?: JSONArray()
            buildString {
                appendLine(view.optString("answer"))
                for (index in 0 until sources.length()) {
                    val source = sources.optJSONObject(index) ?: continue
                    appendLine("- ${source.optString("title").ifEmpty { source.optString("url") }} ${source.optString("url")}")
                }
            }.trim()
        }
    }
}

internal fun textFromBlocks(blocks: JSONArray?): String {
    if (blocks == null) return ""
    return buildString {
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            if (block.optString("type") == "text") append(block.optString("text"))
        }
    }
}

/** 从 content 块中提取所有 image 块的可显示 dataUrl（内嵌 data/url），attachmentId 类由调用方另行处理 */
internal fun imagePreviewsFromBlocks(blocks: JSONArray?): List<String> {
    if (blocks == null) return emptyList()
    val result = mutableListOf<String>()
    for (index in 0 until blocks.length()) {
        val block = blocks.optJSONObject(index) ?: continue
        if (block.optString("type") != "image") continue
        inlineImageDataUrl(block)?.let { result += it }
    }
    return result
}

/** 从 content 块中提取所有 image 块的 attachmentId（电脑端/历史消息的图片引用格式） */
internal fun attachmentIdsFromBlocks(blocks: JSONArray?): List<String> {
    if (blocks == null) return emptyList()
    val result = mutableListOf<String>()
    for (index in 0 until blocks.length()) {
        val block = blocks.optJSONObject(index) ?: continue
        if (block.optString("type") != "image") continue
        block.optJSONObject("attachment")?.optString("attachmentId")?.takeIf { it.isNotEmpty() }?.let { result += it }
    }
    return result
}

/** 从单个 image 块提取内嵌 dataUrl（data base64 / url），不含 attachmentId 引用 */
internal fun inlineImageDataUrl(block: JSONObject): String? {
    val data = block.optString("data").orEmpty()
    if (data.isNotEmpty()) {
        val mediaType = block.optString("mediaType").ifEmpty { "image/png" }
        return if (data.startsWith("data:")) data else "data:$mediaType;base64,$data"
    }
    val url = block.optString("url").orEmpty()
    if (url.isNotEmpty()) return url
    return null
}

internal fun appendAssistantBlocks(
    result: MutableList<DshWebTimelineItem>,
    seq: Int,
    blocks: JSONArray?,
) {
    if (blocks == null) return
    for (blockIndex in 0 until blocks.length()) {
        val block = blocks.optJSONObject(blockIndex) ?: continue
        when (block.optString("type")) {
            "text" -> block.optString("text").takeIf { it.isNotEmpty() }?.let {
                result += DshWebTimelineItem("text-$seq-$blockIndex", DshWebTimelineItem.Kind.ASSISTANT, it)
            }
            "reasoning" -> block.optString("text").takeIf { it.isNotEmpty() }?.let {
                result += DshWebTimelineItem(
                    "reasoning-$seq-$blockIndex",
                    DshWebTimelineItem.Kind.REASONING,
                    it,
                )
            }
            "image" -> {
                val inlineUrl = inlineImageDataUrl(block)
                val attachmentId = block.optJSONObject("attachment")?.optString("attachmentId")?.takeIf { it.isNotEmpty() }
                when {
                    inlineUrl != null -> result += DshWebTimelineItem(
                        "image-$seq-$blockIndex",
                        DshWebTimelineItem.Kind.IMAGE,
                        imagePreviews = listOf(inlineUrl),
                        readableContent = DshReadableContent.blocks(JSONArray().apply { put(block) }),
                    )
                    attachmentId != null -> result += DshWebTimelineItem(
                        "image-$seq-$blockIndex",
                        DshWebTimelineItem.Kind.IMAGE,
                        attachmentId = attachmentId,
                        readableContent = DshReadableContent.blocks(JSONArray().apply { put(block) }),
                    )
                }
            }
            "tool-call" -> Unit
            else -> result += DshWebTimelineItem(
                "block-$seq-$blockIndex",
                DshWebTimelineItem.Kind.UNKNOWN_BLOCK,
                block.toString(),
            )
        }
    }
}
