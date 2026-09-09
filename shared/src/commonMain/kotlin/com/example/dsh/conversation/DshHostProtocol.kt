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
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.setTimeout

/** Browser Host paths mirrored by the native client. */
internal object DshHostProtocol {
    const val API_PREFIX = "/api"
    const val MUX_EVENTS_PATH = "$API_PREFIX/events.mux"
    const val HOST_EVENTS_PATH = "$API_PREFIX/events.host"
    const val HOST_DESCRIBE = "host.describe"
    const val HOST_LIST_DIRECTORY = "host.listDirectory"
    const val HOST_CREATE_DIRECTORY = "host.createDirectory"
    const val WORKSPACE_LIST = "workspace.list"
    const val WORKSPACE_CREATE = "workspace.create"
    const val WORKSPACE_RENAME = "workspace.rename"
    const val WORKSPACE_DELETE = "workspace.delete"
    const val WORKSPACE_INSERT_BEFORE = "workspace.insertBefore"
    const val SESSION_LIST = "session.list"
    const val SESSION_CREATE = "session.create"
    const val SESSION_HISTORY = "session.history"
    const val SESSION_MODELS = "session.models"
    const val SESSION_SELECT_MODEL = "session.selectModel"
    const val SESSION_PROMPT = "session.prompt"
    const val SESSION_CANCEL = "session.cancel"
    const val SESSION_UPDATE_QUEUE = "session.updateQueue"
    const val SESSION_RENAME = "session.rename"
    const val SESSION_FORK = "session.fork"
    const val SESSION_ATTACHMENT = "session.attachment"
    const val WORKSPACE_ARCHIVE_SESSION = "workspace.archiveSession"
    const val SETTINGS_DESCRIBE = "settings.describe"
    const val SETTINGS_UPDATE = "settings.update"
    const val CREDENTIALS_DESCRIBE = "credentials.describe"
    const val CREDENTIALS_SET = "credentials.set"
    const val LLM_PROVIDERS = "llm.providers"
    const val SKILL_LIST = "skill.list"
    const val AGENT_PRESET_LIST = "agentPreset.list"
    const val GOAL_EDIT = "goal.edit"
    const val GOAL_PAUSE = "goal.pause"
    const val GOAL_RESUME = "goal.resume"
    const val GOAL_CLEAR = "goal.clear"
    const val RESPOND_PATH = "$API_PREFIX/respond"
    const val SESSION_EXPORT_PATH = "$API_PREFIX/session.export"

}

internal data class DshHostConnection(val baseUrl: String, val token: String = "")

/** 从 schemastery schema.toJSON()（{uid, refs}）中解析 object 字段的 union 常量选项。 */
internal fun dshParseSchemaChoices(schema: JSONObject?, field: String): List<DshSettingsChoice> {
    if (schema == null) return emptyList()
    val refs = schema.optJSONObject("refs") ?: return emptyList()
    val root = refs.optJSONObject(schema.optString("uid")) ?: return emptyList()
    val fieldRef = root.optJSONObject("dict")?.optString(field) ?: return emptyList()
    val node = refs.optJSONObject(fieldRef) ?: return emptyList()
    if (node.optString("type") != "union") return emptyList()
    val list = node.optJSONArray("list") ?: return emptyList()
    val result = mutableListOf<DshSettingsChoice>()
    for (index in 0 until list.length()) {
        val uid = list.optString(index) ?: continue
        val item = refs.optJSONObject(uid) ?: continue
        if (item.optString("type") != "const") continue
        val value = item.optString("value")
        if (value.isEmpty()) continue
        val description = item.optJSONObject("meta")?.opt("description")
        val label = when (description) {
            is String -> description
            is JSONObject -> description.optString("zh").ifEmpty { description.optString("") }
            else -> value
        }
        result += DshSettingsChoice(value, label.ifEmpty { value })
    }
    return result
}

/** 会话事件摘要：type + 关键元数据（不含 delta 正文 / 工具 JSON 全文）。evtSeq 供详情反查内存原文。 */
internal fun dshSessionEventSummary(seq: Int, type: String, data: JSONObject): String {
    val sb = StringBuilder()
    sb.append("evtSeq=$seq")
    fun field(name: String) {
        val v = data.optString(name)
        if (v.isNotEmpty()) sb.append(" $name=$v")
    }
    when (type) {
        "turn/start", "step/start", "step/end" -> {
            field("turn")
            field("step")
        }
        "turn/end" -> {
            field("turn")
            field("reason")
        }
        "user/message" -> {
            field("turn")
            field("step")
            val sourceKind = data.optJSONObject("source")?.optString("kind").orEmpty()
            if (sourceKind.isNotEmpty()) sb.append(" source=$sourceKind")
            sb.append(" chars=${data.opt("content")?.toString()?.length ?: 0}")
        }
        "assistant/chunk" -> {
            field("turn")
            field("step")
            sb.append(" size=${data.opt("chunk")?.toString()?.length ?: 0}")
        }
        "assistant/message" -> {
            field("turn")
            field("step")
            field("interrupted")
            sb.append(" chars=${data.opt("message")?.toString()?.length ?: 0}")
            if (data.optJSONObject("usage") != null) sb.append(" usage=yes")
        }
        "tool/call" -> {
            field("turn")
            field("step")
            field("callId")
            field("name")
            data.optString("arguments").takeIf { it.isNotEmpty() }?.let { sb.append(" argsChars=${it.length}") }
        }
        "tool/result" -> {
            field("turn")
            field("step")
            field("callId")
            sb.append(" chars=${data.opt("message")?.toString()?.length ?: 0}")
            if (data.optJSONObject("error") != null) sb.append(" error=yes")
        }
        "todo/write" -> sb.append(" items=${data.optJSONArray("todos")?.length() ?: 0}")
        "request/header" -> field("reason")
        else -> { /* 未知类型只记 evtSeq */ }
    }
    return sb.toString()
}

