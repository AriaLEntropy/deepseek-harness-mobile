package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.plugin.DshPluginConfigSave
import com.example.dsh.plugin.DshPluginEntry
import com.example.dsh.plugin.DshPluginFieldKind
import com.example.dsh.plugin.filterDshPlugins
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.example.dsh.base.setTimeout
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.ui.plugin.pluginActionLabel

internal fun DshHomePage.openPluginInventory() {
    dismissKeyboard()
    settingsPageVisible = false
    pluginInventoryVisible = true
    pluginActiveTab = "config"
    pluginSearchInput = ""
    pluginSearchHasText = false
    pluginKeyword = ""
    refreshPluginInventory()
    loadPluginConfig()
}

/** 返回键：先关确认弹窗，再收起展开卡片，最后退出插件页。 */

internal fun DshHomePage.handlePluginInventoryBack() {
    when {
        pluginConfirmAction.isNotEmpty() -> cancelPluginAction()
        pluginExpandedId.isNotEmpty() -> pluginExpandedId = ""
        else -> closePluginInventory()
    }
}

internal fun DshHomePage.closePluginInventory() {
    pluginRequestVersion++
    pluginInventoryVisible = false
    pluginInventoryLoading = false
    pluginExpandedId = ""
    pluginActionTarget = null
    pluginConfirmAction = ""
    pluginBusyId = ""
    pluginActionError = ""
    pluginNotice = ""
    pluginConfigLoading = false
    pluginConfigError = ""
    pluginConfigCards.clear()
    pluginConfigDrafts = emptyMap()
    pluginConfigSecretDrafts = emptyMap()
    pluginConfigCollapsed = emptySet()
    pluginConfigBusyNamespace = ""
    pluginConfigCardError = emptyMap()
    pluginConfigCardNotice = emptyMap()
    settingsPageVisible = true
}

internal fun DshHomePage.selectPluginTab(tab: String) {
    pluginActiveTab = tab
    if (tab == "config" && pluginConfigCards.isEmpty() && !pluginConfigLoading) loadPluginConfig()
}

internal fun DshHomePage.loadPluginConfig() {
    val remote = remoteRepo ?: run {
        pluginConfigLoading = false; pluginConfigError = "请先连接 Host"; return
    }
    val connection = activeConnectionId
    pluginConfigLoading = true
    pluginConfigError = ""
    remote.loadPluginConfig({ state ->
        if (pageAlive && pluginInventoryVisible && connection == activeConnectionId && remote === repository) {
            pluginConfigLoading = false
            pluginConfigWritable = state.writable
            // 首次进入时配置卡默认收起（对齐原版）；后续刷新保留用户展开状态。
            val firstLoad = pluginConfigCards.isEmpty()
            pluginConfigCards.clear()
            pluginConfigCards.addAll(state.cards)
            if (firstLoad) pluginConfigCollapsed = state.cards.map { it.namespace }.toSet()
            pluginConfigDrafts = emptyMap()
            pluginConfigSecretDrafts = emptyMap()
            pluginConfigCardError = emptyMap()
        }
    }, { error ->
        if (pageAlive && pluginInventoryVisible && connection == activeConnectionId && remote === repository) {
            pluginConfigLoading = false
            pluginConfigError = error
        }
    })
}

internal fun DshHomePage.pluginConfigDraft(namespace: String, key: String): String {
    val card = pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return ""
    val fallback = card.fields.firstOrNull { it.key == key }?.value ?: ""
    return pluginConfigDrafts["$namespace::$key"] ?: fallback
}

internal fun DshHomePage.pluginConfigSecretDraft(namespace: String): String = pluginConfigSecretDrafts[namespace] ?: ""

internal fun DshHomePage.isPluginConfigCollapsed(namespace: String): Boolean = namespace in pluginConfigCollapsed

internal fun DshHomePage.hasPluginConfigChanges(namespace: String): Boolean {
    val card = pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return false
    if ((pluginConfigSecretDrafts[namespace] ?: "").isNotEmpty()) return true
    return card.fields.any { it.kind != DshPluginFieldKind.SECRET && pluginConfigDraft(namespace, it.key) != it.value }
}

