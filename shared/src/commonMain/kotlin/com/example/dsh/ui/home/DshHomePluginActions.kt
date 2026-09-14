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
    ui.settingsPageVisible = false
    ui.pluginInventoryVisible = true
    ui.pluginActiveTab = "config"
    pluginSearchInput = ""
    ui.pluginSearchHasText = false
    ui.pluginKeyword = ""
    refreshPluginInventory()
    loadPluginConfig()
}

/** 返回键：先关确认弹窗，再收起展开卡片，最后退出插件页。 */

internal fun DshHomePage.handlePluginInventoryBack() {
    when {
        ui.pluginConfirmAction.isNotEmpty() -> cancelPluginAction()
        ui.pluginExpandedId.isNotEmpty() -> ui.pluginExpandedId = ""
        else -> closePluginInventory()
    }
}

internal fun DshHomePage.closePluginInventory() {
    pluginRequestVersion++
    ui.pluginInventoryVisible = false
    ui.pluginInventoryLoading = false
    ui.pluginExpandedId = ""
    ui.pluginActionTarget = null
    ui.pluginConfirmAction = ""
    ui.pluginBusyId = ""
    ui.pluginActionError = ""
    ui.pluginNotice = ""
    ui.pluginConfigLoading = false
    ui.pluginConfigError = ""
    ui.pluginConfigCards.clear()
    ui.pluginConfigDrafts = emptyMap()
    ui.pluginConfigSecretDrafts = emptyMap()
    ui.pluginConfigCollapsed = emptySet()
    ui.pluginConfigBusyNamespace = ""
    ui.pluginConfigCardError = emptyMap()
    ui.pluginConfigCardNotice = emptyMap()
    ui.settingsPageVisible = true
}

internal fun DshHomePage.selectPluginTab(tab: String) {
    ui.pluginActiveTab = tab
    if (tab == "config" && ui.pluginConfigCards.isEmpty() && !ui.pluginConfigLoading) loadPluginConfig()
}

internal fun DshHomePage.loadPluginConfig() {
    val remote = remoteRepo ?: run {
        ui.pluginConfigLoading = false; ui.pluginConfigError = "请先连接 Host"; return
    }
    val connection = activeConnectionId
    ui.pluginConfigLoading = true
    ui.pluginConfigError = ""
    remote.loadPluginConfig({ state ->
        if (pageAlive && ui.pluginInventoryVisible && connection == activeConnectionId && remote === repository) {
            ui.pluginConfigLoading = false
            ui.pluginConfigWritable = state.writable
            // 首次进入时配置卡默认收起（对齐原版）；后续刷新保留用户展开状态。
            val firstLoad = ui.pluginConfigCards.isEmpty()
            ui.pluginConfigCards.clear()
            ui.pluginConfigCards.addAll(state.cards)
            if (firstLoad) ui.pluginConfigCollapsed = state.cards.map { it.namespace }.toSet()
            ui.pluginConfigDrafts = emptyMap()
            ui.pluginConfigSecretDrafts = emptyMap()
            ui.pluginConfigCardError = emptyMap()
        }
    }, { error ->
        if (pageAlive && ui.pluginInventoryVisible && connection == activeConnectionId && remote === repository) {
            ui.pluginConfigLoading = false
            ui.pluginConfigError = error
        }
    })
}

internal fun DshHomePage.pluginConfigDraft(namespace: String, key: String): String {
    val card = ui.pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return ""
    val fallback = card.fields.firstOrNull { it.key == key }?.value ?: ""
    return ui.pluginConfigDrafts["$namespace::$key"] ?: fallback
}

internal fun DshHomePage.pluginConfigSecretDraft(namespace: String): String = ui.pluginConfigSecretDrafts[namespace] ?: ""

internal fun DshHomePage.isPluginConfigCollapsed(namespace: String): Boolean = namespace in ui.pluginConfigCollapsed

