package com.example.dsh.ui.home

import com.example.dsh.models.DshProviderModel
import com.example.dsh.ui.rendering.DshExpandedContentModal
import com.example.dsh.theme.DshThemeManager
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.ui.models.DshModelsPage
import com.example.dsh.ui.session.DshWorkspacePickerModal
import com.example.dsh.ui.session.DshWorkspacePickerScreen
import com.example.dsh.ui.connection.DshConnectionSettingsModal
import com.example.dsh.ui.connection.DshCredentialSetupModal
import com.example.dsh.ui.settings.DshPersonalizationPage
import com.example.dsh.ui.plugin.DshPluginSettingsView
import com.example.dsh.ui.settings.DshSettingsChoicePicker
import com.example.dsh.ui.settings.DshSettingsPage

internal fun DshHomePage.bodySettingsOverlays(): ViewBuilder {
    val ctx = this
    return {
        // ===== 设置页（ds 风格：顶部居中标题，账户/权限/应用/关于分组） =====
        vif({ ctx.ui.settingsPageVisible }) {
            DshSettingsPage(
                loading = { ctx.ui.settingsLoading },
                error = { ctx.ui.settingsError },
                snapshot = { ctx.ui.settingsSnapshot },
                isRemoteHost = { ctx.isRemoteHost },
                connectionModeLabel = { ctx.connectionModeLabel() },
                modelsSummary = { ctx.modelsSummary() },
                hostVersion = { ctx.ui.hostVersion },
                themeMode = { ctx.themeMode },
                processDisplayMode = { ctx.ui.chatProcessMode },
                agentPresetLabel = { ctx.ui.agentModeLabel },
                onClose = { ctx.closeSettingsPage() },
                onRetry = { ctx.reloadSettings() },
                onOpenConnection = { ctx.openConnectionSettings() },
                onOpenModels = { ctx.openModelsPage() },
                onOpenPersonalization = { ctx.openPersonalizationPage() },
                onPickPermission = { ctx.openSettingsChoice("permission", "工作区权限") },
                onPickLocale = { ctx.openSettingsChoice("locale", "语言") },
                onPickTheme = { ctx.openSettingsChoice("theme", "外观") },
                onOpenAgentPresets = { ctx.openAgentModePicker("Agent 预设") },
                onOpenDiagnosticLogs = { ctx.openDiagnosticLogs() },
                onOpenPlugins = { ctx.openPluginInventory() },
                onDisconnect = { ctx.disconnectFromHost() },
                colors = { ctx.themeColors },
            )
        }

        // ===== 设置页「个性化」子页面：过程展示方式（互斥）+ 弹窗查看开关 =====
        vif({ ctx.ui.personalizationPageVisible }) {
            DshPersonalizationPage(
                mode = { ctx.ui.chatProcessMode },
                expandInModal = { ctx.ui.chatExpandInModal },
                showConnectors = { ctx.ui.chatShowConnectors },
                showResultCards = { ctx.ui.chatShowResultCards },
                onPickMode = { ctx.applyChatProcessMode(it) },
                onToggleExpandInModal = { ctx.applyChatExpandInModal(it) },
                onToggleConnectors = { ctx.applyChatShowConnectors(it) },
                onToggleResultCards = { ctx.applyChatShowResultCards(it) },
                onClose = { ctx.closePersonalizationPage() },
                colors = { ctx.themeColors },
            )
        }

        // ===== 展开内容底部大弹层（「弹窗查看」开启时） =====
        vif({ ctx.ui.expandedPayload != null }) {
            DshExpandedContentModal(
                payload = { ctx.ui.expandedPayload },
                onClose = { ctx.closeExpandedModal() },
                colors = { ctx.themeColors },
            )
        }

        vif({ ctx.ui.pluginInventoryVisible }) {
            DshPluginSettingsView(
                activeTab = { ctx.ui.pluginActiveTab }, onSelectTab = { ctx.selectPluginTab(it) },
                loading = { ctx.ui.pluginInventoryLoading }, error = { ctx.ui.pluginInventoryError },
                keyword = { ctx.pluginSearchInput }, onKeyword = { ctx.onPluginKeyword(it) },
                hasKeyword = { ctx.ui.pluginSearchHasText }, onClearKeyword = { ctx.clearPluginKeyword() },
                onSearchInputRef = { ctx.pluginSearchInputView = it.view },
                onRefresh = { ctx.refreshPluginInventory() }, onClose = { ctx.closePluginInventory() },
                rows = { ctx.ui.pluginRows }, total = { ctx.ui.pluginTotal },
                expandedId = { ctx.ui.pluginExpandedId }, busyId = { ctx.ui.pluginBusyId },
                onToggleExpand = { ctx.togglePluginExpanded(it) },
                actionError = { ctx.ui.pluginActionError }, actionNotice = { ctx.ui.pluginNotice },
                onToggleEnabled = { entry, enable -> ctx.requestPluginToggle(entry, enable) },
                onReload = { ctx.requestPluginReload(it) },
                confirmEntry = { ctx.ui.pluginActionTarget }, confirmAction = { ctx.ui.pluginConfirmAction },
                onConfirm = { ctx.confirmPluginAction() }, onCancelConfirm = { ctx.cancelPluginAction() },
                configCards = { ctx.ui.pluginConfigCards }, configLoading = { ctx.ui.pluginConfigLoading },
                configError = { ctx.ui.pluginConfigError }, configWritable = { ctx.ui.pluginConfigWritable },
                configDraft = { ns, key -> ctx.pluginConfigDraft(ns, key) },
                configSecretDraft = { ns -> ctx.pluginConfigSecretDraft(ns) },
                configCollapsed = { ns -> ctx.isPluginConfigCollapsed(ns) },
                configBusyNamespace = { ctx.ui.pluginConfigBusyNamespace },
                configCardError = { ns -> ctx.ui.pluginConfigCardError[ns] ?: "" },
                configCardNotice = { ns -> ctx.ui.pluginConfigCardNotice[ns] ?: "" },
                configHasChanges = { ns -> ctx.hasPluginConfigChanges(ns) },
                onConfigDraft = { ns, key, value -> ctx.onPluginConfigDraft(ns, key, value) },
                onConfigSecretDraft = { ns, value -> ctx.onPluginConfigSecretDraft(ns, value) },
                onConfigToggleCollapse = { ctx.togglePluginConfigCollapsed(it) },
                onConfigSave = { ctx.savePluginConfigCard(it) },
                onConfigDiscard = { ctx.discardPluginConfigCard(it) },
                colors = { ctx.themeColors },
            )
        }

        // ===== 设置页「模型」详情页（对齐电脑端 settings.models） =====
        vif({ ctx.ui.modelsPageVisible }) {
            DshModelsPage(
                loading = { ctx.ui.modelsLoading },
                error = { ctx.ui.modelsError },
                writable = { ctx.ui.modelsWritable },
                configuredProviders = { ctx.ui.modelsConfiguredProviders },
                addableProviders = { ctx.ui.modelsAddableProviders },
                editingProvider = { ctx.ui.modelsEditingProvider },
                pickerVisible = { ctx.ui.modelsPickerVisible },
                customAdding = { ctx.ui.modelsCustomAdding },
                savedNotice = { ctx.ui.modelsSavedNotice },
                editorAdvanced = { ctx.ui.modelsEditorAdvanced },
                onToggleEditorAdvanced = { ctx.ui.modelsEditorAdvanced = !ctx.ui.modelsEditorAdvanced },
                draftBaseUrl = { ctx.ui.modelsDraftBaseUrl },
                draftApiKey = { ctx.ui.modelsDraftApiKey },
                draftModels = { ctx.ui.modelsDraftModels },
                saving = { ctx.ui.modelsSaving },
                saveError = { ctx.ui.modelsSaveError },
                customProtocols = { ctx.ui.modelsProtocols },
                customRoute = { ctx.ui.modelsCustomRoute },
                customName = { ctx.ui.modelsCustomName },
                customBaseUrl = { ctx.ui.modelsCustomBaseUrl },
                customProtocol = { ctx.ui.modelsCustomProtocol },
                customApiKey = { ctx.ui.modelsCustomApiKey },
                customModels = { ctx.ui.modelsCustomModels },
                customBusy = { ctx.ui.modelsCustomBusy },
                customError = { ctx.ui.modelsCustomError },
                deleteTarget = { ctx.ui.modelsDeleteTarget },
                deleting = { ctx.ui.modelsDeleting },
                onClose = { ctx.closeModelsPage() },
                onRetry = { ctx.reloadModelsSettings() },
                onEdit = { ctx.openProviderEditor(it) },
                onCancelAdd = { ctx.cancelAddProvider() },
                onOpenPicker = { ctx.ui.modelsPickerVisible = true },
                onClosePicker = { ctx.ui.modelsPickerVisible = false },
                onSelectAddable = { ctx.selectAddableProvider(it) },
                onOpenCustom = { ctx.openCustomProvider() },
                onCancelCustom = { ctx.cancelCustomProvider() },
                onBaseUrlChange = { ctx.ui.modelsDraftBaseUrl = it; ctx.ui.modelsSaveError = "" },
                onApiKeyChange = { ctx.ui.modelsDraftApiKey = it; ctx.ui.modelsSaveError = "" },
                onModelChange = { index, field, value ->
                    ctx.updateDraftModel(index, field, value)
                    ctx.ui.modelsSaveError = ""
                },
                onAddModel = { ctx.addDraftModel() },
                onRemoveModel = { ctx.removeDraftModel(it) },
                onApply = { ctx.applyProviderEditor(it) },
                onCustomRoute = { ctx.ui.modelsCustomRoute = it; ctx.ui.modelsCustomError = "" },
                onCustomName = { ctx.ui.modelsCustomName = it },
                onCustomBaseUrl = { ctx.ui.modelsCustomBaseUrl = it; ctx.ui.modelsCustomError = "" },
                onCustomProtocol = { ctx.ui.modelsCustomProtocol = it },
                onCustomApiKey = { ctx.ui.modelsCustomApiKey = it },
                onCustomModelChange = { index, field, value -> ctx.updateCustomModel(index, field, value) },
                onCustomAddModel = { ctx.ui.modelsCustomModels.add(DshProviderModel()) },
                onCustomRemoveModel = { ctx.removeCustomModel(it) },
                onApplyCustom = { ctx.applyCustomProvider() },
                onRequestDelete = { ctx.requestRemoveProvider(it) },
                onConfirmDelete = { ctx.confirmRemoveProvider() },
                onCancelDelete = { ctx.ui.modelsDeleteTarget = null },
                colors = { ctx.themeColors },
            )
        }

    }
}