internal fun DshHomePage.onPluginConfigDraft(namespace: String, key: String, value: String) {
    pluginConfigDrafts = pluginConfigDrafts + ("$namespace::$key" to value)
}

internal fun DshHomePage.onPluginConfigSecretDraft(namespace: String, value: String) {
    pluginConfigSecretDrafts = pluginConfigSecretDrafts + (namespace to value)
}

internal fun DshHomePage.togglePluginConfigCollapsed(namespace: String) {
    pluginConfigCollapsed = if (namespace in pluginConfigCollapsed) {
        pluginConfigCollapsed - namespace
    } else {
        pluginConfigCollapsed + namespace
    }
}

internal fun DshHomePage.discardPluginConfigCard(namespace: String) {
    clearPluginConfigDrafts(namespace)
    pluginConfigCardNotice = pluginConfigCardNotice - namespace
}

internal fun DshHomePage.savePluginConfigCard(namespace: String) {
    val card = pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return
    if (pluginConfigBusyNamespace.isNotEmpty()) return
    val remote = remoteRepo ?: run {
        pluginConfigCardError = pluginConfigCardError + (namespace to "请先连接 Host"); return
    }
    val ops = JSONArray()
    var invalid = ""
    for (field in card.fields) {
        if (field.kind == DshPluginFieldKind.SECRET) continue
        val draft = pluginConfigDraft(namespace, field.key)
        if (draft == field.value) continue
        val path = JSONArray().apply { put(field.key) }
        if (field.kind == DshPluginFieldKind.NUMBER) {
            val text = draft.trim()
            if (text.isEmpty()) {
                ops.put(JSONObject().apply { put("op", "unset"); put("path", path) })
            } else {
                val parsed = text.toIntOrNull()
                if (parsed == null) {
                    invalid = "「${field.label}」请填数字，或留空使用默认值"
                    break
                }
                ops.put(JSONObject().apply { put("op", "set"); put("path", path); put("value", parsed) })
            }
        } else {
            ops.put(JSONObject().apply { put("op", "set"); put("path", path); put("value", draft) })
        }
    }
    if (invalid.isNotEmpty()) {
        pluginConfigCardError = pluginConfigCardError + (namespace to invalid)
        return
    }
    val secret = pluginConfigSecretDraft(namespace).trim()
    if (ops.length() == 0 && secret.isEmpty()) return
    val save = DshPluginConfigSave(
        namespace = namespace,
        ops = ops,
        expectedRevision = card.revision,
        credentialRef = if (secret.isNotEmpty()) card.secretRef else "",
        credentialValue = secret,
    )
    pluginConfigBusyNamespace = namespace
    pluginConfigCardError = pluginConfigCardError - namespace
    pluginConfigCardNotice = pluginConfigCardNotice - namespace
    remote.savePluginConfig(save, {
        if (pageAlive && pluginInventoryVisible) {
            pluginConfigBusyNamespace = ""
            clearPluginConfigDrafts(namespace)
            pluginConfigCardNotice = pluginConfigCardNotice + (namespace to "已保存")
            loadPluginConfig()
        }
    }, { error ->
        if (pageAlive && pluginInventoryVisible) {
            pluginConfigBusyNamespace = ""
            pluginConfigCardError = pluginConfigCardError + (namespace to error)
        }
    })
}

internal fun DshHomePage.clearPluginConfigDrafts(namespace: String) {
    val prefix = "$namespace::"
    pluginConfigDrafts = pluginConfigDrafts.filterKeys { !it.startsWith(prefix) }
    pluginConfigSecretDrafts = pluginConfigSecretDrafts - namespace
    pluginConfigCardError = pluginConfigCardError - namespace
}

internal fun DshHomePage.togglePluginExpanded(entry: DshPluginEntry) {
    pluginExpandedId = if (pluginExpandedId == entry.id) "" else entry.id
}

/** 启用直接执行；停用先弹二次确认。 */

