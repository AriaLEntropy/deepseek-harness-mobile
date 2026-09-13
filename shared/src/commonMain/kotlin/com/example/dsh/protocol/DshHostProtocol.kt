package com.example.dsh.protocol

import com.example.dsh.infrastructure.LogLevel
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.models.DshSettingsChoice

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
    const val SETTINGS_MUTATE = "settings.mutate"
    const val CREDENTIALS_DESCRIBE = "credentials.describe"
    const val CREDENTIALS_SET = "credentials.set"
    const val CREDENTIALS_UNSET = "credentials.unset"
    const val LLM_PROVIDERS = "llm.providers"
    const val SKILL_LIST = "skill.list"
    const val AGENT_PRESET_LIST = "agentPreset.list"
    const val GOAL_EDIT = "goal.edit"
    const val GOAL_PAUSE = "goal.pause"
    const val GOAL_RESUME = "goal.resume"
    const val GOAL_CLEAR = "goal.clear"
    const val RESPOND_PATH = "$API_PREFIX/respond"
    const val SESSION_EXPORT_PATH = "$API_PREFIX/session.export"

    /** host-plugin 自带存储的通用文件端点（pre-0.1.5 Host backport）。 */
    const val ATTACHMENT_UPLOAD_PATH = "$API_PREFIX/mobile-attachment/v1/upload"
    const val ATTACHMENT_DELETE_PATH = "$API_PREFIX/mobile-attachment/v1/delete"
    const val ATTACHMENT_CONFIG_PATH = "$API_PREFIX/mobile-attachment/v1/config"
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

/** 会话事件摘要：type + 关键元数据（不含 delta 正文 / 工具 JSON 全文）。 */
internal fun dshSessionEventSummary(seq: Int, type: String, data: JSONObject): String {
    val sb = StringBuilder()
    sb.append("evtSeq=$seq")
    fun field(name: String) {
        val raw = data.opt(name)
        val v = if (raw is String || raw is Number || raw is Boolean) raw.toString() else ""
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
            data.optJSONObject("reason")?.let { sb.append(" reason=${it.optString("kind")} errorCode=${it.optJSONObject("error")?.optString("code").orEmpty()}") }
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
            sb.append(" chunkType=${data.optJSONObject("chunk")?.optString("type").orEmpty()}")
            sb.append(" size=${data.opt("chunk")?.toString()?.length ?: 0}")
        }
        "assistant/attempt" -> {
            field("turn")
            field("step")
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

/** 会话事件日志等级：结构性 chunk 记 DEBUG，带错误/失败记 WARN，其余 INFO。 */
internal fun dshSessionEventLevel(type: String, data: JSONObject): LogLevel = when {
    type == "assistant/chunk" -> LogLevel.DEBUG
    type == "tool/result" && data.optJSONObject("error") != null -> LogLevel.WARN
    type == "turn/end" && data.optString("reason").contains("error", ignoreCase = true) -> LogLevel.WARN
    else -> LogLevel.INFO
}

/**
 * 该会话事件是否需要落一条日志。
 *
 * 沿用 DSH 事件类型；App 诊断只记录事件摘要，不复制 Host 的完整会话日志。
 * 按 Task 6 保留非增量 chunk 的 DEBUG 元数据，逐 token 内容及其兼容写法不采集。
 */
internal fun dshShouldLogSessionEvent(type: String, data: JSONObject): Boolean {
    if (type != "assistant/chunk") return true
    return when (data.optJSONObject("chunk")?.optString("type")?.replace('_', '-')) {
        "text", "text-delta", "reasoning-delta", "tool-call-delta" -> false
        else -> true
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