/** 会话事件日志等级：chunk 记 DEBUG，带错误/失败记 WARN，其余 INFO。 */
internal fun dshSessionEventLevel(type: String, data: JSONObject): LogLevel = when {
    type == "assistant/chunk" -> LogLevel.DEBUG
    type == "tool/result" && data.optJSONObject("error") != null -> LogLevel.WARN
    type == "turn/end" && data.optString("reason").contains("error", ignoreCase = true) -> LogLevel.WARN
    else -> LogLevel.INFO
}

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
                        result += DshWebTimelineItem("user-$seq", DshWebTimelineItem.Kind.USER, text, attachmentIds = attachmentIds, imagePreviews = imagePreviews)
                    } else {
                        result += DshWebTimelineItem(
                            key = "context-$seq",
                            kind = DshWebTimelineItem.Kind.CONTEXT,
                            text = text,
                            sourceLabel = contextSummary(source),
                            source = source,
                            attachmentIds = attachmentIds,
                            imagePreviews = imagePreviews,
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

private fun DshWebTimelineItem.isRuntimeContextSnapshot(): Boolean {
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
                    )
                    attachmentId != null -> result += DshWebTimelineItem(
                        "image-$seq-$blockIndex",
                        DshWebTimelineItem.Kind.IMAGE,
                        attachmentId = attachmentId,
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

internal data class DshRpcCall(
    val rpcId: String,
    private val cancelAction: () -> Unit = {},
) {
    fun cancel() = cancelAction()
}

private data class QueuedRpc(
    val generation: Long,
    val method: String,
    val payload: JSONObject,
    val rpcId: String,
    val callback: (JSONObject?, DshRpcError?, String) -> Unit,
)

/** Owns one long-lived mux/host WebSocket connection generation. */
internal class DshHostConnectionRuntime(
    private val network: NetworkModule,
    private val webSocket: DshWebSocketModule,
    private val connection: DshHostConnection,
    private val pagerId: String,
    private val onFrame: (DshDownlinkFrame) -> Unit,
    private val onState: (DshHostRuntimeState) -> Unit = {},
    private val onWorkspaceBaseline: (JSONObject) -> Unit = {},
    private val onSessionBaseline: (JSONObject) -> Unit = {},
    private val onQueueSnapshot: (String) -> Unit = {},
    private val onJobsSnapshot: (String) -> Unit = {},
    private val onSessionStatus: (String, Boolean) -> Unit = { _, _ -> },
    private val onSessionEvent: (String, DshRawSessionEvent) -> Unit = { _, _ -> },
    private val onRemoteEvent: (String) -> Unit = {},
    private val onProjection: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
) {
    private var generation = 0L
    private var rpcSequence = 0L
    private var muxOpen = false
    private var hostOpen = false
    private var hostDescribed = false
    private var productReady = false
    private var stopped = false
    private var starting = false
    private var muxHandle: DshWebSocketHandle? = null
    private var hostHandle: DshWebSocketHandle? = null
    private val bufferedFrames = mutableListOf<DshDownlinkFrame>()
    private val queued = mutableListOf<QueuedRpc>()

    init { start() }

    fun currentState(): DshHostRuntimeState = DshHostRuntimeState(
        phase = when {
            stopped -> DshHostRuntimePhase.STOPPED
            productReady -> DshHostRuntimePhase.READY
            hostDescribed -> DshHostRuntimePhase.SYNCING
            muxOpen || hostOpen -> DshHostRuntimePhase.HOST_HANDSHAKE
            starting -> DshHostRuntimePhase.CONNECTING
            else -> DshHostRuntimePhase.DISCONNECTED
        },
        generation = generation,
        muxOpen = muxOpen,
        hostOpen = hostOpen,
    )

    fun start() {
        if (stopped || starting || productReady) return
        starting = true
        generation += 1
        val myGeneration = generation
        muxOpen = false
        hostOpen = false
        hostDescribed = false
        productReady = false
        bufferedFrames.clear()
        publish(DshHostRuntimePhase.CONNECTING, "正在打开 DSH 事件流")
        muxHandle = webSocket.connect(webSocketUrl(DshHostProtocol.MUX_EVENTS_PATH), connection.token) { event ->
            handleSocketEvent(myGeneration, DshEventStream.MUX, event)
        }
        hostHandle = webSocket.connect(webSocketUrl(DshHostProtocol.HOST_EVENTS_PATH), connection.token) { event ->
            handleSocketEvent(myGeneration, DshEventStream.HOST, event)
        }
    }

    fun stop() {
        stopped = true
        generation += 1
        starting = false
        productReady = false
        bufferedFrames.clear()
        queued.clear()
        muxHandle?.close()
        hostHandle?.close()
        muxHandle = null
        hostHandle = null
        publish(DshHostRuntimePhase.STOPPED, "连接已停止")
    }

    fun call(
        method: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?, String) -> Unit,
    ): DshRpcCall {
        val myGeneration = generation
        val rpcId = nextRpcId(myGeneration)
        val request = QueuedRpc(myGeneration, method, payload, rpcId, callback)
        if (stopped) callback(null, DshRpcError("cancelled", "连接已停止"), rpcId)
        else if (productReady) dispatch(request)
        else queued += request
        return DshRpcCall(rpcId) { queued.removeAll { it.rpcId == rpcId } }
    }

    /**
     * POST /api/respond has a ClientResponse body, not a unary RPC body.
     * [ok]=false 时按原版协议发送 error 信封（error.code 如 "cancelled"），
     * 供 apiproxy 的 respond 路由识别「用户取消」等语义响应。
     */
    fun respond(
        rpcId: String,
        value: JSONObject,
        callback: (Boolean, String) -> Unit,
        ok: Boolean = true,
        errorCode: String = "",
        errorMessage: String = "",
    ) {
        if (rpcId.isEmpty()) {
            DshStreamLog.question("respond.http.skip empty-rpcId session=${value.optString("sessionId")}")
            callback(false, "缺少请求编号")
            return
        }
        if (!productReady) {
            DshStreamLog.question("respond.http.skip not-ready rpcId=$rpcId")
            callback(false, "连接尚未就绪")
            return
        }
        val myGeneration = generation
        val body = JSONObject().apply {
            put("type", "client-response")
            put("rpcId", rpcId)
            put("result", JSONObject().apply {
                put("ok", ok)
                if (ok) {
                    put("value", value)
                } else {
                    put("error", JSONObject().apply {
                        put("code", errorCode)
                        put("message", errorMessage)
                        put("details", JSONObject())
                    })
                }
            })
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        DshStreamLog.question(
            "respond.http.start rpcId=$rpcId session=${value.optString("sessionId")} url=${connection.baseUrl.trimEnd('/')}${DshHostProtocol.RESPOND_PATH} body='${DshStreamLog.preview(body.toString(), 400)}'",
        )
        network.httpRequest(
            "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.RESPOND_PATH}", true, body, headers, null, REQUEST_TIMEOUT_SECONDS,
        ) { data, success, errorMsg, response ->
            if (stopped || myGeneration != generation) {
                DshStreamLog.question("respond.http.cancel rpcId=$rpcId")
                callback(false, "generation-cancelled")
                return@httpRequest
            }
            if (!success) {
                DshStreamLog.question(
                    "respond.http.fail rpcId=$rpcId status=${response.statusCode ?: 0} error='$errorMsg' body='${DshStreamLog.preview(data.toString(), 400)}'",
                )
                callback(false, "respond failed (${response.statusCode ?: 0}): $errorMsg")
                return@httpRequest
            }
            val (accepted, reason) = parseRespondReceipt(data)
            DshStreamLog.question(
                "respond.http.done rpcId=$rpcId accepted=$accepted reason='$reason' status=${response.statusCode ?: 0} body='${DshStreamLog.preview(data.toString(), 400)}'",
            )
            callback(accepted, reason)
        }
    }

    private fun handleSocketEvent(myGeneration: Long, stream: DshEventStream, event: DshWebSocketEvent) {
        if (stopped || myGeneration != generation) return
        when (event.kind) {
            DshWebSocketEventKind.OPEN -> {
                if (stream == DshEventStream.MUX) muxOpen = true else hostOpen = true
                publish(DshHostRuntimePhase.HOST_HANDSHAKE, "事件流已连接")
                if (muxOpen && hostOpen && !hostDescribed) describeHost(myGeneration)
            }
            DshWebSocketEventKind.FRAME -> {
                bufferedFrames += DshDownlinkFrame(myGeneration, stream, event.data)
                if (productReady) flushFrames()
            }
            DshWebSocketEventKind.ERROR, DshWebSocketEventKind.CLOSED -> invalidateGeneration(
                myGeneration, event.message.ifEmpty { "DSH 事件流已断开" },
            )
        }
    }

    private fun describeHost(myGeneration: Long) {
        directCall(myGeneration, DshHostProtocol.HOST_DESCRIBE, JSONObject()) { value, error ->
            if (myGeneration != generation || stopped) return@directCall
            if (error != null || value == null) {
                invalidateGeneration(myGeneration, error?.message ?: "host.describe 失败")
                return@directCall
            }
            hostDescribed = true
            publish(DshHostRuntimePhase.SYNCING, "正在同步远程会话")
            var workspaceDone = false
            var sessionDone = false
            var baselineError: DshRpcError? = null
            fun finishBaseline() {
                if (!workspaceDone || !sessionDone) return
                if (baselineError != null) {
                    invalidateGeneration(myGeneration, baselineError?.message ?: "同步基线失败")
                    return
                }
                productReady = true
                starting = false
                flushFrames()
                publish(DshHostRuntimePhase.READY, "DSH 已就绪")
                val pending = queued.toList()
                queued.clear()
                pending.filter { it.generation == myGeneration }.forEach(::dispatch)
            }
            directCall(myGeneration, DshHostProtocol.WORKSPACE_LIST, JSONObject()) { workspaceValue, errorValue ->
                workspaceDone = true
                if (errorValue != null) baselineError = errorValue
                if (errorValue == null && workspaceValue != null) onWorkspaceBaseline(workspaceValue)
                finishBaseline()
            }
            directCall(myGeneration, DshHostProtocol.SESSION_LIST, JSONObject()) { sessionValue, errorValue ->
                sessionDone = true
                if (errorValue != null) baselineError = errorValue
                if (errorValue == null && sessionValue != null) onSessionBaseline(sessionValue)
                finishBaseline()
            }
        }
    }

    private fun flushFrames() {
        if (!productReady) return
        val frames = bufferedFrames.toList()
        bufferedFrames.clear()
        frames.forEach(onFrame)
    }

    private fun invalidateGeneration(myGeneration: Long, message: String) {
        if (stopped || myGeneration != generation) return
        generation += 1
        val reconnectGeneration = generation
        muxHandle?.close()
        hostHandle?.close()
        muxHandle = null
        hostHandle = null
        muxOpen = false
        hostOpen = false
        hostDescribed = false
        productReady = false
        starting = false
        bufferedFrames.clear()
        val cancelled = queued.filter { it.generation == myGeneration }
        queued.removeAll { it.generation == myGeneration }
        cancelled.forEach { request ->
            request.callback(null, DshRpcError("generation-cancelled", message), request.rpcId)
        }
        publish(DshHostRuntimePhase.RECONNECTING, message)
        setTimeout(pagerId, RECONNECT_DELAY_MS) {
            if (!stopped && generation == reconnectGeneration) start()
        }
    }

    private fun dispatch(request: QueuedRpc) {
        if (request.generation != generation || stopped) return
        val body = JSONObject().apply {
            put("type", "client-request")
            put("rpcId", request.rpcId)
            put("method", request.method)
            put("payload", request.payload)
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        network.httpRequest(
            "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.API_PREFIX}/${request.method}",
            true, body, headers, null, REQUEST_TIMEOUT_SECONDS,
        ) { data, success, errorMsg, response ->
            if (request.generation != generation || stopped) {
                request.callback(null, DshRpcError("generation-cancelled", "请求所属连接世代已失效"), request.rpcId)
                return@httpRequest
            }
            if (!success) {
                request.callback(null, DshRpcError(
                    "transport-${response.statusCode ?: 0}",
                    "${request.method} failed (${response.statusCode ?: 0}): $errorMsg",
                ), request.rpcId)
                return@httpRequest
            }
            val result = data.optJSONObject("result")
            if (result == null) {
                request.callback(null, DshRpcError("bad-response", "${request.method} 返回了非法 RPC 信封"), request.rpcId)
                return@httpRequest
            }
            if (!result.optBoolean("ok")) {
                val error = result.optJSONObject("error")
                request.callback(null, DshRpcError(
                    error?.optString("code").orEmpty().ifEmpty { "internal" },
                    error?.optString("message").orEmpty().ifEmpty { "${request.method} 失败" },
                    error?.optJSONObject("details")?.toString() ?: "{}",
                ), request.rpcId)
                return@httpRequest
            }
            request.callback(result.optJSONObject("value"), null, request.rpcId)
        }
    }

    private fun directCall(myGeneration: Long, method: String, payload: JSONObject, callback: (JSONObject?, DshRpcError?) -> Unit) {
        if (myGeneration != generation || stopped) return
        dispatch(QueuedRpc(myGeneration, method, payload, nextRpcId(myGeneration)) { value, error, _ -> callback(value, error) })
    }

    private fun webSocketUrl(path: String): String {
        val base = connection.baseUrl.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://") -> "wss://${base.removePrefix("https://")}"
            base.startsWith("http://") -> "ws://${base.removePrefix("http://")}"
            else -> base
        }
        return "$wsBase$path"
    }

    private fun nextRpcId(myGeneration: Long): String = "dsh-g${myGeneration}-${++rpcSequence}"

    private fun publish(phase: DshHostRuntimePhase, message: String) {
        onState(DshHostRuntimeState(phase, generation, muxOpen, hostOpen, message))
    }

    /**
     * 调用 DSH 插件 HTTP 端点。
     * 插件端点不在标准 RPC 路径 /api/{method} 下，所以不走 call() 方法，
     * 而是直接发 HTTP POST 到插件注册的端点。
     */
    fun callPlugin(
        endpoint: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        if (stopped) {
            callback(null, DshRpcError("connection-expired", "Connection is closed"))
            return
        }
        val myGeneration = generation
        val url = "${connection.baseUrl.trimEnd('/')}/api/session-manager/$endpoint"
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        network.httpRequest(url, true, payload, headers, null, REQUEST_TIMEOUT_SECONDS) { data, success, errorMsg, _ ->
            if (stopped || myGeneration != generation) {
                callback(null, DshRpcError("generation-cancelled", "Connection generation changed"))
                return@httpRequest
            }
            if (!success) {
                callback(null, DshRpcError("network-error", "Plugin request failed: $errorMsg"))
                return@httpRequest
            }
            try {
                if (data?.optBoolean("ok", false) == true) {
                    callback(data, null)
                } else {
                    val err = data?.optJSONObject("error")
                    val code = err?.optString("code", "unknown") ?: "unknown"
                    val message = err?.optString("message", "Unknown error") ?: "Unknown error"
                    callback(null, DshRpcError(code, message))
                }
            } catch (e: Exception) {
                callback(null, DshRpcError("parse-error", "Failed to parse plugin response: ${e.message}"))
            }
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 30
        const val RECONNECT_DELAY_MS = 1_000
    }
}

/** API facade used by the current Kuikly page while the raw timeline evolves. */
internal class DshRemoteHostRepository(
    network: NetworkModule,
    webSocket: DshWebSocketModule,
    private val connection: DshHostConnection,
    pagerId: String,
    onState: (DshHostRuntimeState) -> Unit = {},
    onQueueSnapshot: (String) -> Unit = {},
    onJobsSnapshot: (String) -> Unit = {},
    onSessionStatus: (String, Boolean) -> Unit = { _, _ -> },
    onProjection: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    onSessionEvent: (String, DshRawSessionEvent) -> Unit = { _, _ -> },
    onRemoteEvent: (String) -> Unit = {},
    onPendingInteraction: (String) -> Unit = {},
) : DshRepository {
    internal val store = DshHostStore()
    private val onQueueSnapshotHandler = onQueueSnapshot
    private val onJobsSnapshotHandler = onJobsSnapshot
    private val onSessionStatusHandler = onSessionStatus
    private val onProjectionHandler = onProjection
    private val onSessionEventHandler = onSessionEvent
    private val onRemoteEventHandler = onRemoteEvent
    private val onPendingInteractionHandler = onPendingInteraction
    private val runtime = DshHostConnectionRuntime(
        network = network,
        webSocket = webSocket,
        connection = connection,
        pagerId = pagerId,
        onFrame = ::handleFrame,
        onState = onState,
        onWorkspaceBaseline = { value ->
            val archived = value.optJSONArray("archivedSessionIds")
            val archivedIds = buildSet {
                if (archived != null) for (index in 0 until archived.length()) {
                    archived.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
            store.replaceWorkspaceBaseline(value.optJSONArray("items")?.toString() ?: "[]", archivedIds)
        },
        onSessionBaseline = { value -> store.replaceSessions(parseSessions(value)) },
        onQueueSnapshot = onQueueSnapshot,
        onJobsSnapshot = onJobsSnapshot,
        onSessionStatus = onSessionStatus,
        onProjection = onProjectionHandler,
        onSessionEvent = onSessionEventHandler,
        onRemoteEvent = onRemoteEvent,
    )
    private val activeStreams = mutableMapOf<String, ActiveStream>()

    private data class ActiveStream(
        val sessionId: String,
        val promptRpcId: String,
        val onDelta: (String, Boolean) -> Unit,
        val onComplete: (String) -> Unit,
        val onError: (String) -> Unit,
        var observed: Boolean = true,
        val accumulated: StringBuilder = StringBuilder(),
        var finalMessage: String = "",
        var failure: String = "",
    )

    fun currentConnectionState(): DshHostRuntimeState = runtime.currentState()
    fun isProductReady(): Boolean = runtime.currentState().phase == DshHostRuntimePhase.READY
    fun stop() = runtime.stop()

    fun respondApproval(
        rpcId: String,
        sessionId: String,
        approvalId: String,
        outcome: String,
        callback: (Boolean, String) -> Unit,
    ) {
        if (outcome != "allowed-once" && outcome != "rejected") {
            callback(false, "非法审批结果")
            return
        }
        runtime.respond(rpcId, JSONObject().apply {
            put("sessionId", sessionId)
            put("approvalId", approvalId)
            put("outcome", outcome)
        }, callback)
    }

    fun respondQuestion(
        rpcId: String,
        sessionId: String,
        answer: JSONObject,
        callback: (Boolean, String) -> Unit,
    ) {
        DshStreamLog.question(
            "repo.respondQuestion rpcId=$rpcId session=$sessionId answer='${DshStreamLog.preview(answer.toString(), 400)}'",
        )
        runtime.respond(rpcId, JSONObject().apply {
            put("sessionId", sessionId)
            put("answer", answer)
        }, callback)
    }

    /**
     * 用户主动关闭提问流程：以 ok=false + code=cancelled 拒绝整个等待，
     * 与原版 apiproxy respond 的 cancelled 分支一致（对应 wire 上 ASK_CANCELLED）。
     */
    fun respondQuestionCancel(
        rpcId: String,
        sessionId: String,
        callback: (Boolean, String) -> Unit,
    ) {
        DshStreamLog.question("repo.respondQuestionCancel rpcId=$rpcId session=$sessionId")
        runtime.respond(
            rpcId,
            JSONObject().apply { put("sessionId", sessionId) },
            callback,
            ok = false,
            errorCode = "cancelled",
            errorMessage = "the user closed this question request",
        )
    }

    fun clearPending(rpcId: String) {
        DshStreamLog.question("repo.clearPending rpcId=$rpcId")
        store.removePending(rpcId)
    }

    override fun loadCredentialSetup(onSuccess: (DshCredentialSetup) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.LLM_PROVIDERS, JSONObject()) { providersValue, providersError ->
            if (providersError != null || providersValue == null) {
                onError(providersError?.message ?: "llm.providers 返回为空")
                return@call
            }
            val providers = providersValue.optJSONArray("providers") ?: JSONArray()
            val active = (0 until providers.length()).any { index ->
                val provider = providers.optJSONObject(index) ?: return@any false
                provider.optString("provider") == DEEPSEEK_PROVIDER &&
                    provider.optString("settingsNs") == DEEPSEEK_SETTINGS_NS && provider.optBoolean("active")
            }
            if (!active) {
                onSuccess(DshCredentialSetup(false, false, false))
                return@call
            }
            call(DshHostProtocol.SETTINGS_DESCRIBE, JSONObject()) { settingsValue, settingsError ->
                if (settingsError != null || settingsValue == null) {
                    onError(settingsError?.message ?: "settings.describe 返回为空")
                    return@call
                }
                var credentialRef = DEEPSEEK_CREDENTIAL_REF
                var namespaceFound = false
                val namespaces = settingsValue.optJSONArray("namespaces") ?: JSONArray()
                for (index in 0 until namespaces.length()) {
                    val namespace = namespaces.optJSONObject(index) ?: continue
                    if (namespace.optString("ns") != DEEPSEEK_SETTINGS_NS) continue
                    namespaceFound = true
                    credentialRef = namespace.optJSONObject("value")?.optString("apiKeyEnv")
                        ?.takeIf { it.isNotEmpty() } ?: credentialRef
                    break
                }
                call(DshHostProtocol.CREDENTIALS_DESCRIBE, JSONObject().apply {
                    put("refs", JSONArray().apply { put(credentialRef) })
                }) { credentialsValue, credentialsError ->
                    if (credentialsError != null || credentialsValue == null) {
                        onError(credentialsError?.message ?: "credentials.describe 返回为空")
                        return@call
                    }
                    val credential = credentialsValue.optJSONObject("credentials")?.optJSONObject(credentialRef)
                    onSuccess(DshCredentialSetup(
                        true,
                        credential?.optBoolean("configured") == true,
                        settingsValue.optBoolean("writable") && namespaceFound && credential?.optBoolean("writable") == true,
                        credentialRef,
                    ))
                }
            }
        }
    }

    override fun saveDeepSeekApiKey(apiKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.CREDENTIALS_SET, JSONObject().apply {
            put("ref", DEEPSEEK_CREDENTIAL_REF)
            put("value", apiKey)
        }) { _, error -> if (error == null) onSuccess() else onError(error.message) }
    }

    override fun loadAgentPresets(onSuccess: (List<DshAgentPresetOption>) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.AGENT_PRESET_LIST, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "agentPreset.list 返回为空")
                return@call
            }
            val presets = value.optJSONArray("presets") ?: JSONArray()
            val result = mutableListOf<DshAgentPresetOption>()
            for (index in 0 until presets.length()) {
                val preset = presets.optJSONObject(index) ?: continue
                val id = preset.optString("id")
                if (id.isEmpty()) continue
                result += DshAgentPresetOption(
                    id,
                    preset.optString("name").ifEmpty { id },
                    preset.optString("description"),
                    preset.optBoolean("isDefault"),
                )
            }
            onSuccess(result)
        }
    }

    override fun loadHostVersion(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.HOST_DESCRIBE, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "host.describe 返回为空")
                return@call
            }
            onSuccess(value.optString("version").ifEmpty { "未知版本" })
        }
    }

    override fun describeSettings(onSuccess: (DshSettingsSnapshot) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SETTINGS_DESCRIBE, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "settings.describe 返回为空")
                return@call
            }
            var writable = value.optBoolean("writable")
            var permissionPreset = ""
            var permissionChoices = emptyList<DshSettingsChoice>()
            var permissionRevision = 0
            var localeValue = ""
            var localeRevision = 0
            var themeValue = ""
            var themeRevision = 0
            var defaultModelProvider = ""
            var defaultModelLabel = ""
            var defaultModelRevision = 0
            val namespaces = value.optJSONArray("namespaces") ?: JSONArray()
            for (index in 0 until namespaces.length()) {
                val namespace = namespaces.optJSONObject(index) ?: continue
                val ns = namespace.optString("ns")
                val nsValue = namespace.optJSONObject("value") ?: JSONObject()
                when (ns) {
                    "permission" -> {
                        permissionPreset = nsValue.optString("defaultPreset")
                        permissionRevision = namespace.optInt("revision")
                        permissionChoices = dshParseSchemaChoices(namespace.optJSONObject("schema"), "defaultPreset")
                    }
                    "locale" -> {
                        localeValue = nsValue.optString("preference")
                        localeRevision = namespace.optInt("revision")
                    }
                    "ui-theme" -> {
                        themeValue = nsValue.optString("preference")
                        themeRevision = namespace.optInt("revision")
                    }
                    "agent-default-model" -> {
                        defaultModelProvider = nsValue.optString("provider")
                        val providerName = nsValue.optString("providerName").ifEmpty { defaultModelProvider }
                        val model = nsValue.optString("model")
                        defaultModelLabel = if (model.isEmpty()) {
                            "未设置"
                        } else {
                            val effort = nsValue.optString("reasoningEffort").takeIf { it.isNotEmpty() }
                            if (effort == null) "$providerName · $model" else "$providerName · $model · $effort"
                        }
                        defaultModelRevision = namespace.optInt("revision")
                    }
                }
            }
            onSuccess(DshSettingsSnapshot(
                writable = writable,
                permissionPreset = permissionPreset,
                permissionChoices = permissionChoices,
                permissionRevision = permissionRevision,
                localeValue = localeValue,
                localeRevision = localeRevision,
                themeValue = themeValue,
                themeRevision = themeRevision,
                defaultModelProvider = defaultModelProvider,
                defaultModelLabel = defaultModelLabel,
                defaultModelRevision = defaultModelRevision,
            ))
        }
    }

    override fun updateSetting(ns: String, patch: JSONObject, expectedRevision: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SETTINGS_UPDATE, JSONObject().apply {
            put("ns", ns)
            put("patch", patch)
            if (expectedRevision > 0) put("expectedRevision", expectedRevision)
        }) { _, error -> if (error == null) onSuccess() else onError(error.message) }
    }

    override fun loadModels(sessionId: String, onSuccess: (DshSessionModels) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_MODELS, JSONObject().apply { put("sessionId", sessionId) }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.models 返回为空")
                return@call
            }
            val current = value.optJSONObject("current") ?: JSONObject()
            val currentProvider = current.optString("provider")
            val currentModel = current.optString("model")
            val currentEffort = current.optString("reasoningEffort").takeIf { it.isNotEmpty() }
            val options = mutableListOf<DshModelOption>()
            val groups = value.optJSONArray("groups") ?: JSONArray()
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                val provider = group.optString("id")
                val providerName = group.optString("name").ifEmpty { provider }
                val models = group.optJSONArray("models") ?: JSONArray()
                for (modelIndex in 0 until models.length()) {
                    val model = models.optJSONObject(modelIndex) ?: continue
                    val id = model.optString("id")
                    if (provider.isEmpty() || id.isEmpty()) continue
                    val reasoning = model.optJSONObject("reasoning")
                    val efforts = mutableListOf<DshReasoningEffort>()
                    reasoning?.optJSONArray("efforts")?.let { array ->
                        for (ei in 0 until array.length()) {
                            val e = array.optJSONObject(ei) ?: continue
                            val eid = e.optString("id")
                            if (eid.isEmpty()) continue
                            efforts += DshReasoningEffort(eid, e.optString("name").ifEmpty { eid }, e.optString("description"))
                        }
                    }
                    val effort = reasoning?.optString("defaultEffort")?.takeIf { it.isNotEmpty() }
                    options += DshModelOption(
                        provider, providerName, id, model.optString("name").ifEmpty { id }, model.optString("description"),
                        if (provider == currentProvider && id == currentModel) currentEffort ?: effort else effort,
                        efforts,
                        provider == currentProvider && id == currentModel,
                    )
                }
            }
            val selected = options.firstOrNull { it.selected } ?: DshModelOption(
                currentProvider, currentProvider, currentModel, currentModel.ifEmpty { "选择模型" }, reasoningEffort = currentEffort, selected = true,
            )
            onSuccess(DshSessionModels(selected, options, value.optBoolean("routable")))
        }
    }

    override fun selectModel(sessionId: String, option: DshModelOption, onSuccess: (DshModelOption) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_SELECT_MODEL, JSONObject().apply {
            put("sessionId", sessionId); put("provider", option.provider); put("model", option.model)
            option.reasoningEffort?.let { put("reasoningEffort", it) }
        }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.selectModel 返回为空")
                return@call
            }
            val selected = value.optJSONObject("selected") ?: JSONObject()
            onSuccess(option.copy(
                provider = selected.optString("provider").ifEmpty { option.provider },
                model = selected.optString("model").ifEmpty { option.model },
                reasoningEffort = selected.optString("reasoningEffort").takeIf { it.isNotEmpty() }
                    ?: option.reasoningEffort,
                reasoningEfforts = option.reasoningEfforts,
                selected = true,
            ))
        }
    }

    override fun loadSessions(onSuccess: (List<DshSession>) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_LIST, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.list 返回为空")
                return@call
            }
            val sessions = parseSessions(value)
            store.replaceSessions(sessions)
            onSuccess(sessions)
        }
    }

    /** 会话产生新消息时刷新其 updatedAt（消息时间），供抽屉 workspaceGroups 实时重排。 */
    fun touchSessionActivity(sessionId: String, updatedAt: Long) {
        val current = store.sessions[sessionId] ?: return
        if (current.updatedAt >= updatedAt) return
        store.sessions[sessionId] = current.copy(updatedAt = updatedAt)
    }

    private fun parseSessions(value: JSONObject): List<DshSession> {
        val items = value.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("sessionId")
                if (id.isEmpty()) continue
                val projections = item.optJSONObject("projections")?.optJSONObject("values")
                add(DshSession(
                    id = id,
                    title = projections?.optString("title")?.takeIf { it.isNotEmpty() } ?: "尚无标题",
                    workspace = "Host",
                    updatedLabel = item.optLong("updatedAt").takeIf { it > 0 }?.toString().orEmpty(),
                    updatedAt = item.optLong("updatedAt"),
                    running = item.optBoolean("running"), blank = item.optBoolean("blank"), cwd = item.optString("cwd"),
                    parentSessionId = item.optString("parentSessionId").takeIf { it.isNotEmpty() },
                    origin = item.optString("origin").takeIf { it.isNotEmpty() },
                    agentPreset = item.optString("agentPreset").takeIf { it.isNotEmpty() },
                    permission = item.optString("permission").takeIf { it.isNotEmpty() },
                ))
            }
        }
    }

    override fun createSession(workspaceId: String?, onSuccess: (String) -> Unit, onError: (String) -> Unit, permission: String?, agentPreset: String?) {
        val payload = JSONObject()
        workspaceId?.takeIf { it.isNotEmpty() }?.let { payload.put("workspaceId", it) }
        permission?.takeIf { it.isNotEmpty() }?.let { payload.put("permission", it) }
        agentPreset?.takeIf { it.isNotEmpty() }?.let { payload.put("agentPreset", it) }
        call(DshHostProtocol.SESSION_CREATE, payload) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.create 返回为空")
                return@call
            }
            val id = value.optString("sessionId")
            if (id.isEmpty()) onError("session.create 未返回 sessionId") else onSuccess(id)
        }
    }

    override fun loadHistory(sessionId: String, onSuccess: (List<DshMessage>) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_HISTORY, JSONObject().apply {
            put("sessionId", sessionId)
            put("maxMessages", HISTORY_PAGE_MESSAGES)
        }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.history 返回为空")
                return@call
            }
            onSuccess(parseHistory(value.optJSONArray("events") ?: JSONArray()))
        }
    }

    fun loadWebTimeline(
        sessionId: String,
        onSuccess: (List<DshWebTimelineItem>) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        call(DshHostProtocol.SESSION_HISTORY, JSONObject().apply {
            put("sessionId", sessionId)
            put("maxMessages", HISTORY_PAGE_MESSAGES)
        }) { value, error ->
            if (error != null || value == null) {
                DshStreamLog.i("history.fail session=$sessionId error='${error?.message ?: "empty"}'")
                onError(error?.message ?: "session.history 返回为空")
                return@call
            }
            onSuccess(DshWebTimelineParser.parseWebTimeline(value.optJSONArray("events") ?: JSONArray()))
        }
    }

    fun loadSkills(sessionId: String, onSuccess: (List<DshSkill>) -> Unit, onError: (String) -> Unit = {}) {
        call(DshHostProtocol.SKILL_LIST, JSONObject().apply { put("sessionId", sessionId) }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "skill.list 返回为空")
                return@call
            }
            val skills = buildList {
                val items = value.optJSONArray("skills") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    if (name.isEmpty()) continue
                    add(DshSkill(name, item.optString("description"), item.optString("whenToUse"), item.optBoolean("modelInvocable", true)))
                }
            }
            onSuccess(skills)
        }
    }

    fun goalEdit(sessionId: String, goal: DshGoalSnapshot, objective: String, callback: (DshRpcError?) -> Unit) =
        goalMutation(
            DshHostProtocol.GOAL_EDIT,
            sessionId,
            goal,
            enrich = { it.put("objective", objective) },
            callback = callback,
        )

    fun goalPause(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_PAUSE, sessionId, goal, callback = callback)

    fun goalResume(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_RESUME, sessionId, goal, callback = callback)

    fun goalClear(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_CLEAR, sessionId, goal, callback = callback)

    private fun goalMutation(
        method: String,
        sessionId: String,
        goal: DshGoalSnapshot,
        enrich: (JSONObject) -> Unit = {},
        callback: (DshRpcError?) -> Unit,
    ) {
        call(method, JSONObject().apply {
            put("sessionId", sessionId)
            put("ref", JSONObject().apply { put("id", goal.id); put("revision", goal.revision) })
            enrich(this)
        }) { _, error -> callback(error) }
    }

    fun loadAttachment(
        sessionId: String,
        attachmentId: String,
        callback: (String?, String?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_ATTACHMENT, JSONObject().apply {
            put("sessionId", sessionId)
            put("attachmentId", attachmentId)
        }) { value, error ->
            val data = value?.optString("data").orEmpty()
            val mediaType = value?.optJSONObject("attachment")?.optString("mediaType")?.takeIf { it.isNotEmpty() }
                ?: "image/png"
            if (error != null || data.isEmpty()) callback(null, error?.message ?: "attachment 返回为空")
            else callback("data:$mediaType;base64,$data", null)
        }
    }

    fun queue(sessionId: String): List<DshQueueItem> {
        val raw = store.queueSnapshots[sessionId] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val message = item.optJSONObject("message") ?: JSONObject()
                val text = textFromBlocks(message.optJSONArray("content"))
                add(DshQueueItem(
                    id = item.optString("id"),
                    placement = item.optString("placement"),
                    preview = text.lineSequence().firstOrNull().orEmpty(),
                    text = text.takeIf { it.isNotEmpty() },
                ))
            }
        }.filter { it.id.isNotEmpty() && it.placement == "queued" }
    }

    fun pendingInteractions(sessionId: String): Pair<DshPendingApproval?, DshPendingQuestion?> {
        var approval: DshPendingApproval? = null
        var question: DshPendingQuestion? = null
        store.pendingInteractions.forEach { (rpcId, raw) ->
            val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            if (payload.optString("sessionId") != sessionId) return@forEach
            when (payload.optString("type")) {
                "approval/requested" -> approval = DshPendingApproval(
                    rpcId = rpcId,
                    sessionId = sessionId,
                    approvalId = payload.optString("approvalId"),
                    toolName = payload.optString("toolName"),
                    callId = payload.optString("callId").takeIf { it.isNotEmpty() },
                    reason = payload.optString("reason").takeIf { it.isNotEmpty() },
                    command = approvalCommand(sessionId, payload.optString("callId")),
                )
                "question/requested" -> {
                    val questions = payload.optJSONArray("questions") ?: JSONArray()
                    question = DshPendingQuestion(
                        rpcId = rpcId,
                        sessionId = sessionId,
                        questions = (0 until questions.length()).mapNotNull { index ->
                            val item = questions.optJSONObject(index) ?: return@mapNotNull null
                            val options = item.optJSONArray("options") ?: JSONArray()
                            DshPendingQuestionItem(
                                id = item.optString("id"),
                                question = item.optString("question"),
                                header = item.optString("header"),
                                detail = item.optString("detail"),
                                options = (0 until options.length()).mapNotNull { optionIndex ->
                                    val option = options.optJSONObject(optionIndex) ?: return@mapNotNull null
                                    DshPendingQuestionOption(
                                        label = option.optString("label"),
                                        description = option.optString("description"),
                                    )
                                },
                                multiSelect = item.optBoolean("multiSelect") || item.optBoolean("multi_select"),
                            )
                        },
                    )
                }
            }
        }
        return approval to question
    }

    fun jobs(sessionId: String): List<DshJobItem> {
        val raw = store.jobSnapshots[sessionId] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            DshJobItem(
                id = id,
                kind = item.optString("kind"),
                label = item.optString("label"),
                status = item.optString("status"),
                detail = item.optString("detail"),
                startedAt = item.optLong("startedAt"),
                finishedAt = item.optString("finishedAt").takeIf { it.isNotEmpty() }?.toLongOrNull(),
            )
        }
    }

    fun workspaceGroups(): List<DshWorkspaceGroup> {
        val raw = store.workspaceBaseline
        val workspaces = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val archived = store.archivedSessionIds
        val sessionById = store.sessions.values
            .filterNot { it.blank || archived.contains(it.id) }
            .associateBy { it.id }
        val grouped = mutableSetOf<String>()
        val groups = (0 until workspaces.length()).mapNotNull { index ->
            val workspace = workspaces.optJSONObject(index) ?: return@mapNotNull null
            val workspaceId = workspace.optString("workspaceId")
            if (workspaceId.isEmpty()) return@mapNotNull null
            val sessionIds = workspace.optJSONArray("sessionIds") ?: JSONArray()
            val sessions = (0 until sessionIds.length()).mapNotNull { sessionIndex ->
                val sessionId = sessionIds.optString(sessionIndex)
                sessionId?.takeIf { it.isNotEmpty() }?.let(grouped::add)
                sessionById[sessionId]
            }.sortedByDescending { it.updatedAt }
            DshWorkspaceGroup(
                workspaceId = workspaceId,
                title = workspace.optString("title").ifEmpty { workspaceId },
                path = workspace.optString("path"),
                sessions = sessions,
            )
        }
        val ungrouped = sessionById.values.filterNot { grouped.contains(it.id) }.sortedByDescending { it.updatedAt }
        return if (ungrouped.isEmpty()) groups else groups + DshWorkspaceGroup(
            workspaceId = "",
            title = "未分组",
            path = "",
            sessions = ungrouped,
        )
    }

    fun workspaceIdForSession(sessionId: String): String? {
        val workspaces = runCatching { JSONArray(store.workspaceBaseline) }.getOrNull() ?: JSONArray()
        for (index in 0 until workspaces.length()) {
            val workspace = workspaces.optJSONObject(index) ?: continue
            val sessionIds = workspace.optJSONArray("sessionIds") ?: continue
            for (sessionIndex in 0 until sessionIds.length()) {
                if (sessionIds.optString(sessionIndex) == sessionId) {
                    return workspace.optString("workspaceId").takeIf { it.isNotEmpty() }
                }
            }
        }
        return null
    }

    fun blankSessionInWorkspace(workspaceId: String?): DshSession? {
        if (workspaceId == null) return store.sessions.values.firstOrNull { it.blank && it.cwd.isEmpty() }
        val workspaces = runCatching { JSONArray(store.workspaceBaseline) }.getOrNull() ?: JSONArray()
        for (index in 0 until workspaces.length()) {
            val workspace = workspaces.optJSONObject(index) ?: continue
            if (workspace.optString("workspaceId") != workspaceId) continue
            val sessionIds = workspace.optJSONArray("sessionIds") ?: continue
            for (sessionIndex in 0 until sessionIds.length()) {
                val sessionId = sessionIds.optString(sessionIndex)
                val session = store.sessions[sessionId] ?: continue
                if (session.blank) return session
            }
        }
        return null
    }

    private fun approvalCommand(sessionId: String, callId: String): String? {
        if (callId.isEmpty()) return null
        val events = store.sessionEvents[sessionId] ?: return null
        events.forEach { event ->
            if (event.type != "tool/call") return@forEach
            val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return@forEach
            val data = payload.optJSONObject("data") ?: return@forEach
            if (data.optString("callId") != callId) return@forEach
            val arguments = data.opt("arguments") ?: return@forEach
            return when (arguments) {
                is String -> arguments
                else -> {
                    val obj = arguments as? JSONObject
                    obj?.optString("command")?.takeIf { it.isNotEmpty() } ?: arguments.toString()
                }
            }
        }
        return null
    }

    fun updateQueue(
        sessionId: String,
        itemId: String,
        action: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_UPDATE_QUEUE, JSONObject().apply {
            put("sessionId", sessionId)
            put("itemId", itemId)
            put("action", action)
        }) { value, error -> callback(value, error) }
    }

    fun renameSession(
        sessionId: String,
        title: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_RENAME, JSONObject().apply {
            put("sessionId", sessionId)
            put("title", title)
        }) { value, error -> callback(value, error) }
    }

    fun forkSession(
        sessionId: String,
        atSeq: Int?,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        val payload = JSONObject().apply { put("sessionId", sessionId) }
        atSeq?.let { payload.put("atSeq", it) }
        call(DshHostProtocol.SESSION_FORK, payload) { value, error -> callback(value, error) }
    }

    fun archiveSession(
        sessionId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_ARCHIVE_SESSION, JSONObject().apply {
            put("sessionId", sessionId)
        }) { value, error -> callback(value, error) }
    }

    fun sessionExportUrl(sessionId: String, includeDescendants: Boolean = true): String {
        val encodedSessionId = dshEncodeQueryComponent(sessionId)
        return "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.SESSION_EXPORT_PATH}" +
            "?sessionId=$encodedSessionId" +
            "&includeDescendants=${if (includeDescendants) "true" else "false"}"
    }

    fun listDirectory(
        path: String?,
        callback: (DshDirectoryListing?, DshRpcError?) -> Unit,
    ) {
        val payload = JSONObject()
        path?.takeIf { it.isNotEmpty() }?.let { payload.put("path", it) }
        call(DshHostProtocol.HOST_LIST_DIRECTORY, payload) { value, error ->
            if (error != null || value == null) {
                callback(null, error ?: DshRpcError("internal", "host.listDirectory failed"))
                return@call
            }
            callback(parseDirectoryListing(value), null)
        }
    }

    fun createDirectory(
        path: String,
        name: String,
        callback: (String?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.HOST_CREATE_DIRECTORY, JSONObject().apply {
            put("path", path)
            put("name", name)
        }) { value, error ->
            if (error != null || value == null) {
                callback(null, error ?: DshRpcError("internal", "host.createDirectory failed"))
                return@call
            }
            callback(value.optString("path").takeIf { it.isNotEmpty() }, null)
        }
    }

    fun createWorkspace(
        path: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_CREATE, JSONObject().apply {
            put("path", path)
        }) { value, error -> callback(value, error) }
    }

    fun renameWorkspace(
        workspaceId: String,
        title: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_RENAME, JSONObject().apply {
            put("workspaceId", workspaceId)
            put("title", title)
        }) { value, error -> callback(value, error) }
    }

    fun deleteWorkspace(
        workspaceId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_DELETE, JSONObject().apply {
            put("workspaceId", workspaceId)
        }) { value, error -> callback(value, error) }
    }

    fun moveWorkspaceBefore(
        workspaceId: String,
        beforeWorkspaceId: String?,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        val payload = JSONObject().apply {
            put("workspaceId", workspaceId)
            beforeWorkspaceId?.takeIf { it.isNotEmpty() }?.let { put("beforeWorkspaceId", it) }
        }
        call(DshHostProtocol.WORKSPACE_INSERT_BEFORE, payload) { value, error ->
            callback(value, error)
        }
    }

    private fun parseDirectoryListing(value: JSONObject): DshDirectoryListing {
        fun entries(array: JSONArray?): List<DshDirectoryEntry> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                add(DshDirectoryEntry(
                    name = entry.optString("name"),
                    path = entry.optString("path"),
                    hidden = entry.optBoolean("hidden"),
                ))
            }
        }
        return DshDirectoryListing(
            path = value.optString("path"),
            home = value.optString("home"),
            crumbs = entries(value.optJSONArray("crumbs")),
            entries = entries(value.optJSONArray("entries")),
            truncated = value.optBoolean("truncated"),
        )
    }

    fun streamReply(pagerId: String, sessionId: String, prompt: String, onDelta: (String) -> Unit, onComplete: (String) -> Unit, onError: (String) -> Unit): DshStreamHandle {
        val call = runtime.call(DshHostProtocol.SESSION_PROMPT, JSONObject().apply {
            put("sessionId", sessionId); put("mode", "queue")
            put("content", JSONArray().apply { put(JSONObject().apply { put("type", "text"); put("text", prompt) }) })
            put("clientTimeZone", "UTC")
        }) { value, error, rpcId ->
            if (error != null) {
                if (dshIsTransportInterrupt(error.code, error.message)) {
                    DshStreamLog.i("prompt.hold-for-resync session=$sessionId rpcId=$rpcId code=${error.code}")
                    return@call
                }
                activeStreams.remove(rpcId); onError(error.message); return@call
            }
            val command = value?.optJSONObject("command")
            if (command != null) {
                activeStreams.remove(rpcId); onComplete(command.optString("text"))
            }
        }
        activeStreams[call.rpcId] = ActiveStream(sessionId, call.rpcId, { text, _ -> onDelta(text) }, onComplete, onError)
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(call.rpcId)
                call.cancel()
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    override fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle {
        val call = runtime.call(DshHostProtocol.SESSION_PROMPT, JSONObject().apply {
            put("sessionId", sessionId); put("mode", "queue")
            put("content", JSONArray().apply { put(JSONObject().apply { put("type", "text"); put("text", prompt) }) })
            put("clientTimeZone", "UTC")
        }) { value, error, rpcId ->
            if (error != null) {
                if (dshIsTransportInterrupt(error.code, error.message)) {
                    DshStreamLog.i("prompt.hold-for-resync session=$sessionId rpcId=$rpcId code=${error.code}")
                    return@call
                }
                activeStreams.remove(rpcId); onError(error.message); return@call
            }
            val command = value?.optJSONObject("command")
            if (command != null) {
                activeStreams.remove(rpcId); onComplete(command.optString("text"))
            }
        }
        activeStreams[call.rpcId] = ActiveStream(sessionId, call.rpcId, onDelta, onComplete, onError)
        DshStreamLog.log(LogLevel.INFO, "prompt.start", "prompt.start session=$sessionId rpcId=${call.rpcId} promptChars=${prompt.length}", sessionId, call.rpcId)
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(call.rpcId)
                call.cancel()
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    /**
     * 携带图片的会话发送：content 按官方 PromptContentPart 构造
     * [{type:"text",text}, {type:"image",mediaType,data,name}...]。
     * 仅发送通过预检的图片（SELECTED/UPLOADING/SENT），失败或超限项不进入 wire。
     * Host 落盘后以 ImageAttachmentRef 进入消息事实；本地草稿不写历史。
     */
    fun streamReplyWithImages(
        pagerId: String,
        sessionId: String,
        prompt: String,
        images: List<DshPendingImage>,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle {
        val content = JSONArray()
        if (prompt.isNotEmpty()) {
            content.put(JSONObject().apply { put("type", "text"); put("text", prompt) })
        }
        images.forEach { image ->
            if (image.state == DshImageDraftState.INVALID || image.dataBase64.isEmpty()) return@forEach
            content.put(JSONObject().apply {
                put("type", "image")
                put("mediaType", image.mediaType)
                put("data", image.dataBase64)
                if (image.name.isNotEmpty()) put("name", image.name)
            })
        }
        val call = runtime.call(DshHostProtocol.SESSION_PROMPT, JSONObject().apply {
            put("sessionId", sessionId); put("mode", "queue")
            put("content", content)
            put("clientTimeZone", "UTC")
        }) { value, error, rpcId ->
            if (error != null) {
                if (dshIsTransportInterrupt(error.code, error.message)) {
                    DshStreamLog.i("prompt.hold-for-resync session=$sessionId rpcId=$rpcId code=${error.code}")
                    return@call
                }
                activeStreams.remove(rpcId); onError(error.message); return@call
            }
            val command = value?.optJSONObject("command")
            if (command != null) {
                activeStreams.remove(rpcId); onComplete(command.optString("text"))
            }
        }
        activeStreams[call.rpcId] = ActiveStream(sessionId, call.rpcId, onDelta, onComplete, onError)
        DshStreamLog.log(
            LogLevel.INFO, "prompt.start",
            "prompt.start session=$sessionId rpcId=${call.rpcId} promptChars=${prompt.length} images=${images.size}",
            sessionId, call.rpcId,
        )
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(call.rpcId)
                call.cancel()
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    fun adoptLiveStream(
        sessionId: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle {
        detachLiveStreams(sessionId)
        val rpcId = "adopted-$sessionId"
        activeStreams[rpcId] = ActiveStream(sessionId, rpcId, onDelta, onComplete, onError)
        DshStreamLog.i("prompt.adopt-live session=$sessionId rpcId=$rpcId")
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(rpcId)
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    fun detachLiveStreams(sessionId: String) {
        val removed = activeStreams.entries
            .filter { it.value.sessionId == sessionId }
            .map { it.key }
        removed.forEach(activeStreams::remove)
        if (removed.isNotEmpty()) {
            DshStreamLog.i("prompt.detach-live session=$sessionId count=${removed.size}")
        }
    }

    private fun call(method: String, payload: JSONObject, callback: (JSONObject?, DshRpcError?) -> Unit) =
        runtime.call(method, payload) { value, error, _ -> callback(value, error) }

    private fun handleFrame(frame: DshDownlinkFrame) {
        val envelope = runCatching { JSONObject(frame.raw) }.getOrNull()
        if (envelope == null) {
            DshStreamLog.log(LogLevel.WARN, "host.frame.parse-error", "host.frame stream=${frame.stream.name.lowercase()} parse-error chars=${frame.raw.length}", null, null)
            return
        }
        val payload = envelope.optJSONObject("payload")
        if (payload == null) {
            DshStreamLog.log(LogLevel.INFO, "host.frame.no-payload", "host.frame stream=${frame.stream.name.lowercase()} no-payload envelopeType=${envelope.optString("type")} chars=${frame.raw.length}", null, null)
            return
        }
        val frameType = payload.optString("type")
        val inboundEvent = payload.optJSONObject("event")
        DshStreamLog.log(LogLevel.INFO, "host.frame", "host.frame stream=${frame.stream.name.lowercase()} type=$frameType session=${payload.optString("sessionId")} event=${inboundEvent?.optString("type").orEmpty()} seq=${inboundEvent?.optInt("seq", -1) ?: -1} chars=${frame.raw.length}", payload.optString("sessionId").takeIf { it.isNotEmpty() }, null)
        if (frame.stream == DshEventStream.HOST) {
            handleHostFrame(payload)
            return
        }
        if (frame.stream != DshEventStream.MUX) return
        when (frameType) {
            "session/subscribed" -> {
                store.applySubscribed(payload.optString("sessionId"), payload.optInt("lastSeq", -1))
                return
            }
            "session/queue" -> {
                store.replaceQueue(payload.optString("sessionId"), payload.optJSONArray("items")?.toString() ?: "[]")
                onQueueSnapshotHandler(payload.optString("sessionId"))
                return
            }
            "session/jobs" -> {
                store.replaceJobs(payload.optString("sessionId"), payload.optJSONArray("jobs")?.toString() ?: "[]")
                onJobsSnapshotHandler(payload.optString("sessionId"))
                return
            }
            "session/projection" -> {
                val value = payload.optJSONObject("value")?.toString() ?: payload.optString("value")
                onProjectionHandler(payload.optString("sessionId"), payload.optString("key"), value, payload.optInt("seq", -1))
                return
            }
            "approval/requested", "question/requested" -> {
                val rpcId = pendingInteractionRpcId(envelope, payload)
                DshStreamLog.question(
                    "mux.requested type=$frameType rpcId=$rpcId session=${payload.optString("sessionId")} envelopeRpc=${envelope.optString("rpcId")} payloadRpc=${payload.optString("rpcId")}",
                )
                if (rpcId.isEmpty()) {
                    DshStreamLog.question(
                        "mux.requested-drop empty-rpcId type=$frameType raw='${DshStreamLog.preview(frame.raw, 240)}'",
                    )
                    return
                }
                store.putPending(rpcId, payload.toString())
                onPendingInteractionHandler(payload.optString("sessionId"))
                return
            }
            "approval/resolved", "question/resolved" -> {
                val rpcId = pendingInteractionRpcId(envelope, payload)
                    .ifEmpty { payload.optString("questionRpcId") }
                    .ifEmpty { payload.optString("approvalId") }
                DshStreamLog.question(
                    "mux.resolved type=$frameType rpcId=$rpcId session=${payload.optString("sessionId")} outcome=${payload.optString("outcome")}",
                )
                store.removePending(rpcId)
                onPendingInteractionHandler(payload.optString("sessionId"))
                return
            }
        }
        if (frameType != "session/event") return
        val sessionId = payload.optString("sessionId")
        val event = payload.optJSONObject("event") ?: return
        val type = event.optString("type")
        val seq = event.optInt("seq", -1)
        // Preserve the optional host-computed tool view. History entries and
        // live mux frames must feed the same remote tool model.
        val eventEnvelope = JSONObject().apply {
            put("event", event)
            payload.optJSONObject("view")?.let { put("view", it) }
        }
        store.applySessionEvent(sessionId, seq, type, eventEnvelope.toString())
        if (seq > -1) onSessionEventHandler(sessionId, DshRawSessionEvent(seq, type, eventEnvelope.toString()))
        val data = event.optJSONObject("data") ?: JSONObject()
        // 会话事件摘要日志：type 用事件类型原值（turn/start、assistant/chunk…对齐 dsh session log 语义）。
        // 只记元数据；原文仅在内存 sessionEvents 保留，详情/导出时按需组稿。
        val evtRpcId = sessionEventSource(data)?.optString("rpcId").orEmpty().takeIf { it.isNotEmpty() }
        DshStreamLog.log(
            dshSessionEventLevel(type, data),
            type,
            dshSessionEventSummary(seq, type, data),
            sessionId,
            evtRpcId,
        )
        val source = sessionEventSource(data)
        val rpcId = source?.optString("rpcId").orEmpty()
        val active = resolveActiveStream(sessionId, type, rpcId)
        if (active == null) {
            DshStreamLog.i(
                "host.frame drop-no-active-stream session=$sessionId event=$type seq=$seq rpcId=$rpcId",
            )
            return
        }
        when (type) {
            "user/message" -> {
                val kind = source?.optString("kind").orEmpty()
                if (kind.isEmpty() || kind == "user") active.observed = true
            }
            "assistant/chunk" -> {
                val chunk = data.optJSONObject("chunk") ?: return
                val chunkType = chunk.optString("type")
                val text = chunk.optString("text").ifEmpty { chunk.optString("delta") }
                DshStreamLog.log(LogLevel.INFO, "mux.chunk", "mux.chunk session=$sessionId rpcId=${active.promptRpcId} type=$chunkType deltaChars=${text.length} acc=${active.accumulated.length}", sessionId, active.promptRpcId)
                when (chunkType) {
                    "text-delta", "text_delta", "text" -> text.takeIf { it.isNotEmpty() }?.let {
                        active.observed = true
                        active.accumulated.append(it)
                        active.onDelta(it, false)
                    }
                    "reasoning-delta", "reasoning_delta" -> text.takeIf { it.isNotEmpty() }?.let {
                        active.observed = true
                        active.onDelta(it, true)
                    }
                }
                if (chunkType == "finish") {
                    val reason = chunk.optJSONObject("reason")
                    if (reason?.optString("kind") == "error") {
                        active.failure = reason.optJSONObject("failure")?.optString("message").orEmpty()
                    }
                }
            }
            "assistant/message" -> {
                val message = data.optJSONObject("message") ?: data
                active.finalMessage = textFromBlocks(message.optJSONArray("content"))
            }
            "turn/end" -> {
                activeStreams.remove(active.promptRpcId)
                val reason = data.optJSONObject("reason")
                val error = reason?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }
                    ?: active.failure.takeIf { it.isNotEmpty() }
                val completed = active.accumulated.toString().ifEmpty { active.finalMessage }
                DshStreamLog.i(
                    "mux.turn-end session=$sessionId rpcId=${active.promptRpcId} acc=${active.accumulated.length} final=${active.finalMessage.length} error=${error ?: "-"} preview='${DshStreamLog.preview(completed)}'",
                )
                if (error != null) active.onError(error) else {
                    active.onComplete(completed)
                }
            }
        }
    }

    private fun sessionEventSource(data: JSONObject): JSONObject? =
        data.optJSONObject("source") ?: data.optJSONObject("message")?.optJSONObject("source")

    private fun resolveActiveStream(sessionId: String, type: String, rpcId: String): ActiveStream? {
        if (rpcId.isNotEmpty()) {
            activeStreams[rpcId]?.takeIf { it.sessionId == sessionId }?.let { return it }
        }
        return activeStreams.values.lastOrNull { it.sessionId == sessionId }
    }

    private fun handleHostFrame(payload: JSONObject) {
        when (payload.optString("type")) {
            "host/remote-event" -> {
                onRemoteEventHandler(payload.optString("event"))
                return
            }
            "host/session-added" -> {
                val id = payload.optString("sessionId")
                if (id.isEmpty()) return
                store.applySessionAdded(DshSession(
                    id = id,
                    title = "尚无标题",
                    workspace = "Host",
                    updatedLabel = "",
                    updatedAt = payload.optLong("updatedAt"),
                    blank = true,
                    cwd = payload.optString("cwd"),
                    parentSessionId = payload.optString("parentSessionId").takeIf { it.isNotEmpty() },
                    origin = payload.optString("origin").takeIf { it.isNotEmpty() },
                    agentPreset = payload.optString("agentPreset").takeIf { it.isNotEmpty() },
                    permission = payload.optString("permission").takeIf { it.isNotEmpty() },
                ))
            }
            "host/session-status" -> {
                val id = payload.optString("sessionId")
                val current = store.sessions[id] ?: return
                val running = payload.optBoolean("running")
                store.sessions[id] = current.copy(running = running, blank = if (running) false else current.blank)
                onSessionStatusHandler(id, running)
            }
            "host/session-removed" -> {
                val id = payload.optString("sessionId")
                store.sessions.remove(id)
                store.sessionEvents.remove(id)
                store.queueSnapshots.remove(id)
                store.jobSnapshots.remove(id)
                store.projections.remove(id)
            }
            "host/workspace-order-changed" -> {
                val order = payload.optJSONArray("workspaceIds")?.toString() ?: return
                store.reorderWorkspaces(order)
            }
        }
    }

    private fun parseHistory(events: JSONArray): List<DshMessage> {
        val messages = mutableListOf<DshMessage>()
        val partials = mutableMapOf<String, StringBuilder>()
        for (index in 0 until events.length()) {
            val entry = events.optJSONObject(index) ?: continue
            val event = entry.optJSONObject("event") ?: entry
            val view = entry.optJSONObject("view")
            val viewValue = view?.optJSONObject("view")
            val seq = event.optInt("seq", index)
            val type = event.optString("type")
            val data = event.optJSONObject("data") ?: continue
            when (type) {
                "user/message" -> textFromBlocks(data.optJSONArray("content")).takeIf { it.isNotEmpty() }?.let {
                    messages += DshMessage("user-$seq", DshMessageRole.USER, it)
                }
                "assistant/chunk" -> {
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val chunk = data.optJSONObject("chunk")
                    val text = chunk?.optString("text").orEmpty()
                    if (text.isNotEmpty()) partials.getOrPut(key) { StringBuilder() }.append(text)
                    if (chunk?.optString("type") == "finish" && chunk.optJSONObject("reason")?.optString("kind") == "error") {
                        chunk.optJSONObject("reason")?.optJSONObject("failure")?.optString("message")?.takeIf { it.isNotEmpty() }?.let {
                            messages += DshMessage("turn-error-$seq", DshMessageRole.ERROR, it)
                        }
                    }
                }
                "assistant/message" -> {
                    val message = data.optJSONObject("message") ?: data
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val text = textFromBlocks(message.optJSONArray("content")).ifEmpty { partials[key]?.toString().orEmpty() }
                    if (text.isNotEmpty()) messages += DshMessage("assistant-$seq", DshMessageRole.ASSISTANT, text)
                    partials.remove(key)
                }
                "tool/call" -> messages += DshMessage("tool-$seq", DshMessageRole.TOOL, "正在执行 ${data.optString("name").ifEmpty { "工具" }}", toolName = data.optString("name").takeIf { it.isNotEmpty() })
                "turn/end" -> data.optJSONObject("reason")?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }?.let {
                    messages += DshMessage("turn-error-$seq", DshMessageRole.ERROR, it)
                }
            }
        }
        partials.forEach { (key, text) -> if (text.isNotEmpty()) messages += DshMessage("partial-$key", DshMessageRole.ASSISTANT, text.toString(), streaming = true) }
        return messages.filterNot { it.isRuntimeContextSnapshot() }
    }

    private fun DshWebTimelineItem.isRuntimeContextSnapshot(): Boolean {
        return text.startsWith("Current runtime context. This snapshot supersedes earlier runtime-context snapshots.")
    }

    private fun contextSourceLabel(source: JSONObject?): String {
        if (source == null) return "未知来源"
        return when {
            source.optString("kind") == "skill-invocation" -> source.optString("name").ifEmpty { "skill" }
            source.optString("plugin").isNotEmpty() -> source.optString("plugin")
            source.optString("name").isNotEmpty() -> source.optString("name")
            source.optString("kind").isNotEmpty() -> source.optString("kind")
            else -> "未知来源"
        }
    }

    private fun toolInputSummary(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }

    private fun toolOutputSummary(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is JSONArray -> textFromBlocks(value)
        else -> value.toString()
    }

    private fun toolCardType(view: JSONObject): DshToolCardType = when (view.optString("card")) {
        "terminal" -> DshToolCardType.TERMINAL
        "read" -> DshToolCardType.READ
        "diff" -> DshToolCardType.DIFF
        "search" -> DshToolCardType.SEARCH
        "web" -> DshToolCardType.WEB
        else -> DshToolCardType.GENERIC
    }

    private fun diffBody(view: JSONObject): String {
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

    private fun toolResultBody(type: DshToolCardType, view: JSONObject, fallback: String): String {
        return when (type) {
            DshToolCardType.TERMINAL -> view.optString("output").ifEmpty { fallback }
            DshToolCardType.READ -> readBody(view)
            DshToolCardType.DIFF -> diffBody(view)
            DshToolCardType.SEARCH -> searchBody(view)
            DshToolCardType.WEB -> webBody(view)
            else -> fallback
        }
    }

    private fun readBody(view: JSONObject): String {
        val lines = view.optJSONArray("lines") ?: JSONArray()
        return buildString {
            for (index in 0 until lines.length()) {
                val line = lines.optJSONObject(index) ?: continue
                appendLine("${line.optInt("number")}\t${line.optString("text")}")
            }
        }.trim()
    }

    private fun searchBody(view: JSONObject): String {
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

    private fun webBody(view: JSONObject): String {
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

    private fun textFromBlocks(blocks: JSONArray?): String {
        if (blocks == null) return ""
        return buildString {
            for (index in 0 until blocks.length()) {
                val block = blocks.optJSONObject(index) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
    }

    /**
     * 调用 DSH 插件 HTTP 端点，转发到当前连接 runtime。
     */
    fun callPlugin(
        endpoint: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = runtime.callPlugin(endpoint, payload, callback)

    private companion object {
        const val DEEPSEEK_PROVIDER = "deepseek-official"
        const val DEEPSEEK_SETTINGS_NS = "llm-deepseek"
        const val DEEPSEEK_CREDENTIAL_REF = "DEEPSEEK_API_KEY"
        const val HISTORY_PAGE_MESSAGES = 80
    }
}

internal fun pendingInteractionRpcId(envelope: JSONObject, payload: JSONObject): String {
    val nested = payload.optJSONObject("payload")
    return listOf(
        envelope.optString("rpcId"),
        payload.optString("rpcId"),
        nested?.optString("rpcId").orEmpty(),
    ).firstOrNull { it.isNotEmpty() }.orEmpty()
}

internal fun parseRespondReceipt(data: JSONObject): Pair<Boolean, String> {
    val result = data.optJSONObject("result")
    val value = result?.optJSONObject("value")
    val accepted = jsonFlag(data, "accepted")
        ?: jsonFlag(value, "accepted")
        ?: false
    val reason = data.optString("reason")
        .ifEmpty { value?.optString("reason").orEmpty() }
        .ifEmpty { result?.optJSONObject("error")?.optString("message").orEmpty() }
        .ifEmpty { if (accepted) "" else "bad-response" }
    return accepted to reason
}

private fun jsonFlag(obj: JSONObject?, key: String): Boolean? {
    if (obj == null) return null
    val raw = obj.opt(key) ?: return null
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.equals("true", ignoreCase = true)
        else -> obj.optBoolean(key)
    }
}

internal fun dshEncodeQueryComponent(value: String): String {
    val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
    return buildString {
        value.encodeToByteArray().forEach { byte ->
            val unsigned = byte.toInt() and 0xFF
            val char = unsigned.toChar()
            if (char in allowed) append(char)
            else append('%').append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