internal fun DshHomePage.bodySettingsChoiceAndCredentials(): ViewBuilder {
    val ctx = this
    return {
        // ===== 设置项选择器（权限预设 / 语言 / 外观） =====
        vif({ ctx.ui.settingsChoiceKind.isNotEmpty() }) {
            DshSettingsChoicePicker(
                title = ctx.ui.settingsChoiceTitle,
                options = { ctx.ui.settingsChoiceOptions },
                selectedValue = {
                    when (ctx.ui.settingsChoiceKind) {
                        "permission" -> ctx.ui.settingsSnapshot.permissionPreset
                        "locale" -> ctx.ui.settingsSnapshot.localeValue
                        "theme" -> DshThemeManager.preferenceValue
                        else -> ""
                    }
                },
                busy = { ctx.ui.settingsChoiceBusy },
                onClose = { if (!ctx.ui.settingsChoiceBusy) ctx.ui.settingsChoiceKind = "" },
                onSelect = { ctx.applySettingsChoice(it) },
                colors = { ctx.themeColors },
            )
        }

        // ===== API Key 设置弹窗 =====
        // 输入并保存 DeepSeek API Key（也可用于修改远程 DSH 的 Key）。
        vif({ ctx.ui.credentialSetupVisible }) {
            DshCredentialSetupModal(
                title = { ctx.ui.credentialSetupTitle },
                busy = { ctx.ui.credentialSetupBusy },
                error = { ctx.ui.credentialSetupError },
                inputRef = {
                    ctx.apiKeyInputView = it.view
                    ctx.apiKeyInputView?.setText(ctx.ui.apiKeyDraft)
                },
                onApiKeyChange = {
                    ctx.ui.apiKeyDraft = it
                    ctx.ui.credentialSetupError = ""
                },
                onSave = { ctx.saveDeepSeekApiKey() },
                onClose = { ctx.closeCredentialSettings() },
                colors = { ctx.themeColors },
            )
        }
        // ===== 连接设置弹窗 =====
        // 配置连接方式（扫码 RELAY / SSH）：主机、端口、用户名、私钥导入、指纹确认、DSH 端口。
        vif({ ctx.ui.sshSettingsVisible }) {
            DshConnectionSettingsModal(
                sshMode = { ctx.sshMode },
                host = { ctx.ui.sshHost },
                user = { ctx.ui.sshUser },
                port = { ctx.ui.sshPort },
                dshPort = { ctx.ui.sshDshPort },
                keyLabel = { ctx.ui.sshKeyLabel },
                keyPassphrase = { ctx.ui.sshKeyPassphrase },
                busy = { ctx.ui.sshSettingsBusy },
                error = { ctx.ui.sshSettingsError },
                onModeChange = { ctx.setConnectionMode(it) },
                onHostChange = { ctx.ui.sshHost = it; ctx.ui.sshSettingsError = "" },
                onUserChange = { ctx.ui.sshUser = it; ctx.ui.sshSettingsError = "" },
                onPortChange = { ctx.ui.sshPort = it; ctx.ui.sshSettingsError = "" },
                onDshPortChange = { ctx.ui.sshDshPort = it; ctx.ui.sshSettingsError = "" },
                onPickKey = { ctx.pickSshKey() },
                onPassphraseChange = { ctx.ui.sshKeyPassphrase = it },
                onTrustFingerprint = { ctx.trustSshFingerprint() },
                onSave = { ctx.saveConnectionSettings() },
                onClose = { ctx.updateSshSettingsVisibility(false) },
                onOpenApiKey = {
                    ctx.updateSshSettingsVisibility(false)
                    ctx.openCredentialSettings()
                },
                colors = { ctx.themeColors },
            )
        }
    }
}

