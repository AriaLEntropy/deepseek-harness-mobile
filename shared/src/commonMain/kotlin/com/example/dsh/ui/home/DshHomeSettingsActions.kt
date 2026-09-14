package com.example.dsh.ui.home

import com.example.dsh.host.DshConnectionMode
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.host.DshRepository
import com.example.dsh.models.DshSettingsChoice
import com.example.dsh.ui.rendering.DSH_PREF_EXPAND_MODAL
import com.example.dsh.ui.rendering.DSH_PREF_PROCESS_DISPLAY
import com.example.dsh.ui.rendering.DSH_PREF_SHOW_CONNECTORS
import com.example.dsh.ui.rendering.DSH_PREF_SHOW_RESULT_CARDS
import com.example.dsh.ui.rendering.DshExpandedPayload
import com.example.dsh.ui.rendering.DshProcessDisplayMode
import com.example.dsh.ui.rendering.dshProcessDisplayValue
import com.example.dsh.theme.DshThemeManager
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.launch
import com.example.dsh.ui.interaction.DshPermissionOption

internal fun DshHomePage.openCredentialSettings() {
    dismissKeyboard()
    ui.commandSheetVisible = false
    //closeSessionDrawer()
    ui.credentialSetupTitle = if (sshMode) "修改电脑端 DSH 的 API Key" else "设置 DeepSeek API Key"
    ui.credentialSetupError = ""
    ui.apiKeyDraft = pendingApiKey
    updateCredentialSetupVisibility(true)
}

internal fun DshHomePage.openSettingsPage() {
    dismissKeyboard()
    ui.commandSheetVisible = false
    closeSessionDrawerImmediately()
    reloadSettings()
    loadHostVersion()
    if (isRemoteHost) reloadModelsSettings(showLoading = false)
    ui.settingsPageVisible = true
}

internal fun DshHomePage.closeSettingsPage() {
    ui.settingsPageVisible = false
    ui.settingsChoiceKind = ""
    ui.settingsChoiceBusy = false
}

// ===== 个性化「对话展示」 =====

internal fun DshHomePage.openPersonalizationPage() {
    dismissKeyboard()
    ui.personalizationPageVisible = true
}

internal fun DshHomePage.closePersonalizationPage() {
    ui.personalizationPageVisible = false
}

internal fun DshHomePage.applyChatProcessMode(mode: DshProcessDisplayMode) {
    ui.chatProcessMode = mode
    runCatching {
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .setString(DSH_PREF_PROCESS_DISPLAY, dshProcessDisplayValue(mode))
    }
    remountConversationList(ui.activeSessionId)
}

internal fun DshHomePage.applyChatExpandInModal(enabled: Boolean) {
    ui.chatExpandInModal = enabled
    runCatching {
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .setString(DSH_PREF_EXPAND_MODAL, if (enabled) "1" else "0")
    }
    remountConversationList(ui.activeSessionId)
}

internal fun DshHomePage.applyChatShowConnectors(enabled: Boolean) {
    ui.chatShowConnectors = enabled
    runCatching {
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .setString(DSH_PREF_SHOW_CONNECTORS, if (enabled) "1" else "0")
    }
    remountConversationList(ui.activeSessionId)
}

internal fun DshHomePage.applyChatShowResultCards(enabled: Boolean) {
    ui.chatShowResultCards = enabled
    runCatching {
        acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            .setString(DSH_PREF_SHOW_RESULT_CARDS, if (enabled) "1" else "0")
    }
    remountConversationList(ui.activeSessionId)
}

internal fun DshHomePage.openExpandedModal(payload: DshExpandedPayload) {
    ui.expandedPayload = payload
}

internal fun DshHomePage.closeExpandedModal() {
    ui.expandedPayload = null
}

/** 设置页「模型」摘要：已配置的提供方数量。 */

