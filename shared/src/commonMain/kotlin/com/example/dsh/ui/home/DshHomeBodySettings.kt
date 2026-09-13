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
        vif({ ctx.settingsPageVisible }) {
            DshSettingsPage(
                loading = { ctx.settingsLoading },
                error = { ctx.settingsError },
                snapshot = { ctx.settingsSnapshot },
                isRemoteHost = { ctx.isRemoteHost },
                connectionModeLabel = { ctx.connectionModeLabel() },
                modelsSummary = { ctx.modelsSummary() },
                hostVersion = { ctx.hostVersion },
                themeMode = { ctx.themeMode },
                processDisplayMode = { ctx.chatProcessMode },
                agentPresetLabel = { ctx.agentModeLabel },
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
        vif({ ctx.personalizationPageVisible }) {
            DshPersonalizationPage(
                mode = { ctx.chatProcessMode },
                expandInModal = { ctx.chatExpandInModal },
                showConnectors = { ctx.chatShowConnectors },
                showResultCards = { ctx.chatShowResultCards },
                onPickMode = { ctx.applyChatProcessMode(it) },
                onToggleExpandInModal = { ctx.applyChatExpandInModal(it) },
                onToggleConnectors = { ctx.applyChatShowConnectors(it) },
                onToggleResultCards = { ctx.applyChatShowResultCards(it) },
                onClose = { ctx.closePersonalizationPage() },
                colors = { ctx.themeColors },
            )
        }

        // ===== 展开内容底部大弹层（「弹窗查看」开启时） =====
        vif({ ctx.expandedPayload != null }) {
            DshExpandedContentModal(
                payload = { ctx.expandedPayload },
                onClose = { ctx.closeExpandedModal() },
                colors = { ctx.themeColors },
            )
        }

        vif({ ctx.pluginInventoryVisible }) {
            DshPluginSettingsView(
                activeTab = { ctx.pluginActiveTab }, onSelectTab = { ctx.selectPluginTab(it) },
                loading = { ctx.pluginInventoryLoading }, error = { ctx.pluginInventoryError },
                keyword = { ctx.pluginSearchInput }, onKeyword = { ctx.onPluginKeyword(it) },
                hasKeyword = { ctx.pluginSearchHasText }, onClearKeyword = { ctx.clearPluginKeyword() },
                onSearchInputRef = { ctx.pluginSearchInputView = it.view },
                onRefresh = { ctx.refreshPluginInventory() }, onClose = { ctx.closePluginInventory() },
                rows = { ctx.pluginRows }, total = { ctx.pluginTotal },
                expandedId = { ctx.pluginExpandedId }, busyId = { ctx.pluginBusyId },
                onToggleExpand = { ctx.togglePluginExpanded(it) },
                actionError = { ctx.pluginActionError }, actionNotice = { ctx.pluginNotice },
                onToggleEnabled = { entry, enable -> ctx.requestPluginToggle(entry, enable) },
                onReload = { ctx.requestPluginReload(it) },
                confirmEntry = { ctx.pluginActionTarget }, confirmAction = { ctx.pluginConfirmAction },
                onConfirm = { ctx.confirmPluginAction() }, onCancelConfirm = { ctx.cancelPluginAction() },
                configCards = { ctx.pluginConfigCards }, configLoading = { ctx.pluginConfigLoading },
                configError = { ctx.pluginConfigError }, configWritable = { ctx.pluginConfigWritable },
                configDraft = { ns, key -> ctx.pluginConfigDraft(ns, key) },
                configSecretDraft = { ns -> ctx.pluginConfigSecretDraft(ns) },
                configCollapsed = { ns -> ctx.isPluginConfigCollapsed(ns) },
                configBusyNamespace = { ctx.pluginConfigBusyNamespace },
                configCardError = { ns -> ctx.pluginConfigCardError[ns] ?: "" },
                configCardNotice = { ns -> ctx.pluginConfigCardNotice[ns] ?: "" },
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
        vif({ ctx.modelsPageVisible }) {
            DshModelsPage(
                loading = { ctx.modelsLoading },
                error = { ctx.modelsError },
                writable = { ctx.modelsWritable },
                configuredProviders = { ctx.modelsConfiguredProviders },
                addableProviders = { ctx.modelsAddableProviders },
                editingProvider = { ctx.modelsEditingProvider },
                pickerVisible = { ctx.modelsPickerVisible },
                customAdding = { ctx.modelsCustomAdding },
                savedNotice = { ctx.modelsSavedNotice },
                editorAdvanced = { ctx.modelsEditorAdvanced },
                onToggleEditorAdvanced = { ctx.modelsEditorAdvanced = !ctx.modelsEditorAdvanced },
                draftBaseUrl = { ctx.modelsDraftBaseUrl },
                draftApiKey = { ctx.modelsDraftApiKey },
                draftModels = { ctx.modelsDraftModels },
                saving = { ctx.modelsSaving },
                saveError = { ctx.modelsSaveError },
                customProtocols = { ctx.modelsProtocols },
                customRoute = { ctx.modelsCustomRoute },
                customName = { ctx.modelsCustomName },
                customBaseUrl = { ctx.modelsCustomBaseUrl },
                customProtocol = { ctx.modelsCustomProtocol },
                customApiKey = { ctx.modelsCustomApiKey },
                customModels = { ctx.modelsCustomModels },
                customBusy = { ctx.modelsCustomBusy },
                customError = { ctx.modelsCustomError },
                deleteTarget = { ctx.modelsDeleteTarget },
                deleting = { ctx.modelsDeleting },
                onClose = { ctx.closeModelsPage() },
                onRetry = { ctx.reloadModelsSettings() },
                onEdit = { ctx.openProviderEditor(it) },
                onCancelAdd = { ctx.cancelAddProvider() },
                onOpenPicker = { ctx.modelsPickerVisible = true },
                onClosePicker = { ctx.modelsPickerVisible = false },
                onSelectAddable = { ctx.selectAddableProvider(it) },
                onOpenCustom = { ctx.openCustomProvider() },
                onCancelCustom = { ctx.cancelCustomProvider() },
                onBaseUrlChange = { ctx.modelsDraftBaseUrl = it; ctx.modelsSaveError = "" },
                onApiKeyChange = { ctx.modelsDraftApiKey = it; ctx.modelsSaveError = "" },
                onModelChange = { index, field, value ->
                    ctx.updateDraftModel(index, field, value)
                    ctx.modelsSaveError = ""
                },
                onAddModel = { ctx.addDraftModel() },
                onRemoveModel = { ctx.removeDraftModel(it) },
                onApply = { ctx.applyProviderEditor(it) },
                onCustomRoute = { ctx.modelsCustomRoute = it; ctx.modelsCustomError = "" },
                onCustomName = { ctx.modelsCustomName = it },
                onCustomBaseUrl = { ctx.modelsCustomBaseUrl = it; ctx.modelsCustomError = "" },
                onCustomProtocol = { ctx.modelsCustomProtocol = it },
                onCustomApiKey = { ctx.modelsCustomApiKey = it },
                onCustomModelChange = { index, field, value -> ctx.updateCustomModel(index, field, value) },
                onCustomAddModel = { ctx.modelsCustomModels.add(DshProviderModel()) },
                onCustomRemoveModel = { ctx.removeCustomModel(it) },
                onApplyCustom = { ctx.applyCustomProvider() },
                onRequestDelete = { ctx.requestRemoveProvider(it) },
                onConfirmDelete = { ctx.confirmRemoveProvider() },
                onCancelDelete = { ctx.modelsDeleteTarget = null },
                colors = { ctx.themeColors },
            )
        }

    }
}

internal fun DshHomePage.bodySettingsChoiceAndCredentials(): ViewBuilder {
    val ctx = this
    return {
        // ===== 设置项选择器（权限预设 / 语言 / 外观） =====
        vif({ ctx.settingsChoiceKind.isNotEmpty() }) {
            DshSettingsChoicePicker(
                title = ctx.settingsChoiceTitle,
                options = { ctx.settingsChoiceOptions },
                selectedValue = {
                    when (ctx.settingsChoiceKind) {
                        "permission" -> ctx.settingsSnapshot.permissionPreset
                        "locale" -> ctx.settingsSnapshot.localeValue
                        "theme" -> DshThemeManager.preferenceValue
                        else -> ""
                    }
                },
                busy = { ctx.settingsChoiceBusy },
                onClose = { if (!ctx.settingsChoiceBusy) ctx.settingsChoiceKind = "" },
                onSelect = { ctx.applySettingsChoice(it) },
                colors = { ctx.themeColors },
            )
        }

        // ===== API Key 设置弹窗 =====
        // 输入并保存 DeepSeek API Key（也可用于修改远程 DSH 的 Key）。
        vif({ ctx.credentialSetupVisible }) {
            DshCredentialSetupModal(
                title = { ctx.credentialSetupTitle },
                busy = { ctx.credentialSetupBusy },
                error = { ctx.credentialSetupError },
                inputRef = {
                    ctx.apiKeyInputView = it.view
                    ctx.apiKeyInputView?.setText(ctx.apiKeyDraft)
                },
                onApiKeyChange = {
                    ctx.apiKeyDraft = it
                    ctx.credentialSetupError = ""
                },
                onSave = { ctx.saveDeepSeekApiKey() },
                onClose = { ctx.closeCredentialSettings() },
                colors = { ctx.themeColors },
            )
        }
        // ===== 连接设置弹窗 =====
        // 配置连接方式（扫码 RELAY / SSH）：主机、端口、用户名、私钥导入、指纹确认、DSH 端口。
        vif({ ctx.sshSettingsVisible }) {
            DshConnectionSettingsModal(
                sshMode = { ctx.sshMode },
                host = { ctx.sshHost },
                user = { ctx.sshUser },
                port = { ctx.sshPort },
                dshPort = { ctx.sshDshPort },
                keyLabel = { ctx.sshKeyLabel },
                keyPassphrase = { ctx.sshKeyPassphrase },
                busy = { ctx.sshSettingsBusy },
                error = { ctx.sshSettingsError },
                onModeChange = { ctx.setConnectionMode(it) },
                onHostChange = { ctx.sshHost = it; ctx.sshSettingsError = "" },
                onUserChange = { ctx.sshUser = it; ctx.sshSettingsError = "" },
                onPortChange = { ctx.sshPort = it; ctx.sshSettingsError = "" },
                onDshPortChange = { ctx.sshDshPort = it; ctx.sshSettingsError = "" },
                onPickKey = { ctx.pickSshKey() },
                onPassphraseChange = { ctx.sshKeyPassphrase = it },
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
        vif({ ctx.workspacePickerVisible && ctx.isRemoteHost }) {
            DshWorkspacePickerModal(
                screen = { ctx.workspacePickerScreen },
                folders = { ctx.workspacePickerFolders },
                activeWorkspaceId = { ctx.activeWorkspaceId() },
                busy = { ctx.workspacePickerBusy || ctx.workspaceAddBusy },
                error = { ctx.workspacePickerError },
                onSelectFolder = { ctx.switchWorkspaceTo(it) },
                onAddFolder = { ctx.openWorkspaceAddFolder() },
                onBack = { ctx.onWorkspacePickerBack() },
                onClose = { ctx.closeWorkspacePicker() },
                path = { ctx.workspaceAddPath },
                home = { ctx.workspaceAddHome },
                entries = { ctx.workspaceAddEntries },
                newName = { ctx.workspaceAddNewName },
                onDirectorySelect = { ctx.loadWorkspaceAddDirectory(it) },
                onNewNameChange = { ctx.workspaceAddNewName = it },
                onCreateDirectory = { ctx.createWorkspaceAddDirectory() },
                onAdopt = { ctx.adoptWorkspaceAddDirectory() },
                colors = { ctx.themeColors },
            )
        }
        // ===== 重命名工作区 弹窗 =====
        // 内嵌 Modal：输入新名称 → 保存/取消；错误信息红字显示。
        vif({ ctx.workspaceRenameTargetId.isNotEmpty() && ctx.isRemoteHost }) {
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
                            text(ctx.workspaceRenameDraft)
                        }
                        event { textDidChange { ctx.workspaceRenameDraft = it.text } }
                    }
                    vif({ ctx.workspaceActionError.isNotEmpty() }) {
                        Text { attr { text(ctx.workspaceActionError); marginTop(8f); fontSize(12f); color(ctx.themeColors.stateErrorPrimary) } }
                    }
                    View {
                        attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                        Text {
                            attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(ctx.themeColors.labelTertiary) }
                            event { click { ctx.workspaceRenameTargetId = ""; ctx.workspaceActionError = "" } }
                        }
                        Text {
                            attr { text(if (ctx.workspaceActionBusy) "保存中..." else "保存"); width(78f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(ctx.themeColors.stateBusinessPrimary) }
                            event { click { if (!ctx.workspaceActionBusy) ctx.saveWorkspaceRename() } }
                        }
                    }
                }
            }
        }
        // ===== 删除工作区注册 确认弹窗 =====
        // 仅从列表移除注册，不删除实际目录/会话/日志；红色「删除注册」按钮。
        vif({ ctx.workspaceDeleteTargetId.isNotEmpty() && ctx.isRemoteHost }) {
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
                    vif({ ctx.workspaceActionError.isNotEmpty() }) {
                        Text { attr { text(ctx.workspaceActionError); marginTop(8f); fontSize(12f); color(ctx.themeColors.stateErrorPrimary) } }
                    }
                    View {
                        attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                        Text {
                            attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(ctx.themeColors.labelTertiary) }
                            event { click { ctx.workspaceDeleteTargetId = ""; ctx.workspaceActionError = "" } }
                        }
                        Text {
                            attr { text(if (ctx.workspaceActionBusy) "删除中..." else "删除注册"); width(112f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(ctx.themeColors.stateErrorPrimary) }
                            event { click { if (!ctx.workspaceActionBusy) ctx.confirmWorkspaceDelete() } }
                        }
                    }
                }
            }
        }
    }
}
