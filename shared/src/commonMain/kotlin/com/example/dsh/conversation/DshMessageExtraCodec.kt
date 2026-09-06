package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * Hand-rolled JSON codec for the composite fields of [DshMessage] that back
 * the local store. Kept as plain functions (no kotlinx.serialization plugin)
 * so the persistent cache round-trips identity without build changes.
 */
internal object DshMessageExtraCodec {

    /**
     * Returns the composite fields of [message] (context lists plus the
     * structured tool model) as a JSON string, or null when there is nothing
     * beyond the scale store columns to persist.
     */
    fun encode(message: DshMessage): String? {
        val nullIfNoComposite = message.contextCatalog.isEmpty() && message.contextSections.isEmpty() &&
            message.contextRecalls.isEmpty() && message.contextInstructions.isEmpty() && message.remoteTool == null
        if (nullIfNoComposite) return null
        val root = JSONObject()
        message.contextCatalog.takeIf { it.isNotEmpty() }?.let { root.put("catalog", encodeCatalogEntries(it)) }
        message.contextSections.takeIf { it.isNotEmpty() }?.let { root.put("sections", encodeSections(it)) }
        message.contextRecalls.takeIf { it.isNotEmpty() }?.let { root.put("recalls", encodeRecalls(it)) }
        message.contextInstructions.takeIf { it.isNotEmpty() }?.let { root.put("instructions", encodeInstructions(it)) }
        message.remoteTool?.let { root.put("remoteTool", encodeRemoteTool(it)) }
        return root.toString()
    }

    /**
     * Overlays the composite fields from [json] onto [base]. Returns [base]
     * unchanged when [json] is absent or unparseable.
     */
    fun decode(base: DshMessage, json: String?): DshMessage {
        if (json.isNullOrBlank()) return base
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return base
        val catalog = decodeCatalogEntries(root.optJSONArray("catalog"))
        val sections = decodeSections(root.optJSONArray("sections"))
        val recalls = decodeRecalls(root.optJSONArray("recalls"))
        val instructions = decodeInstructions(root.optJSONArray("instructions"))
        val remoteTool = root.optJSONObject("remoteTool")?.let(::decodeRemoteTool)
        if (catalog.isEmpty() && sections.isEmpty() && recalls.isEmpty() &&
            instructions.isEmpty() && remoteTool == null
        ) {
            return base
        }
        return base.copy(
            contextCatalog = catalog,
            contextSections = sections,
            contextRecalls = recalls,
            contextInstructions = instructions,
            remoteTool = remoteTool,
        )
    }

    private fun encodeCatalogEntries(list: List<DshContextCatalogEntry>): JSONArray = JSONArray().apply {
        list.forEach { e ->
            val o = JSONObject()
            o.put("name", e.name)
            o.put("description", e.description)
            put(o)
        }
    }

    private fun decodeCatalogEntries(arr: JSONArray?): List<DshContextCatalogEntry> {
        if (arr == null) return emptyList()
        return buildList {
            for (index in 0 until arr.length()) {
                val o = arr.optJSONObject(index) ?: continue
                add(DshContextCatalogEntry(o.optString("name"), o.optString("description")))
            }
        }
    }

    private fun encodeSections(list: List<DshContextSection>): JSONArray = JSONArray().apply {
        list.forEach { e ->
            val o = JSONObject()
            o.put("title", e.title)
            o.put("body", e.body)
            put(o)
        }
    }

    private fun decodeSections(arr: JSONArray?): List<DshContextSection> {
        if (arr == null) return emptyList()
        return buildList {
            for (index in 0 until arr.length()) {
                val o = arr.optJSONObject(index) ?: continue
                add(DshContextSection(o.optString("title"), o.optString("body")))
            }
        }
    }

    private fun encodeRecalls(list: List<DshContextRecall>): JSONArray = JSONArray().apply {
        list.forEach { e ->
            val o = JSONObject()
            o.put("label", e.label)
            o.put("retained", e.retainedMessages)
            o.put("omitted", e.omittedMessages)
            o.put("truncated", e.truncated)
            put(o)
        }
    }

    private fun decodeRecalls(arr: JSONArray?): List<DshContextRecall> {
        if (arr == null) return emptyList()
        return buildList {
            for (index in 0 until arr.length()) {
                val o = arr.optJSONObject(index) ?: continue
                add(DshContextRecall(
                    o.optString("label"),
                    o.optInt("retained", 0),
                    o.optInt("omitted", 0),
                    o.optBoolean("truncated", false),
                ))
            }
        }
    }

    private fun encodeInstructions(list: List<DshContextInstruction>): JSONArray = JSONArray().apply {
        list.forEach { e ->
            val o = JSONObject()
            o.put("path", e.path)
            o.put("action", e.action)
            put(o)
        }
    }

    private fun decodeInstructions(arr: JSONArray?): List<DshContextInstruction> {
        if (arr == null) return emptyList()
        return buildList {
            for (index in 0 until arr.length()) {
                val o = arr.optJSONObject(index) ?: continue
                add(DshContextInstruction(o.optString("path"), o.optString("action")))
            }
        }
    }

    private fun encodeRemoteTool(tool: DshRemoteToolCallModel): JSONObject = JSONObject().apply {
        put("callId", tool.callId)
        put("toolName", tool.toolName)
        put("kind", tool.kind.name)
        put("title", tool.title)
        put("summary", tool.summary)
        put("input", tool.input)
        put("body", tool.body)
        put("output", tool.output)
        put("error", tool.error)
        put("running", tool.running)
        put("cardType", tool.cardType.name)
        put("filePath", tool.filePath)
        put("todoDone", tool.todoDone)
        put("todoTotal", tool.todoTotal)
        put("todoActive", tool.todoActive)
        put("todoActiveExtra", tool.todoActiveExtra)
        put("questionAnswered", tool.questionAnswered)
        put("questionTotal", tool.questionTotal)
        put("callTimeMs", tool.callTimeMs)
        put("durationMs", tool.durationMs)
    }

    private fun decodeRemoteTool(o: JSONObject): DshRemoteToolCallModel = DshRemoteToolCallModel(
        callId = o.optString("callId"),
        toolName = o.optString("toolName"),
        kind = runCatching { DshRemoteToolKind.valueOf(o.optString("kind")) }.getOrDefault(DshRemoteToolKind.GENERIC),
        title = o.optString("title"),
        summary = o.optString("summary"),
        input = o.optString("input"),
        body = o.optString("body"),
        output = o.optString("output"),
        error = o.optString("error").takeIf { it.isNotEmpty() },
        running = o.optBoolean("running", false),
        cardType = runCatching { DshToolCardType.valueOf(o.optString("cardType")) }.getOrDefault(DshToolCardType.GENERIC),
        filePath = o.optString("filePath").takeIf { it.isNotEmpty() },
        todoDone = o.optInt("todoDone", 0),
        todoTotal = o.optInt("todoTotal", 0),
        todoActive = o.optString("todoActive").takeIf { it.isNotEmpty() },
        todoActiveExtra = o.optInt("todoActiveExtra", 0),
        questionAnswered = o.optInt("questionAnswered", 0),
        questionTotal = o.optInt("questionTotal", 0),
        callTimeMs = o.optLong("callTimeMs", 0L),
        durationMs = o.optLong("durationMs", 0L),
    )
}