internal fun DshHomePage.reloadSettings(showLoading: Boolean = true) {
    // 保存后的回读只更新数据，避免加载提示插入列表导致滚动位置跳动。
    if (showLoading) {
        ui.settingsLoading = true
        ui.settingsError = ""
    }
    val repo = repository
    if (repo == null) {
        ui.settingsLoading = false
        if (showLoading) ui.settingsError = "未连接电脑端"
        return
    }
    repo.describeSettings({
        ui.settingsSnapshot = it
        ui.settingsError = ""
        ui.settingsLoading = false
    }, {
        ui.settingsLoading = false
        if (showLoading) ui.settingsError = it
        else bridgeModule.toast("设置刷新失败：$it")
    })
}

internal fun DshHomePage.loadHostVersion() {
    val repo = repository ?: return
    repo.loadHostVersion({ ui.hostVersion = it }, { })
}

internal fun DshHomePage.openAgentModePicker(title: String = "选择模式") {
    ui.agentModePickerTitle = title
    if (ui.agentPresetOptions.isEmpty()) {
        val repo = repository
        repo?.loadAgentPresets({
            ui.agentPresetOptions.clear()
            ui.agentPresetOptions.addAll(it)
            ui.agentModePickerVisible = true
        }, {
            ui.agentModePickerVisible = true
        })
    } else {
        ui.agentModePickerVisible = true
    }
}

internal fun DshHomePage.openSettingsChoice(kind: String, title: String) {
    if (ui.settingsChoiceBusy) return
    ui.settingsChoiceTitle = title
    ui.settingsChoiceOptions.clear()
    when (kind) {
        "permission" -> ui.settingsChoiceOptions.addAll(ui.settingsSnapshot.permissionChoices)
        "locale" -> {
            ui.settingsChoiceOptions.add(DshSettingsChoice("zh", "简体中文"))
            ui.settingsChoiceOptions.add(DshSettingsChoice("en", "English"))
        }
        "theme" -> {
            ui.settingsChoiceOptions.add(DshSettingsChoice("system", "跟随系统"))
            ui.settingsChoiceOptions.add(DshSettingsChoice("sunrise-sunset", "日出日落"))
            ui.settingsChoiceOptions.add(DshSettingsChoice("dark", "深色"))
            ui.settingsChoiceOptions.add(DshSettingsChoice("light", "浅色"))
        }
    }
    ui.settingsChoiceKind = kind
}

internal fun DshHomePage.applySettingsChoice(choice: DshSettingsChoice) {
    val kind = ui.settingsChoiceKind
    if (kind.isEmpty() || ui.settingsChoiceBusy) return
    ui.settingsChoiceKind = ""
    if (kind == "theme") {
        // 本地外观不依赖电脑连接；同步失败也保留移动端选择。
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DshThemeManager.PREF_KEY_THEME_MODE, choice.value)
        }
        DshThemeManager.applyPreference(choice.value)
    }
    ui.settingsChoiceBusy = true
    val repo = repository
    if (repo == null) {
        ui.settingsChoiceBusy = false
        if (kind != "theme") bridgeModule.toast("未连接电脑端")
        return
    }
    when (kind) {
        "permission" -> repo.updateSetting(
            "permission",
            JSONObject().apply { put("defaultPreset", choice.value) },
            ui.settingsSnapshot.permissionRevision,
            {
                ui.settingsChoiceBusy = false
                reloadSettings(showLoading = false)
            },
            {
                ui.settingsChoiceBusy = false
                bridgeModule.toast("权限设置失败：$it")
            },
        )
        "locale" -> repo.updateSetting(
            "locale",
            JSONObject().apply { put("preference", choice.value) },
            ui.settingsSnapshot.localeRevision,
            {
                ui.settingsChoiceBusy = false
                reloadSettings(showLoading = false)
            },
            {
                ui.settingsChoiceBusy = false
                bridgeModule.toast("语言设置失败：$it")
            },
        )
        "theme" -> {
            ui.settingsChoiceBusy = false
            // 日出日落为移动端本地模式，电脑端不支持该偏好，不做同步。
            if (choice.value == "sunrise-sunset") return
            // 顺带同步电脑端外观（失败仅提示，不影响移动端）
            repo.updateSetting(
                "ui-theme",
                JSONObject().apply { put("preference", choice.value) },
                ui.settingsSnapshot.themeRevision,
                { reloadSettings(showLoading = false) },
                { bridgeModule.toast("外观同步电脑端失败：$it") },
            )
        }
        else -> ui.settingsChoiceBusy = false
    }
}

