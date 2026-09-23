package com.example.dsh.dsh

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 官方 `pluginInventory/list` 返回的一条只读插件条目。
 *
 * 只有官方快照字段（Loader entryId / moduleName / enabled / fiberPhase）；
 * 不含桥接插件才能提供的配置摘要、失败原因、启停提示等扩展字段。
 */
internal data class DshPluginEntry(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val phase: String?,
)

private fun JSONObject.pluginNullableString(key: String): String? =
    optString(key).takeIf { it.isNotEmpty() && it != "null" }

/**
 * 解析官方 `pluginInventory/list` 快照：
 * `{ entries: [{ entryId, moduleName, enabled, fiberPhase }] }`。
 *
 * 官方快照没有 `version` 字段，不要求桥接清单的协议版本。
 */
internal fun parseDshPluginInventory(value: JSONObject): List<DshPluginEntry> {
    val entries = value.optJSONArray("entries") ?: error("插件清单缺少 entries")
    val ids = mutableSetOf<String>()
    return (0 until entries.length()).map { index ->
        val item = entries.optJSONObject(index) ?: error("插件条目无效")
        val id = item.optString("entryId")
        val name = item.optString("moduleName")
        check(id.isNotBlank() && name.isNotBlank() && ids.add(id) && item.has("enabled")) { "插件条目字段无效" }
        DshPluginEntry(
            id = id,
            name = name,
            enabled = item.optBoolean("enabled"),
            phase = item.pluginNullableString("fiberPhase"),
        )
    }
}

/** 插件列表的本地过滤：关键词匹配名称或 ID；phase 为空表示不过滤。 */
internal fun filterDshPlugins(entries: List<DshPluginEntry>, keyword: String, phase: String): List<DshPluginEntry> {
    val query = keyword.trim()
    return entries.filter {
        (query.isEmpty() || it.name.contains(query, true) || it.id.contains(query, true)) &&
            (phase.isEmpty() || if (phase == "none") it.phase == null else it.phase == phase)
    }
}

internal fun pluginPhaseLabel(phase: String?): String = when (phase) {
    null -> "无运行实例"
    "pending" -> "等待依赖"
    "loading" -> "加载中"
    "active" -> "已挂载"
    "failed" -> "挂载失败"
    "unloading" -> "卸载中"
    else -> phase
}

internal fun pluginPhaseColor(phase: String?): Color = when (phase) {
    "active" -> Color(0xFF2FA36B)
    "failed" -> Color(0xFFEC1313)
    null -> Color(0xFF81858C)
    else -> Color(0xFF4176E6)
}