internal fun DshHomePage.requestPluginToggle(entry: DshPluginEntry, enable: Boolean) {
    if (pluginBusyId.isNotEmpty()) return
    pluginActionTarget = entry
    pluginActionError = ""
    pluginNotice = ""
    if (enable) performPluginAction("enable") else pluginConfirmAction = "disable"
}

/** 重载先弹二次确认。 */

internal fun DshHomePage.requestPluginReload(entry: DshPluginEntry) {
    if (pluginBusyId.isNotEmpty()) return
    pluginActionTarget = entry
    pluginActionError = ""
    pluginNotice = ""
    pluginConfirmAction = "reload"
}

internal fun DshHomePage.cancelPluginAction() {
    pluginConfirmAction = ""
    pluginActionError = ""
}

internal fun DshHomePage.confirmPluginAction() {
    val action = pluginConfirmAction
    pluginConfirmAction = ""
    if (action.isNotEmpty()) performPluginAction(action)
}

internal fun DshHomePage.performPluginAction(action: String) {
    val entry = pluginActionTarget ?: run {
        pluginActionError = "未选择插件"; return
    }
    val remote = remoteRepo ?: run {
        pluginActionError = "请先连接 Host"; return
    }
    if (pluginBusyId.isNotEmpty()) return
    val connection = activeConnectionId
    pluginBusyId = entry.id
    pluginActionError = ""
    pluginNotice = ""
    remote.pluginAction(entry.id, action, {
        if (pageAlive && connection == activeConnectionId) {
            pluginBusyId = ""
            pluginNotice = "${pluginActionLabel(action)}指令已下发，正在刷新状态"
            refreshPluginInventory()
        }
    }, { error ->
        if (pageAlive && connection == activeConnectionId) {
            pluginBusyId = ""
            pluginActionError = error
        }
    })
    setTimeout(35_000) {
        if (pageAlive && pluginBusyId == entry.id && connection == activeConnectionId) {
            pluginBusyId = ""
            pluginActionError = "操作超时，请刷新确认结果"
        }
    }
}

/** 搜索框回调：同步非响应式原生文本，仅用可观察的过滤词触发列表刷新。 */

internal fun DshHomePage.onPluginKeyword(value: String) {
    pluginSearchInput = value
    pluginSearchHasText = value.isNotEmpty()
    pluginKeyword = value
    applyPluginFilters()
}

/** 清除搜索框：清空原生文本与过滤词，并刷新列表。 */

internal fun DshHomePage.clearPluginKeyword() {
    pluginSearchInput = ""
    pluginSearchHasText = false
    pluginKeyword = ""
    pluginSearchInputView?.setText("")
    applyPluginFilters()
}

internal fun DshHomePage.applyPluginFilters() {
    pluginTotal = pluginInventory.size
    pluginRows.diffUpdate(filterDshPlugins(pluginInventory, pluginKeyword, pluginPhase)) { old, new -> old == new }
}

internal fun DshHomePage.refreshPluginInventory() {
    val remote = remoteRepo ?: run {
        pluginInventoryLoading = false; pluginInventoryError = "请先连接 Host"; return
    }
    val version = ++pluginRequestVersion
    val connection = activeConnectionId
    pluginInventoryLoading = true
    pluginInventoryError = ""
    fun current() = pageAlive && pluginInventoryVisible && version == pluginRequestVersion &&
        remote === repository && connection == activeConnectionId
    remote.loadPluginInventory({ entries ->
        if (current()) {
            pluginInventoryLoading = false
            pluginInventory = entries
            val ids = entries.map { it.id }.toSet()
            if (pluginExpandedId.isNotEmpty() && pluginExpandedId !in ids) pluginExpandedId = ""
            pluginActionTarget = pluginActionTarget?.let { old -> entries.firstOrNull { it.id == old.id } }
            applyPluginFilters()
        }
    }, { error ->
        if (current()) { pluginInventoryLoading = false; pluginInventoryError = error }
    })
    setTimeout(35_000) {
        if (current() && pluginInventoryLoading) {
            pluginRequestVersion++; pluginInventoryLoading = false; pluginInventoryError = "读取插件超时，请刷新重试"
        }
    }
}
