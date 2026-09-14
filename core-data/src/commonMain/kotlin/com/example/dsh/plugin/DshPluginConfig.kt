package com.example.dsh.plugin

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 插件配置卡字段类型。密钥字段不入 settings，改走 credentials。 */
enum class DshPluginFieldKind { TEXT, NUMBER, SECRET }

data class DshPluginConfigField(
    val key: String,
    val label: String,
    val hint: String,
    val kind: DshPluginFieldKind,
    val value: String,
)

data class DshPluginConfigCard(
    val namespace: String,
    val title: String,
    val description: String,
    val revision: Int,
    val fields: List<DshPluginConfigField>,
    val secretSet: Boolean = false,
    val secretRef: String = "DEEPSEEK_API_KEY",
)

data class DshPluginConfigState(
    val writable: Boolean = true,
    val cards: List<DshPluginConfigCard> = emptyList(),
)

/** 一次插件配置保存：字段改动走 settings.mutate 的 set/unset 路径操作，密钥走 credentials.set。 */
data class DshPluginConfigSave(
    val namespace: String,
    val ops: JSONArray,
    val expectedRevision: Int,
    val credentialRef: String = "",
    val credentialValue: String = "",
)

private data class FieldSpec(val key: String, val label: String, val hint: String, val kind: DshPluginFieldKind)

private val SHELL_FIELDS = listOf(
    FieldSpec("timeoutMs", "命令超时（毫秒）", "单条命令允许运行多久，超时即终止。", DshPluginFieldKind.NUMBER),
    FieldSpec("maxOutputBytes", "单流输出上限（字节）", "超出部分会转存到临时文件，而不是被丢弃。", DshPluginFieldKind.NUMBER),
)

private val AGENT_LOOP_FIELDS = listOf(
    FieldSpec("maxParallelToolCalls", "并行工具调用数", "同一步内最多同时运行多少个可并行的调用。", DshPluginFieldKind.NUMBER),
)

private val WEB_SEARCH_FIELDS = listOf(
    FieldSpec("apiKey", "API Key", "不写入设置文件。留空表示保持当前密钥。", DshPluginFieldKind.SECRET),
    FieldSpec("baseURL", "接口地址", "留空则使用提供方默认地址。", DshPluginFieldKind.TEXT),
    FieldSpec("maxUses", "单次请求最多搜索次数", "一次请求在必须作答前最多可以搜索多少次。", DshPluginFieldKind.NUMBER),
)

fun parseDshPluginConfig(value: JSONObject): DshPluginConfigState {
    val writable = value.optBoolean("writable", true)
    val namespaces = value.optJSONArray("namespaces") ?: JSONArray()
    val byNs = mutableMapOf<String, JSONObject>()
    for (index in 0 until namespaces.length()) {
        val namespace = namespaces.optJSONObject(index) ?: continue
        byNs[namespace.optString("ns")] = namespace
    }
    val cards = listOfNotNull(
        byNs["shell"]?.let { cardOf(it, "shell", "终端", "限制 agent 运行的每一条命令。", SHELL_FIELDS) },
        byNs["agent-loop"]?.let { cardOf(it, "agent-loop", "Agent 循环", "Agent 如何派发工具调用。", AGENT_LOOP_FIELDS) },
        byNs["web-search-deepseek"]?.let {
            cardOf(it, "web-search-deepseek", "网页搜索", "DeepSeek 搜索提供方。", WEB_SEARCH_FIELDS).copy(
                secretSet = dshPluginSecretSet(it, "apiKey"),
                secretRef = it.optJSONObject("value")?.optString("apiKeyEnv").orEmpty()
                    .ifEmpty { "DEEPSEEK_API_KEY" },
            )
        },
    )
    return DshPluginConfigState(writable = writable, cards = cards)
}

private fun cardOf(
    namespace: JSONObject,
    ns: String,
    title: String,
    description: String,
    specs: List<FieldSpec>,
): DshPluginConfigCard {
    val values = namespace.optJSONObject("value") ?: JSONObject()
    val fields = specs.map { spec ->
        DshPluginConfigField(
            key = spec.key,
            label = spec.label,
            hint = spec.hint,
            kind = spec.kind,
            value = if (spec.kind == DshPluginFieldKind.SECRET) "" else dshPluginValueText(values.opt(spec.key)),
        )
    }
    return DshPluginConfigCard(
        namespace = ns,
        title = title,
        description = description,
        revision = namespace.optInt("revision"),
        fields = fields,
    )
}

/** 读取一个配置值并格式化为输入框文本；缺失或 JSON null 返回空串。 */
fun dshPluginValueText(value: Any?): String = when (value) {
    null -> ""
    is Number -> if (value.toDouble() == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    else -> value.toString().takeUnless { it == "null" } ?: ""
}

/** Host 报告的密钥槽是否已配置，用于网页搜索的“已配置密钥”标记。 */
fun dshPluginSecretSet(namespace: JSONObject, pathSegment: String): Boolean {
    val secrets = namespace.optJSONArray("secrets") ?: return false
    for (index in 0 until secrets.length()) {
        val secret = secrets.optJSONObject(index) ?: continue
        val path = secret.optJSONArray("path") ?: continue
        for (pi in 0 until path.length()) {
            if (path.optString(pi) == pathSegment) return secret.optBoolean("set")
        }
    }
    return false
}