internal fun DshHomePage.bodyWorkspaceDialogs(): ViewBuilder {
    val ctx = this
    return {
        // ===== 新建会话-工作区选择弹窗（最近的文件夹 / 添加文件夹） =====
        // 仅远程模式、且当前会话尚未发送第一条消息（blank）时可用。
        vif({ ctx.ui.workspacePickerVisible && ctx.isRemoteHost }) {
            DshWorkspacePickerModal(
                screen = { ctx.ui.workspacePickerScreen },
                folders = { ctx.ui.workspacePickerFolders },
                activeWorkspaceId = { ctx.activeWorkspaceId() },
                busy = { ctx.ui.workspacePickerBusy || ctx.ui.workspaceAddBusy },
                error = { ctx.ui.workspacePickerError },
                onSelectFolder = { ctx.switchWorkspaceTo(it) },
                onAddFolder = { ctx.openWorkspaceAddFolder() },
                onBack = { ctx.onWorkspacePickerBack() },
                onClose = { ctx.closeWorkspacePicker() },
                path = { ctx.ui.workspaceAddPath },
                home = { ctx.ui.workspaceAddHome },
                entries = { ctx.ui.workspaceAddEntries },
                newName = { ctx.ui.workspaceAddNewName },
                keyboardHeight = { ctx.ui.workspaceAddKeyboardHeight },
                onKeyboardHeightChange = {
                    if (ctx.ui.workspacePickerVisible && ctx.ui.workspacePickerScreen == DshWorkspacePickerScreen.ADD) {
                        ctx.ui.workspaceAddKeyboardHeight = it.coerceAtLeast(0f)
                    }
                },
                onNewNameInputRef = { ctx.workspaceAddInputView = it },
                directoryLoaded = { ctx.ui.workspaceAddDirectoryLoaded },
                onDirectorySelect = { ctx.loadWorkspaceAddDirectory(it) },
                onNewNameChange = { ctx.ui.workspaceAddNewName = it },
                onCreateDirectory = { ctx.createWorkspaceAddDirectory() },
                onAdopt = { ctx.adoptWorkspaceAddDirectory() },
                colors = { ctx.themeColors },
            )
        }
        // ===== 重命名工作区 弹窗 =====
        // 内嵌 Modal：输入新名称 → 保存/取消；错误信息红字显示。
        vif({ ctx.ui.workspaceRenameTargetId.isNotEmpty() && ctx.isRemoteHost }) {
            Modal(inWindow = true) {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    paddingLeft(20f)
                    paddingRight(20f)
                    backgroundColor(Color(0x66000000))
                }
                View {
                    attr {
                        width(pagerData.pageViewWidth - 40f)
                        maxWidth(420f)
                        padding(20f)
                        borderRadius(16f)
                        backgroundColor(ctx.themeColors.bgLayer3)
                    }
                    Text { attr { text("重命名工作区"); fontSize(18f); fontWeightBold(); color(ctx.themeColors.labelPrimary) } }
                    Input {
                        attr {
                            height(38f)
                            marginTop(14f)
                            fontSize(14f)
                            placeholder("工作区名称")
                            placeholderColor(ctx.themeColors.labelTertiary)
                            text(ctx.ui.workspaceRenameDraft)
                        }
                        event { textDidChange { ctx.ui.workspaceRenameDraft = it.text } }
                    }
                    vif({ ctx.ui.workspaceActionError.isNotEmpty() }) {
                        Text { attr { text(ctx.ui.workspaceActionError); marginTop(8f); fontSize(12f); color(ctx.themeColors.stateErrorPrimary) } }
                    }
                    View {
                        attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                        Text {
                            attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(ctx.themeColors.labelTertiary) }
                            event { click { ctx.ui.workspaceRenameTargetId = ""; ctx.ui.workspaceActionError = "" } }
                        }
                        Text {
                            attr { text(if (ctx.ui.workspaceActionBusy) "保存中..." else "保存"); width(78f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(ctx.themeColors.stateBusinessPrimary) }
                            event { click { if (!ctx.ui.workspaceActionBusy) ctx.saveWorkspaceRename() } }
                        }
                    }
                }
            }
        }
        // ===== 删除工作区注册 确认弹窗 =====
        // 仅从列表移除注册，不删除实际目录/会话/日志；红色「删除注册」按钮。
        vif({ ctx.ui.workspaceDeleteTargetId.isNotEmpty() && ctx.isRemoteHost }) {
            Modal(inWindow = true) {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    paddingLeft(20f)
                    paddingRight(20f)
                    backgroundColor(Color(0x66000000))
                }
                View {
                    attr {
                        width(pagerData.pageViewWidth - 40f)
                        maxWidth(420f)
                        padding(20f)
                        borderRadius(16f)
                        backgroundColor(ctx.themeColors.bgLayer3)
                    }
                    Text { attr { text("删除工作区注册?"); fontSize(18f); fontWeightBold(); color(ctx.themeColors.labelPrimary) } }
                    Text {
                        attr {
                            text("只会从列表移除注册，不会删除目录、会话或日志。")
                            marginTop(8f)
                            fontSize(13f)
                            lineHeight(20f)
                            color(ctx.themeColors.labelSecondary)
                        }
                    }
                    vif({ ctx.ui.workspaceActionError.isNotEmpty() }) {
                        Text { attr { text(ctx.ui.workspaceActionError); marginTop(8f); fontSize(12f); color(ctx.themeColors.stateErrorPrimary) } }
                    }
                    View {
                        attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                        Text {
                            attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(ctx.themeColors.labelTertiary) }
                            event { click { ctx.ui.workspaceDeleteTargetId = ""; ctx.ui.workspaceActionError = "" } }
                        }
                        Text {
                            attr { text(if (ctx.ui.workspaceActionBusy) "删除中..." else "删除注册"); width(112f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(ctx.themeColors.stateErrorPrimary) }
                            event { click { if (!ctx.ui.workspaceActionBusy) ctx.confirmWorkspaceDelete() } }
                        }
                    }
                }
            }
        }
    }
}
