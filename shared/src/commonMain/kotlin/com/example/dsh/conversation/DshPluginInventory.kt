package com.example.dsh.conversation

import com.example.dsh.infrastructure.LogSanitizer
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal data class DshPluginEntry(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val phase: String?,
    val configSummary: String,
    val failureSummary: String?,
    /** 完整脱敏配置（详情页展示），旧桥接为空。 */
    val configDetail: String = "",
    /** 脱敏后的 inject 声明，旧桥接为空。 */
    val injectDetail: String = "",
    /** 条件表达式形式的 disabled 原始值；非表达式为 null。 */
    val disabledExpr: String? = null,
    /** 桥接是否允许在本 App 启停该插件。 */
    val canToggle: Boolean = false,
    /** 不可启停时的可读原因。 */
    val toggleHint: String = "",
)

private fun JSONObject.optNullableString(key: String): String? =
    optString(key).takeIf { it.isNotEmpty() && it != "null" }

internal fun parseDshPluginInventory(value: JSONObject): List<DshPluginEntry> {
    check(value.optInt("version") == 1) { "插件清单协议版本不兼容，请更新 Host 桥接插件" }
    val entries = value.optJSONArray("entries") ?: error("插件清单缺少 entries")
    val ids = mutableSetOf<String>()
    return (0 until entries.length()).map { index ->
        val item = entries.optJSONObject(index) ?: error("插件条目无效")
        val id = item.optString("entryId")
        val name = item.optString("moduleName")
        check(id.isNotBlank() && name.isNotBlank() && ids.add(id) && item.opt("enabled") is Boolean) { "插件条目字段无效" }
        val phase = item.optNullableString("fiberPhase")
        DshPluginEntry(
            id = id,
            name = name,
            enabled = item.optBoolean("enabled"),
            phase = phase,
            configSummary = LogSanitizer.sanitize(item.optString("configSummary").ifEmpty { "Host 未提供配置摘要" }),
            failureSummary = if (phase == "failed") {
                LogSanitizer.sanitize(item.optString("failureSummary").ifEmpty { "Host 未提供失败原因" })
            } else null,
            configDetail = LogSanitizer.sanitize(item.optNullableString("configDetail") ?: ""),
            injectDetail = LogSanitizer.sanitize(item.optNullableString("injectDetail") ?: ""),
            disabledExpr = item.optNullableString("disabledExpr"),
            canToggle = item.optBoolean("canToggle", false),
            toggleHint = item.optNullableString("toggleHint") ?: "",
        )
    }
}

internal fun filterDshPlugins(entries: List<DshPluginEntry>, keyword: String, phase: String): List<DshPluginEntry> {
    val query = keyword.trim()
    return entries.filter {
        (query.isEmpty() || it.name.contains(query, true) || it.id.contains(query, true)) &&
            (phase.isEmpty() || if (phase == "none") it.phase == null else it.phase == phase)
    }
}