internal fun DshHomePage.hasPluginConfigChanges(namespace: String): Boolean {
    val card = ui.pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return false
    if ((ui.pluginConfigSecretDrafts[namespace] ?: "").isNotEmpty()) return true
    return card.fields.any { it.kind != DshPluginFieldKind.SECRET && pluginConfigDraft(namespace, it.key) != it.value }
}

internal fun DshHomePage.onPluginConfigDraft(namespace: String, key: String, value: String) {
    ui.pluginConfigDrafts = ui.pluginConfigDrafts + ("$namespace::$key" to value)
}

internal fun DshHomePage.onPluginConfigSecretDraft(namespace: String, value: String) {
    ui.pluginConfigSecretDrafts = ui.pluginConfigSecretDrafts + (namespace to value)
}

internal fun DshHomePage.togglePluginConfigCollapsed(namespace: String) {
    ui.pluginConfigCollapsed = if (namespace in ui.pluginConfigCollapsed) {
        ui.pluginConfigCollapsed - namespace
    } else {
        ui.pluginConfigCollapsed + namespace
    }
}

internal fun DshHomePage.discardPluginConfigCard(namespace: String) {
    clearPluginConfigDrafts(namespace)
    ui.pluginConfigCardNotice = ui.pluginConfigCardNotice - namespace
}

