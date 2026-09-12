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
            message.contextRecalls.isEmpty() && message.contextInstructions.isEmpty() && message.remoteTool == null &&
            message.readableContent == null && message.attachmentIds.isEmpty() && message.sourceSeq == null
        if (nullIfNoComposite) return null
        val root = JSONObject()
        message.contextCatalog.takeIf { it.isNotEmpty() }?.let { root.put("catalog", encodeCatalogEntries(it)) }
        message.contextSections.takeIf { it.isNotEmpty() }?.let { root.put("sections", encodeSections(it)) }
        message.contextRecalls.takeIf { it.isNotEmpty() }?.let { root.put("recalls", encodeRecalls(it)) }
        message.contextInstructions.takeIf { it.isNotEmpty() }?.let { root.put("instructions", encodeInstructions(it)) }
        message.remoteTool?.let { root.put("remoteTool", encodeRemoteTool(it)) }
        message.readableContent?.let { root.put("readableContent", it) }
        message.sourceSeq?.let { root.put("sourceSeq", it) }
        root.put("attachmentIds", JSONArray().apply { message.attachmentIds.forEach { put(it) } })
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
            instructions.isEmpty() && remoteTool == null && root.opt("readableContent") == null && root.optJSONArray("attachmentIds") == null && !root.has("sourceSeq")
        ) {
            return base
        }
        return base.copy(
            contextCatalog = catalog,
            contextSections = sections,
            contextRecalls = recalls,
            contextInstructions = instructions,
            remoteTool = remoteTool,
            sourceSeq = root.optInt("sourceSeq", -1).takeIf { it >= 0 } ?: base.sourceSeq,
            readableContent = root.optString("readableContent").takeIf { root.opt("readableContent") != null },
            attachmentIds = root.optJSONArray("attachmentIds")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it) }
            } ?: base.attachmentIds,
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
        put("stopped", tool.stopped)
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
        tool.webCard?.let { put("webCard", encodeWebCard(it)) }
        tool.searchCard?.let { put("searchCard", encodeSearchCard(it)) }
    }

    private fun encodeWebCard(card: DshWebCard): JSONObject = JSONObject().apply {
        put("isFetch", card.isFetch)
        put("url", card.url)
        put("statusCode", card.statusCode)
        put("answer", card.answer)
        put("truncated", card.truncated)
        put("sources", JSONArray().apply {
            card.sources.forEach { source ->
                put(JSONObject().apply {
                    put("url", source.url)
                    put("title", source.title)
                    put("snippet", source.snippet)
                    put("publishedAt", source.publishedAt)
                })
            }
        })
    }

    private fun decodeWebCard(o: JSONObject): DshWebCard {
        val sourcesArr = o.optJSONArray("sources") ?: JSONArray()
        val sources = buildList {
            for (index in 0 until sourcesArr.length()) {
                val source = sourcesArr.optJSONObject(index) ?: continue
                add(
                    DshWebSource(
                        url = source.optString("url"),
                        title = source.optString("title"),
                        snippet = source.optString("snippet"),
                        publishedAt = source.optString("publishedAt"),
                    ),
                )
            }
        }
        return DshWebCard(
            isFetch = o.optBoolean("isFetch", false),
            url = o.optString("url"),
            statusCode = o.optInt("statusCode", 0),
            answer = o.optString("answer"),
            sources = sources,
            truncated = o.optBoolean("truncated", false),
        )
    }

    private fun encodeSearchCard(card: DshSearchCard): JSONObject = JSONObject().apply {
        put("pathsOnly", card.pathsOnly)
        put("truncated", card.truncated)
        put("total", card.total)
        put("paths", JSONArray().apply { card.paths.forEach { put(it) } })
        put("files", JSONArray().apply {
            card.files.forEach { file ->
                put(JSONObject().apply {
                    put("path", file.path)
                    put("matches", JSONArray().apply {
                        file.matches.forEach { match ->
                            put(JSONObject().apply {
                                put("lineNumber", match.lineNumber)
                                put("line", match.line)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun decodeSearchCard(o: JSONObject): DshSearchCard {
        val pathsArr = o.optJSONArray("paths") ?: JSONArray()
        val paths = buildList {
            for (index in 0 until pathsArr.length()) {
                val path = pathsArr.optString(index).orEmpty()
                if (path.isNotEmpty()) add(path)
            }
        }
        val filesArr = o.optJSONArray("files") ?: JSONArray()
        val files = buildList {
            for (index in 0 until filesArr.length()) {
                val file = filesArr.optJSONObject(index) ?: continue
                val matchesArr = file.optJSONArray("matches") ?: JSONArray()
                val matches = buildList {
                    for (matchIndex in 0 until matchesArr.length()) {
                        val match = matchesArr.optJSONObject(matchIndex) ?: continue
                        add(DshSearchMatch(match.optInt("lineNumber", 0), match.optString("line")))
                    }
                }
                add(DshSearchFile(file.optString("path"), matches))
            }
        }
        return DshSearchCard(
            pathsOnly = o.optBoolean("pathsOnly", false),
            paths = paths,
            files = files,
            truncated = o.optBoolean("truncated", false),
            total = o.optInt("total", 0),
        )
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
        stopped = o.optBoolean("stopped", false),
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
        webCard = o.optJSONObject("webCard")?.let(::decodeWebCard),
        searchCard = o.optJSONObject("searchCard")?.let(::decodeSearchCard),
    )
}