// 首页权限弹窗的选择：与设置页「工作区权限」走同一个全局设置通道（settings.update），
// 选项已优先取 host 动态枚举；未连接或 host 不支持设置时仅本地记住并提示。

internal fun DshHomePage.openPermissionPicker() {
    dismissKeyboard()
    ui.commandSheetVisible = false
    ui.permissionPickerVisible = true
    // 快照未加载（通常还没打开过设置页）时预热拉取，弹窗选项展示 host 真实预设。
    if (repository != null && ui.settingsSnapshot.permissionChoices.isEmpty()) {
        reloadSettings(showLoading = false)
    }
}

internal fun DshHomePage.applyPermissionPreset(option: DshPermissionOption) {
    val repo = repository ?: run {
        bridgeModule.toast("未连接电脑端，权限已本地记住，连接后可在设置页同步")
        return
    }
    if (ui.settingsChoiceBusy) return
    if (ui.settingsSnapshot.permissionChoices.isEmpty()) {
        // 设置快照尚未加载：先拉取一次 host settings（拿到 revision），成功后再写入。
        ui.settingsChoiceBusy = true
        repo.describeSettings({
            ui.settingsChoiceBusy = false
            ui.settingsSnapshot = it
            pushPermissionPreset(repo, option, it.permissionRevision)
        }, {
            ui.settingsChoiceBusy = false
            bridgeModule.toast("获取权限设置失败：$it")
        })
        return
    }
    pushPermissionPreset(repo, option, ui.settingsSnapshot.permissionRevision)
}

internal fun DshHomePage.pushPermissionPreset(repo: DshRepository, option: DshPermissionOption, revision: Int) {
    ui.settingsChoiceBusy = true
    repo.updateSetting(
        "permission",
        JSONObject().apply { put("defaultPreset", option.value) },
        revision,
        {
            ui.settingsChoiceBusy = false
            reloadSettings(showLoading = false)
        },
        {
            ui.settingsChoiceBusy = false
            bridgeModule.toast("权限设置失败：$it")
        },
    )
}

internal fun DshHomePage.closeCredentialSettings() {
    dismissKeyboard()
    updateCredentialSetupVisibility(false)
}

internal fun DshHomePage.updateCredentialSetupVisibility(visible: Boolean) {
    ui.credentialSetupVisible = visible
    if (pageData.isAndroid || pageData.isIOS) {
        bridgeModule.setSystemBarsDimmed(visible)
    }
}

internal fun DshHomePage.loadApiKeyAsync() {
    if (isRemoteHost) return
    val store = localStore
    if (store == null) {
        showCredentialSetupIfNeeded("")
        return
    }
    localReadScope.launch {
        val apiKey = runCatching { store.loadApiKey() }.getOrDefault("")
        postToUi {
            pendingApiKey = apiKey
            if (apiKey.isEmpty()) {
                showCredentialSetupIfNeeded(apiKey)
            } else if (engineReady && repository == null && ui.connectionMode == DshConnectionMode.LOCAL) {
                connectLocalEngine(apiKey)
            }
        }
    }
}

internal fun DshHomePage.showCredentialSetupIfNeeded(apiKey: String) {
    if (isRemoteHost) return
    if (pendingApiKey.isNotEmpty() || apiKey.isNotEmpty()) return
    connectionLabel = "等待配置"
    updateCredentialSetupVisibility(true)
    if (ui.messages.none { it.id == "api-key-required" }) {
        ui.messages.add(
            DshMessage(
                id = "api-key-required",
                role = DshMessageRole.ASSISTANT,
                content = "输入 DeepSeek API Key 后即可开始使用本地 Agent。",
            ),
        )
    }
}