internal fun DshHomePage.savePluginConfigCard(namespace: String) {
    val card = ui.pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return
    if (ui.pluginConfigBusyNamespace.isNotEmpty()) return
    val remote = remoteRepo ?: run {
        ui.pluginConfigCardError = ui.pluginConfigCardError + (namespace to "请先连接 Host"); return
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
        ui.pluginConfigCardError = ui.pluginConfigCardError + (namespace to invalid)
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
    ui.pluginConfigBusyNamespace = namespace
    ui.pluginConfigCardError = ui.pluginConfigCardError - namespace
    ui.pluginConfigCardNotice = ui.pluginConfigCardNotice - namespace
    remote.savePluginConfig(save, {
        if (pageAlive && ui.pluginInventoryVisible) {
            ui.pluginConfigBusyNamespace = ""
            clearPluginConfigDrafts(namespace)
            ui.pluginConfigCardNotice = ui.pluginConfigCardNotice + (namespace to "已保存")
            loadPluginConfig()
        }
    }, { error ->
        if (pageAlive && ui.pluginInventoryVisible) {
            ui.pluginConfigBusyNamespace = ""
            ui.pluginConfigCardError = ui.pluginConfigCardError + (namespace to error)
        }
    })
}

internal fun DshHomePage.clearPluginConfigDrafts(namespace: String) {
    val prefix = "$namespace::"
    ui.pluginConfigDrafts = ui.pluginConfigDrafts.filterKeys { !it.startsWith(prefix) }
    ui.pluginConfigSecretDrafts = ui.pluginConfigSecretDrafts - namespace
    ui.pluginConfigCardError = ui.pluginConfigCardError - namespace
}

internal fun DshHomePage.togglePluginExpanded(entry: DshPluginEntry) {
    ui.pluginExpandedId = if (ui.pluginExpandedId == entry.id) "" else entry.id
}

/** 启用直接执行；停用先弹二次确认。 */

internal fun DshHomePage.requestPluginToggle(entry: DshPluginEntry, enable: Boolean) {
    if (ui.pluginBusyId.isNotEmpty()) return
    ui.pluginActionTarget = entry
    ui.pluginActionError = ""
    ui.pluginNotice = ""
    if (enable) performPluginAction("enable") else ui.pluginConfirmAction = "disable"
}

/** 重载先弹二次确认。 */

internal fun DshHomePage.requestPluginReload(entry: DshPluginEntry) {
    if (ui.pluginBusyId.isNotEmpty()) return
    ui.pluginActionTarget = entry
    ui.pluginActionError = ""
    ui.pluginNotice = ""
    ui.pluginConfirmAction = "reload"
}

internal fun DshHomePage.cancelPluginAction() {
    ui.pluginConfirmAction = ""
    ui.pluginActionError = ""
}

internal fun DshHomePage.confirmPluginAction() {
    val action = ui.pluginConfirmAction
    ui.pluginConfirmAction = ""
    if (action.isNotEmpty()) performPluginAction(action)
}

internal fun DshHomePage.performPluginAction(action: String) {
    val entry = ui.pluginActionTarget ?: run {
        ui.pluginActionError = "未选择插件"; return
    }
    val remote = remoteRepo ?: run {
        ui.pluginActionError = "请先连接 Host"; return
    }
    if (ui.pluginBusyId.isNotEmpty()) return
    val connection = activeConnectionId
    ui.pluginBusyId = entry.id
    ui.pluginActionError = ""
    ui.pluginNotice = ""
    remote.pluginAction(entry.id, action, {
        if (pageAlive && connection == activeConnectionId) {
            ui.pluginBusyId = ""
            ui.pluginNotice = "${pluginActionLabel(action)}指令已下发，正在刷新状态"
            refreshPluginInventory()
        }
    }, { error ->
        if (pageAlive && connection == activeConnectionId) {
            ui.pluginBusyId = ""
            ui.pluginActionError = error
        }
    })
    setTimeout(35_000) {
        if (pageAlive && ui.pluginBusyId == entry.id && connection == activeConnectionId) {
            ui.pluginBusyId = ""
            ui.pluginActionError = "操作超时，请刷新确认结果"
        }
    }
}

/** 搜索框回调：同步非响应式原生文本，仅用可观察的过滤词触发列表刷新。 */

internal fun DshHomePage.onPluginKeyword(value: String) {
    pluginSearchInput = value
    ui.pluginSearchHasText = value.isNotEmpty()
    ui.pluginKeyword = value
    applyPluginFilters()
}

/** 清除搜索框：清空原生文本与过滤词，并刷新列表。 */

internal fun DshHomePage.clearPluginKeyword() {
    pluginSearchInput = ""
    ui.pluginSearchHasText = false
    ui.pluginKeyword = ""
    pluginSearchInputView?.setText("")
    applyPluginFilters()
}

internal fun DshHomePage.applyPluginFilters() {
    ui.pluginTotal = pluginInventory.size
    ui.pluginRows.diffUpdate(filterDshPlugins(pluginInventory, ui.pluginKeyword, ui.pluginPhase)) { old, new -> old == new }
}

internal fun DshHomePage.refreshPluginInventory() {
    val remote = remoteRepo ?: run {
        ui.pluginInventoryLoading = false; ui.pluginInventoryError = "请先连接 Host"; return
    }
    val version = ++pluginRequestVersion
    val connection = activeConnectionId
    ui.pluginInventoryLoading = true
    ui.pluginInventoryError = ""
    fun current() = pageAlive && ui.pluginInventoryVisible && version == pluginRequestVersion &&
        remote === repository && connection == activeConnectionId
    remote.loadPluginInventory({ entries ->
        if (current()) {
            ui.pluginInventoryLoading = false
            pluginInventory = entries
            val ids = entries.map { it.id }.toSet()
            if (ui.pluginExpandedId.isNotEmpty() && ui.pluginExpandedId !in ids) ui.pluginExpandedId = ""
            ui.pluginActionTarget = ui.pluginActionTarget?.let { old -> entries.firstOrNull { it.id == old.id } }
            applyPluginFilters()
        }
    }, { error ->
        if (current()) { ui.pluginInventoryLoading = false; ui.pluginInventoryError = error }
    })
    setTimeout(35_000) {
        if (current() && ui.pluginInventoryLoading) {
            pluginRequestVersion++; ui.pluginInventoryLoading = false; ui.pluginInventoryError = "读取插件超时，请刷新重试"
        }
    }
}
