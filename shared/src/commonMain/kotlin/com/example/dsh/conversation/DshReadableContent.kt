package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 会话导出可选的文件格式；扩展名用于生成文件名。 */
internal enum class DshExportFormat(val extension: String, val label: String, val description: String) {
    TXT("txt", "纯文本", "通用 txt，便于粘贴"),
    MARKDOWN("md", "Markdown", "保留标题、列表与代码块"),
    HTML("html", "HTML", "网页，可分享或打印为 PDF"),
}

/** User-requested content export. Do not apply diagnostic payload stripping to message/tool text. */
internal object DshReadableContent {
    private val credential = Regex("""\b((?:api[_-]?key|access[_-]?ticket|clientToken|hostToken|token|secret|password)\s*[=:]\s*)(?:"(?:\\.|[^"\\])*"|'[^']*'|[^&\s,}]+)""", RegexOption.IGNORE_CASE)
    private val bearer = Regex("""\b(?:Bearer|Basic)\s+[^\s"',}]+""", RegexOption.IGNORE_CASE)
    private val dataUri = Regex("""data:[^\s"']*;base64,[A-Za-z0-9+/=_-]*""", RegexOption.IGNORE_CASE)
    private val key = Regex("""\bsk-[A-Za-z0-9_-]{8,}""")
    fun safeText(text: String): String = dataUri.replace(bearer.replace(
        credential.replace(key.replace(text, "***")) { "${it.groupValues[1]}***" }, "[credential omitted]"), "[图片数据已省略]")

    fun blocks(blocks: JSONArray?): String = buildString {
        if (blocks == null) return@buildString
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            when (block.optString("type")) {
                "text", "reasoning" -> append(safeText(block.optString("text")))
                "image" -> {
                    val ref = block.optJSONObject("attachment") ?: block
                    appendLine()
                    append("[图片] ")
                    append(ref.optString("name").ifEmpty { "未命名图片" })
                    ref.optString("mediaType").takeIf { it.isNotEmpty() }?.let { append("；$it") }
                    if (ref.optLong("bytes", -1) >= 0) append("；${ref.optLong("bytes")} B")
                    if (ref.optInt("width") > 0) append("；${ref.optInt("width")}×${ref.optInt("height")}")
                    val id = ref.optString("attachmentId")
                    if (id.isNotEmpty()) append("；引用 attachmentId=$id（通过 Host session.attachment 读取，无公共下载地址）")
                    else append("；无持久化引用，图片数据已省略")
                    appendLine()
                }
                "tool-call" -> Unit // represented by the ordered tool/call card
                else -> {
                    appendLine()
                    append("[${block.optString("type").ifEmpty { "内容块" }}] ")
                    append(safeText(block.optString("text").ifEmpty { block.optString("name") }))
                    appendLine()
                }
            }
        }
    }

    fun message(message: DshMessage, content: String = message.content): String {
        val body = if (content != message.content) content else message.readableContent ?: content
        if (message.role == DshMessageRole.TOOL) {
            val tool = message.remoteTool
            val status = when {
                message.toolError || tool?.error != null -> "失败"
                message.toolStopped || tool?.stopped == true -> "已中断"
                message.toolRunning || tool?.running == true -> "运行中"
                else -> "已完成"
            }
            return safeText(buildString {
                appendLine("[${if (message.isContextInjection) "上下文" else "工具"}] ${tool?.toolName ?: message.toolName ?: "未知工具"}；$status")
                tool?.filePath?.let { appendLine("文件：$it") }
                if (tool != null) {
                    if (tool.input.isNotEmpty()) appendLine("输入：\n${tool.input}")
                    append(tool.output.ifEmpty { tool.body.ifEmpty { body } })
                    tool.error?.let { append("\n错误：$it") }
                } else append(body)
            }).trimEnd()
        }
        return safeText(buildString {
            append(body)
            if (message.readableContent == null) {
                (message.attachmentIds + listOfNotNull(message.attachmentId)).distinct().forEach {
                    append("\n[图片] 引用 attachmentId=$it（通过 Host session.attachment 读取，无公共下载地址）")
                }
                if (message.imagePreviews.isNotEmpty() && message.attachmentIds.isEmpty() && message.attachmentId == null) {
                    append("\n[图片] 未获得持久化引用，图片数据已省略")
                }
            }
        })
    }

    /**
     * 「一键复制」目标回合的可读正文：按原始顺序拼接该回合的助手正文段
     * （正文可能被工具调用切分成多段）。
     *
     * 思考、上下文注入、隐藏项与工具调用卡片不复制：工具卡片有独立复制入口，
     * 完整记录（含工具顺序）由分享/导出承载。
     */
    fun copyText(messages: List<DshMessage>, anchorId: String, contentFor: (DshMessage) -> String = { it.content }): String {
        val index = messages.indexOfFirst { it.id == anchorId }
        if (index < 0) return ""
        val anchor = messages[index]
        if (anchor.hidden || anchor.isReasoning || anchor.isContextInjection || anchor.role == DshMessageRole.TOOL) return ""
        if (anchor.role != DshMessageRole.ASSISTANT) return contentFor(anchor)
        fun isPrompt(message: DshMessage) = message.role == DshMessageRole.USER && !message.isContextInjection && !message.hidden
        val start = messages.take(index).indexOfLast(::isPrompt) + 1
        val end = (index + 1 until messages.size).firstOrNull { isPrompt(messages[it]) } ?: messages.size
        return messages.subList(start, end)
            .filter {
                !it.hidden && !it.isReasoning && !it.isContextInjection && it.role != DshMessageRole.TOOL
            }
            .map(contentFor)
            .filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    fun turn(messages: List<DshMessage>, anchorId: String, contentFor: (DshMessage) -> String = { it.content }): String {
        val index = messages.indexOfFirst { it.id == anchorId }
        if (index < 0) return ""
        if (messages[index].role == DshMessageRole.USER) return message(messages[index], contentFor(messages[index]))
        val start = messages.take(index).indexOfLast { it.role == DshMessageRole.USER } + 1
        val end = (index + 1 until messages.size).firstOrNull { messages[it].role == DshMessageRole.USER } ?: messages.size
        return messages.subList(start, end).filterNot { it.hidden || it.isReasoning }
            .map { message(it, contentFor(it)) }.filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    /**
     * 导出用户在多选态中勾选的消息。
     * [messages] 需按界面顺序传入；[contentFor] 负责解析流式正文。
     */
    fun selection(
        title: String,
        sessionId: String,
        messages: List<DshMessage>,
        format: DshExportFormat,
        contentFor: (DshMessage) -> String = { it.content },
    ): String {
        val picked = messages.filterNot { it.hidden }
        if (format == DshExportFormat.HTML) return htmlDocument(title, sessionId, picked, contentFor)
        val body = buildString {
            if (format == DshExportFormat.MARKDOWN) {
                appendLine("# ${safeText(title)}")
                appendLine()
                appendLine("- 会话 ID：$sessionId")
                appendLine("- 消息数量：${picked.size}")
                picked.forEach { message ->
                    appendLine()
                    appendLine("## ${roleTitle(message)}")
                    appendLine()
                    appendLine(selectionBody(message, contentFor(message)))
                }
            } else {
                appendLine("DSH 会话：${safeText(title)}")
                appendLine("会话 ID：$sessionId")
                appendLine("格式：UTF-8 可读文本；已选择 ${picked.size} 条消息。")
                picked.forEach { message ->
                    appendLine()
                    appendLine("[${roleTitle(message)}]")
                    appendLine(message(message, contentFor(message)))
                }
            }
        }
        return body.trimEnd() + "\n"
    }

    /** 生成自包含 HTML 文档；内容转义、保留换行，并自带明暗主题。 */
    private fun htmlDocument(
        title: String,
        sessionId: String,
        picked: List<DshMessage>,
        contentFor: (DshMessage) -> String,
    ): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"zh\">")
        appendLine("<head>")
        appendLine("<meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        appendLine("<title>${htmlEscape(title)}</title>")
        appendLine("<style>")
        appendLine("body{font-family:-apple-system,'Segoe UI',Roboto,'PingFang SC','Microsoft YaHei',sans-serif;max-width:760px;margin:24px auto;padding:0 16px;line-height:1.6;color:#1f1f23;background:#fff;word-break:break-word}")
        appendLine("h1{font-size:22px;margin:0 0 4px}h2{font-size:15px;margin:24px 0 8px;color:#4176e6}")
        appendLine(".meta{color:#8a8f98;font-size:12px;margin:0 0 8px}.block{white-space:pre-wrap}")
        appendLine("@media (prefers-color-scheme:dark){body{background:#151517;color:#f5f6f7}h2{color:#78a4f8}.meta{color:#9aa0a6}}")
        appendLine("</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("<h1>${htmlEscape(title)}</h1>")
        appendLine("<p class=\"meta\">会话 ID：${htmlEscape(sessionId)} · 消息数量：${picked.size}</p>")
        picked.forEach { message ->
            appendLine("<h2>${htmlEscape(roleTitle(message))}</h2>")
            appendLine("<div class=\"block\">${htmlEscape(selectionBody(message, contentFor(message)))}</div>")
        }
        appendLine("</body>")
        appendLine("</html>")
    }

    private fun htmlEscape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun roleTitle(message: DshMessage): String = when {
        message.isReasoning -> "思考"
        message.isContextInjection -> "上下文"
        message.role == DshMessageRole.USER -> "用户"
        message.role == DshMessageRole.TOOL -> message.toolName ?: "工具"
        message.role == DshMessageRole.ERROR -> "错误"
        else -> "助手"
    }

    /** 助手/用户保留原始 Markdown，工具与思考沿用结构化可读文本。 */
    private fun selectionBody(message: DshMessage, content: String): String = when {
        message.role == DshMessageRole.TOOL || message.isReasoning || message.isContextInjection ->
            message(message, content)
        else -> safeText(content)
    }

    fun session(title: String, sessionId: String, events: JSONArray): String = buildString {
        appendLine("DSH 会话：${safeText(title)}")
        appendLine("会话 ID：$sessionId")
        appendLine("格式：UTF-8 可读文本；图片仅保留名称、属性和 Host 引用。")
        for (item in DshWebTimelineParser.parseWebTimeline(events)) {
            appendLine()
            appendLine("[${when (item.kind) {
                DshWebTimelineItem.Kind.USER -> "用户"
                DshWebTimelineItem.Kind.REASONING -> "思考"
                DshWebTimelineItem.Kind.TOOL -> "工具调用"
                DshWebTimelineItem.Kind.CONTEXT -> "上下文"
                DshWebTimelineItem.Kind.ERROR -> "错误"
                else -> "助手"
            }}]")
            val tool = item.remoteTool
            if (tool != null) appendLine(message(tool.toRemoteMessage(item.key)))
            else appendLine(safeText(item.readableContent ?: item.text))
        }
    }
}
