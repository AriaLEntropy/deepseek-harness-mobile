package com.example.dsh.models

import com.example.dsh.theme.DshColorTokens

import com.example.dsh.models.DshProviderConfig
import com.example.dsh.models.DshProviderModel
import com.example.dsh.models.dshCustomRouteError
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.Button
import com.example.dsh.theme.DshDefaultTheme
import com.example.dsh.ui.home.DshHitButton

/**
 * 设置页「模型」详情页：对齐电脑端 DSH settings.models 的右侧主内容区。
 * 移动端只保留内容列，结构为：标题 + 说明 + 已配置提供方行 + 底部
 * 「添加提供方 / 添加自定义提供方」；行内展开编辑器（API 密钥为主字段，
 * API 地址与模型目录收在可折叠的「自定义设置」里）。
 */
internal fun ViewContainer<*, *>.DshModelsPage(
    loading: () -> Boolean,
    error: () -> String,
    writable: () -> Boolean,
    configuredProviders: () -> ObservableList<DshProviderConfig>,
    addableProviders: () -> ObservableList<DshProviderConfig>,
    editingProvider: () -> String,
    pickerVisible: () -> Boolean,
    customAdding: () -> Boolean,
    savedNotice: () -> String,
    editorAdvanced: () -> Boolean,
    onToggleEditorAdvanced: () -> Unit,
    draftBaseUrl: () -> String,
    draftApiKey: () -> String,
    draftModels: () -> ObservableList<DshProviderModel>,
    saving: () -> Boolean,
    saveError: () -> String,
    customProtocols: () -> List<String>,
    customRoute: () -> String,
    customName: () -> String,
    customBaseUrl: () -> String,
    customProtocol: () -> String,
    customApiKey: () -> String,
    customModels: () -> ObservableList<DshProviderModel>,
    customBusy: () -> Boolean,
    customError: () -> String,
    deleteTarget: () -> DshProviderConfig?,
    deleting: () -> Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (DshProviderConfig) -> Unit,
    onCancelAdd: () -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSelectAddable: (DshProviderConfig) -> Unit,
    onOpenCustom: () -> Unit,
    onCancelCustom: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (Int, String, String) -> Unit,
    onAddModel: () -> Unit,
    onRemoveModel: (Int) -> Unit,
    onApply: (DshProviderConfig) -> Unit,
    onCustomRoute: (String) -> Unit,
    onCustomName: (String) -> Unit,
    onCustomBaseUrl: (String) -> Unit,
    onCustomProtocol: (String) -> Unit,
    onCustomApiKey: (String) -> Unit,
    onCustomModelChange: (Int, String, String) -> Unit,
    onCustomAddModel: () -> Unit,
    onCustomRemoveModel: (Int) -> Unit,
    onApplyCustom: () -> Unit,
    onRequestDelete: (DshProviderConfig) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            backgroundColor(colors().bgLayer2)
        }
        View {
            attr {
                height(pagerData.statusBarHeight + 52f)
                paddingTop(pagerData.statusBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(8f)
                paddingRight(8f)
                backgroundColor(colors().bgLayer2)
            }
            View {
                attr { size(36f, 36f); allCenter() }
                Image {
                    attr {
                        src(ImageUri.commonAssets("chevron-left.svg"))
                        size(20f, 20f)
                        tintColor(colors().labelSecondary)
                    }
                }
                DshHitButton(onClose)
            }
            Text {
                attr {
                    text("模型")
                    flex(1f)
                    textAlignCenter()
                    fontSize(17f)
                    fontWeightBold()
                    color(colors().labelPrimary)
                }
            }
            View { attr { size(36f, 36f) } }
        }
        View { attr { height(1f); backgroundColor(colors().borderL1) } }

        Scroller {
            attr {
                flex(1f)
                width(pagerData.pageViewWidth)
                backgroundColor(colors().bgLayer2)
            }
            View {
                attr {
                    flexDirectionColumn()
                    paddingLeft(16f)
                    paddingRight(16f)
                    paddingTop(18f)
                    paddingBottom(32f)
                }
                Text {
                    attr {
                        text("填入各提供方的 API 密钥即可使用其模型。")
                        fontSize(14f)
                        lineHeight(22f)
                        color(colors().labelTertiary)
                    }
                }
                vif({ savedNotice().isNotEmpty() }) {
                    Text {
                        attr {
                            text(savedNotice())
                            marginTop(10f)
                            fontSize(12f)
                            lineHeight(18f)
                            color(colors().stateSuccessPrimary)
                        }
                    }
                }
                vif({ !loading() && error().isNotEmpty() }) {
                    View {
                        attr { flexDirectionColumn(); marginTop(12f) }
                        Text {
                            attr {
                                text("加载提供方目录失败：${error()}")
                                fontSize(12f)
                                lineHeight(18f)
                                color(colors().stateErrorPrimary)
                            }
                        }
                        DshModelsPillButton(
                            label = "重试",
                            enabled = true,
                            dense = false,
                            tone = DshModelsButtonTone.SECONDARY,
                            marginTop = 10f,
                            onClick = onRetry,
                            colors = colors,
                        )
                    }
                }
                vif({ !loading() && error().isEmpty() && !writable() }) {
                    Text {
                        attr {
                            text("当前部署的设置文档为只读。")
                            marginTop(10f)
                            fontSize(12f)
                            lineHeight(18f)
                            color(colors().stateWarnLabel)
                        }
                    }
                }
                vif({ loading() }) {
                    Text {
                        attr {
                            text("正在加载模型设置…")
                            marginTop(20f)
                            fontSize(14f)
                            color(colors().labelTertiary)
                        }
                    }
                }
                vif({ !loading() && error().isEmpty() }) {
                    View {
                        attr { flexDirectionColumn(); marginTop(14f) }
                        vforIndex({ configuredProviders() }) { provider, _, _ ->
                            DshModelsProviderCard(
                                provider = provider,
                                editing = { !customAdding() && editingProvider() == provider.provider },
                                showRowActions = true,
                                writable = writable,
                                saving = { saving() && editingProvider() == provider.provider },
                                advanced = editorAdvanced,
                                onToggleAdvanced = onToggleEditorAdvanced,
                                draftBaseUrl = draftBaseUrl,
                                draftApiKey = draftApiKey,
                                draftModels = draftModels,
                                saveError = { if (editingProvider() == provider.provider) saveError() else "" },
                                onEdit = { onEdit(provider) },
                                onCancelEditor = { onEdit(provider) },
                                onBaseUrlChange = onBaseUrlChange,
                                onApiKeyChange = onApiKeyChange,
                                onModelChange = onModelChange,
                                onAddModel = onAddModel,
                                onRemoveModel = onRemoveModel,
                                onApply = { onApply(provider) },
                                onRequestDelete = { onRequestDelete(provider) },
                                colors = colors,
                            )
                        }

                        // 新增已有提供方：以空草稿打开其编辑器
                        vif({ editingProvider().isNotEmpty() && !customAdding()
                            && addableProviders().any { it.provider == editingProvider() } }) {
                            val addProvider = addableProviders().firstOrNull { it.provider == editingProvider() }
                            if (addProvider != null) {
                                DshModelsProviderCard(
                                    provider = addProvider,
                                    editing = { true },
                                    showRowActions = false,
                                    writable = writable,
                                    saving = saving,
                                    advanced = editorAdvanced,
                                    onToggleAdvanced = onToggleEditorAdvanced,
                                    draftBaseUrl = draftBaseUrl,
                                    draftApiKey = draftApiKey,
                                    draftModels = draftModels,
                                    saveError = saveError,
                                    onEdit = onCancelAdd,
                                    onCancelEditor = onCancelAdd,
                                    onBaseUrlChange = onBaseUrlChange,
                                    onApiKeyChange = onApiKeyChange,
                                    onModelChange = onModelChange,
                                    onAddModel = onAddModel,
                                    onRemoveModel = onRemoveModel,
                                    onApply = { onApply(addProvider) },
                                    onRequestDelete = {},
                                    colors = colors,
                                )
                            }
                        }

                        // 底部：添加提供方 / 添加自定义提供方
                        vif({ !customAdding()
                            && !(editingProvider().isNotEmpty()
                                && addableProviders().any { it.provider == editingProvider() }) }) {
                            View {
                                attr { flexDirectionRow(); marginTop(10f) }
                                DshModelsAddButton(
                                    label = "添加提供方",
                                    enabled = writable() && addableProviders().isNotEmpty(),
                                    onClick = onOpenPicker,
                                    colors = colors,
                                )
                                View { attr { width(10f) } }
                                DshModelsAddButton(
                                    label = "添加自定义提供方",
                                    enabled = writable() && customProtocols().isNotEmpty(),
                                    onClick = onOpenCustom,
                                    colors = colors,
                                )
                            }
                        }
                        vif({ customAdding() }) {
                            DshModelsCustomProviderCard(
                                protocols = customProtocols,
                                route = customRoute,
                                name = customName,
                                baseUrl = customBaseUrl,
                                protocol = customProtocol,
                                apiKey = customApiKey,
                                models = customModels,
                                busy = customBusy,
                                error = customError,
                                taken = {
                                    val list = mutableListOf<String>()
                                    for (p in configuredProviders()) list += p.provider
                                    for (p in addableProviders()) list += p.provider
                                    list
                                },
                                writable = writable,
                                onRoute = onCustomRoute,
                                onName = onCustomName,
                                onBaseUrl = onCustomBaseUrl,
                                onProtocol = onCustomProtocol,
                                onApiKey = onCustomApiKey,
                                onModelChange = onCustomModelChange,
                                onAddModel = onCustomAddModel,
                                onRemoveModel = onCustomRemoveModel,
                                onCancel = onCancelCustom,
                                onApply = onApplyCustom,
                                colors = colors,
                            )
                        }
                    }
                }
            }
        }
    }

    // 选择要添加的已有提供方
    vif({ pickerVisible() }) {
        Modal(inWindow = true) {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0x66000000))
                paddingTop(pagerData.statusBarHeight + 20f)
                paddingBottom(pagerData.safeAreaInsets.bottom + 20f)
            }
            View {
                attr {
                    flex(1f)
                    marginLeft(16f)
                    marginRight(16f)
                    borderRadius(16f)
                    flexDirectionColumn()
                    backgroundColor(colors().bgLayer1)
                }
                View {
                    attr { height(52f); flexDirectionRow(); alignItemsCenter(); paddingLeft(16f); paddingRight(16f) }
                    Text {
                        attr { text("选择提供方"); flex(1f); fontSize(16f); fontWeightMedium(); color(colors().labelPrimary) }
                    }
                    Text {
                        attr { text("取消"); fontSize(14f); color(colors().labelSecondary) }
                        event { click { onClosePicker() } }
                    }
                }
                View { attr { height(1f); backgroundColor(colors().borderL1) } }
                Scroller {
                    attr { flex(1f) }
                    vforIndex({ addableProviders() }) { provider, _, _ ->
                        View {
                            attr {
                                height(52f)
                                paddingLeft(16f)
                                paddingRight(16f)
                                flexDirectionRow()
                                alignItemsCenter()
                                border(Border(0f, BorderStyle.SOLID, colors().borderL1))
                            }
                            Text {
                                attr { text(provider.displayName); flex(1f); fontSize(15f); color(colors().labelPrimary) }
                            }
                            Text {
                                attr { text(provider.provider); fontSize(12f); color(colors().labelTertiary) }
                            }
                            event { click { onSelectAddable(provider) } }
                        }
                    }
                    View { attr { height(12f) } }
                }
            }
        }
    }

    // 删除确认
    vif({ deleteTarget() != null }) {
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
                    maxWidth(380f)
                    flexDirectionColumn()
                    paddingLeft(24f)
                    paddingRight(24f)
                    paddingTop(22f)
                    paddingBottom(24f)
                    borderRadius(24f)
                    backgroundColor(colors().bgLayer2)
                }
                val target = deleteTarget()
                Text {
                    attr {
                        text("删除 ${target?.displayName.orEmpty()}？")
                        fontSize(16f)
                        lineHeight(24f)
                        fontWeightMedium()
                        color(colors().labelPrimary)
                    }
                }
                Text {
                    attr {
                        text(
                            if (target?.credential != null) "删除会移除该提供方的配置和存储的 API 密钥。"
                            else "删除会移除该提供方的配置；其使用的凭证（如有）由其他位置管理，将会保留。",
                        )
                        marginTop(8f)
                        fontSize(14f)
                        lineHeight(22f)
                        color(colors().labelPrimary)
                    }
                }
                View {
                    attr { marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    DshModelsPillButton(
                        label = "取消",
                        enabled = !deleting(),
                        dense = false,
                        tone = DshModelsButtonTone.SECONDARY,
                        marginRight = 8f,
                        onClick = onCancelDelete,
                        colors = colors,
                    )
                    DshModelsPillButton(
                        label = if (deleting()) "删除中…" else "删除",
                        enabled = !deleting(),
                        dense = false,
                        tone = DshModelsButtonTone.DANGER_OUTLINE,
                        onClick = onConfirmDelete,
                        colors = colors,
                    )
                }
            }
        }
    }
}

private enum class DshModelsButtonTone { SECONDARY, DANGER, DANGER_OUTLINE }

private fun ViewContainer<*, *>.DshModelsPillButton(
    label: String,
    enabled: Boolean,
    dense: Boolean,
    tone: DshModelsButtonTone,
    onClick: () -> Unit,
    colors: () -> DshColorTokens,
    marginTop: Float = 0f,
    marginLeft: Float = 0f,
    marginRight: Float = 0f,
) {
    View {
        attr {
            height(if (dense) 28f else 36f)
            paddingLeft(if (dense) 10f else 14f)
            paddingRight(if (dense) 10f else 14f)
            marginTop(marginTop)
            marginLeft(marginLeft)
            marginRight(marginRight)
            allCenter()
            borderRadius(if (dense) 14f else 18f)
            when (tone) {
                DshModelsButtonTone.SECONDARY ->
                    border(Border(0.5f, BorderStyle.SOLID, colors().borderL3))
                DshModelsButtonTone.DANGER_OUTLINE ->
                    border(Border(0.5f, BorderStyle.SOLID, colors().stateErrorPrimary))
                DshModelsButtonTone.DANGER -> Unit
            }
            opacity(if (enabled) 1f else 0.4f)
        }
        Text {
            attr {
                text(label)
                fontSize(if (dense) 12f else 14f)
                lineHeight(if (dense) 18f else 22f)
                color(when (tone) {
                    DshModelsButtonTone.SECONDARY -> colors().labelPrimary
                    DshModelsButtonTone.DANGER, DshModelsButtonTone.DANGER_OUTLINE -> colors().stateErrorPrimary
                })
            }
        }
        DshHitButton { if (enabled) onClick() }
    }
}

private fun ViewContainer<*, *>.DshModelsAddButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr {
            flex(1f)
            height(44f)
            allCenter()
            flexDirectionRow()
            borderRadius(16f)
            border(Border(0.5f, BorderStyle.DASHED, colors().borderL3))
            opacity(if (enabled) 1f else 0.4f)
        }
        Image {
            attr {
                src(ImageUri.commonAssets("plus.svg"))
                size(14f, 14f)
                marginRight(6f)
                tintColor(colors().labelPrimary)
            }
        }
        Text {
            attr {
                text(label)
                fontSize(14f)
                color(colors().labelPrimary)
            }
        }
        DshHitButton { if (enabled) onClick() }
    }
}

private fun ViewContainer<*, *>.DshModelsProviderCard(
    provider: DshProviderConfig,
    editing: () -> Boolean,
    showRowActions: Boolean,
    writable: () -> Boolean,
    saving: () -> Boolean,
    advanced: () -> Boolean,
    onToggleAdvanced: () -> Unit,
    draftBaseUrl: () -> String,
    draftApiKey: () -> String,
    draftModels: () -> ObservableList<DshProviderModel>,
    saveError: () -> String,
    onEdit: () -> Unit,
    onCancelEditor: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (Int, String, String) -> Unit,
    onAddModel: () -> Unit,
    onRemoveModel: (Int) -> Unit,
    onApply: () -> Unit,
    onRequestDelete: () -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr {
            flexDirectionColumn()
            marginBottom(8f)
            paddingTop(12f)
            paddingBottom(12f)
            paddingLeft(14f)
            paddingRight(14f)
            borderRadius(16f)
            border(Border(1f, BorderStyle.SOLID, colors().borderL4))
        }
        if (showRowActions) {
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(provider.displayName)
                        fontSize(14f)
                        fontWeightMedium()
                        color(colors().labelPrimary)
                    }
                }
                if (provider.declared) {
                    View {
                        attr {
                            paddingLeft(6f)
                            paddingRight(6f)
                            paddingTop(1f)
                            paddingBottom(1f)
                            marginLeft(6f)
                            borderRadius(4f)
                            border(Border(1f, BorderStyle.SOLID, colors().borderL3))
                        }
                        Text { attr { text("自定义"); fontSize(11f); lineHeight(16f); color(colors().labelSecondary) } }
                    }
                }
                val credential = provider.credential
                if (credential != null) {
                    View {
                        attr {
                            size(8f, 8f)
                            marginLeft(6f)
                            borderRadius(4f)
                            backgroundColor(if (credential.configured) colors().stateSuccessPrimary else colors().stateErrorPrimary)
                        }
                    }
                }
                View { attr { flex(1f) } }
                DshModelsPillButton(
                    label = "编辑",
                    enabled = true,
                    dense = true,
                    tone = DshModelsButtonTone.SECONDARY,
                    marginLeft = 4f,
                    onClick = onEdit,
                    colors = colors,
                )
                if (provider.removable) {
                    DshModelsPillButton(
                        label = "删除",
                        enabled = true,
                        dense = true,
                        tone = DshModelsButtonTone.DANGER,
                        marginLeft = 4f,
                        onClick = onRequestDelete,
                        colors = colors,
                    )
                }
            }
        }
        vif({ editing() }) {
            View {
                attr { flexDirectionColumn(); marginTop(12f); borderRadius(12f)
                    backgroundColor(colors().bgModulePlatform); padding(14f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text(provider.displayName); fontSize(14f); lineHeight(22f); fontWeightMedium(); color(colors().labelPrimary) } }
                    if (provider.provider != provider.displayName) {
                        Text { attr { text(provider.provider); marginLeft(8f); fontSize(12f); lineHeight(18f); color(colors().labelTertiary) } }
                    }
                }
                DshModelsFieldLabel("API 密钥", colors)
                val credential = provider.credential
                if (credential != null && !credential.writable) {
                    View {
                        attr {
                            height(44f)
                            marginTop(8f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(12f)
                            paddingRight(12f)
                            borderRadius(8f)
                            backgroundColor(colors().bgModulePlatform)
                        }
                        Text { attr { text("由启动环境提供（只读）"); fontSize(13f); color(colors().labelTertiary) } }
                    }
                } else {
                    DshModelsInput(
                        value = draftApiKey,
                        placeholder = when {
                            credential?.configured == true -> "已配置——输入新值可替换"
                            provider.apiKeyEnv.isEmpty() -> "输入 API 密钥，或留空使用环境认证"
                            else -> "输入 API 密钥"
                        },
                        enabled = writable() && !saving(),
                        password = true,
                        marginTop = 8f,
                        onChange = onApiKeyChange,
                        colors = colors,
                    )
                }

                // 自定义设置（可折叠）：API 地址 + 模型目录
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginTop(14f) }
                    event { click { onToggleAdvanced() } }
                    Text {
                        attr {
                            text("自定义设置"
                                + if (provider.baseUrl.isNotEmpty() || provider.modelsOverridden || draftModels().isNotEmpty()) " · 已覆盖" else "")
                            flex(1f)
                            fontSize(12f)
                            fontWeightMedium()
                            color(colors().labelSecondary)
                        }
                    }
                    Text { attr { text(if (advanced()) "▾" else "▸"); fontSize(11f); color(colors().labelTertiary) } }
                }
                vif({ advanced() }) {
                    View {
                        attr { flexDirectionColumn() }
                        DshModelsFieldLabel("API 地址", colors)
                        DshModelsInput(
                            value = draftBaseUrl,
                            placeholder = if (provider.settingsNs == "llm-deepseek") "https://api.deepseek.com" else "提供方默认",
                            enabled = writable() && !saving(),
                            password = false,
                            marginTop = 8f,
                            onChange = onBaseUrlChange,
                            colors = colors,
                        )
                        DshModelsFieldLabel("模型目录", colors)
                        Text {
                            attr {
                                text(if (provider.modelsOverridden) "已自定义模型目录" else "正在使用适配器默认模型")
                                marginTop(4f)
                                fontSize(12f)
                                lineHeight(18f)
                                color(colors().labelTertiary)
                            }
                        }
                        View {
                            attr { flexDirectionColumn(); marginTop(6f) }
                            vforIndex({ draftModels() }) { model, index, _ ->
                                DshModelsModelRow(
                                    model = model,
                                    enabled = writable() && !saving(),
                                    onChange = { field, value -> onModelChange(index, field, value) },
                                    onRemove = { onRemoveModel(index) },
                                    colors = colors,
                                )
                            }
                        }
                        DshModelsPillButton(
                            label = "添加模型",
                            enabled = writable() && !saving(),
                            dense = true,
                            tone = DshModelsButtonTone.SECONDARY,
                            marginTop = 8f,
                            onClick = onAddModel,
                            colors = colors,
                        )
                    }
                }

                vif({ saveError().isNotEmpty() }) {
                    Text {
                        attr {
                            text(saveError())
                            marginTop(10f)
                            fontSize(12f)
                            lineHeight(18f)
                            color(colors().stateErrorPrimary)
                        }
                    }
                }
                View {
                    attr { marginTop(16f); flexDirectionRow(); justifyContentFlexEnd() }
                    DshModelsPillButton(
                        label = "取消",
                        enabled = !saving(),
                        dense = false,
                        tone = DshModelsButtonTone.SECONDARY,
                        marginRight = 8f,
                        onClick = onCancelEditor,
                        colors = colors,
                    )
                    Button {
                        attr {
                            height(36f)
                            paddingLeft(14f)
                            paddingRight(14f)
                            borderRadius(18f)
                            backgroundColor(colors().buttonPrimaryFill)
                            opacity(if (saving() || !writable()) 0.4f else 1f)
                            titleAttr {
                                text(if (saving()) "保存中…" else "保存")
                                fontSize(14f)
                                color(colors().labelPrimaryForeground)
                            }
                        }
                        event { click { if (!saving() && writable()) onApply() } }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshModelsCustomProviderCard(
    protocols: () -> List<String>,
    route: () -> String,
    name: () -> String,
    baseUrl: () -> String,
    protocol: () -> String,
    apiKey: () -> String,
    models: () -> ObservableList<DshProviderModel>,
    busy: () -> Boolean,
    error: () -> String,
    taken: () -> List<String>,
    writable: () -> Boolean,
    onRoute: (String) -> Unit,
    onName: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onProtocol: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onModelChange: (Int, String, String) -> Unit,
    onAddModel: () -> Unit,
    onRemoveModel: (Int) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr {
            flexDirectionColumn()
            marginTop(12f)
            padding(14f)
            borderRadius(12f)
            backgroundColor(colors().bgModulePlatform)
        }
        Text { attr { text("自定义提供方"); fontSize(14f); lineHeight(22f); fontWeightMedium(); color(colors().labelPrimary) } }

        DshModelsFieldLabel("Provider ID", colors)
        DshModelsInput(
            value = route,
            placeholder = "acme-gateway",
            enabled = writable() && !busy(),
            password = false,
            marginTop = 8f,
            onChange = onRoute,
            colors = colors,
        )
        val routeError = dshCustomRouteError(route(), taken())
        Text {
            attr {
                text(if (routeError.isNotEmpty()) routeError else "以小写字母开头的标识，在请求中唯一标识该提供方，并用于派生凭据名。")
                marginTop(4f)
                fontSize(12f)
                lineHeight(18f)
                color(if (routeError.isNotEmpty()) colors().stateErrorPrimary else colors().labelTertiary)
            }
        }

        DshModelsFieldLabel("显示名称", colors)
        DshModelsInput(
            value = name,
            placeholder = if (route().isEmpty()) "显示名称" else route(),
            enabled = writable() && !busy(),
            password = false,
            marginTop = 8f,
            onChange = onName,
            colors = colors,
        )

        DshModelsFieldLabel("API 地址", colors)
        DshModelsInput(
            value = baseUrl,
            placeholder = "https://gateway.example/v1",
            enabled = writable() && !busy(),
            password = false,
            marginTop = 8f,
            onChange = onBaseUrl,
            colors = colors,
        )

        DshModelsFieldLabel("API 协议", colors)
        View {
            attr { flexDirectionRow(); marginTop(8f) }
            protocols().forEach { choice ->
                val active = protocol() == choice
                View {
                    attr {
                        paddingLeft(10f)
                        paddingRight(10f)
                        paddingTop(6f)
                        paddingBottom(6f)
                        marginRight(6f)
                        borderRadius(8f)
                        backgroundColor(if (active) colors().stateBusinessTertiary else colors().bgLayer2)
                    }
                    Text {
                        attr { text(choice); fontSize(12f); color(if (active) colors().stateBusinessPrimary else colors().labelSecondary) }
                    }
                    event { click { if (writable() && !busy()) onProtocol(choice) } }
                }
            }
        }

        DshModelsFieldLabel("API 密钥", colors)
        DshModelsInput(
            value = apiKey,
            placeholder = "输入 API 密钥，或留空使用环境认证",
            enabled = writable() && !busy(),
            password = true,
            marginTop = 8f,
            onChange = onApiKey,
            colors = colors,
        )

        DshModelsFieldLabel("模型目录", colors)
        View {
            attr { flexDirectionColumn(); marginTop(6f) }
            vforIndex({ models() }) { model, index, _ ->
                DshModelsModelRow(
                    model = model,
                    enabled = writable() && !busy(),
                    onChange = { field, value -> onModelChange(index, field, value) },
                    onRemove = { onRemoveModel(index) },
                    colors = colors,
                )
            }
        }
        DshModelsPillButton(
            label = "添加模型",
            enabled = writable() && !busy(),
            dense = true,
            tone = DshModelsButtonTone.SECONDARY,
            marginTop = 8f,
            onClick = onAddModel,
            colors = colors,
        )

        vif({ error().isNotEmpty() }) {
            Text { attr { text(error()); marginTop(10f); fontSize(12f); lineHeight(18f); color(colors().stateErrorPrimary) } }
        }
        View {
            attr { marginTop(16f); flexDirectionRow(); justifyContentFlexEnd() }
            DshModelsPillButton(
                label = "取消",
                enabled = !busy(),
                dense = false,
                tone = DshModelsButtonTone.SECONDARY,
                marginRight = 8f,
                onClick = onCancel,
                colors = colors,
            )
            Button {
                attr {
                    height(36f)
                    paddingLeft(14f)
                    paddingRight(14f)
                    borderRadius(18f)
                    backgroundColor(colors().buttonPrimaryFill)
                    opacity(if (busy() || !writable()) 0.4f else 1f)
                    titleAttr {
                        text(if (busy()) "创建中…" else "创建提供方")
                        fontSize(14f)
                        color(colors().labelPrimaryForeground)
                    }
                }
                event { click { if (!busy() && writable()) onApply() } }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshModelsModelRow(
    model: DshProviderModel,
    enabled: Boolean,
    onChange: (String, String) -> Unit,
    onRemove: () -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f); borderRadius(10f); padding(6f)
            border(Border(0.5f, BorderStyle.SOLID, colors().borderL4)) }
        DshModelsInput(
            value = { model.id },
            placeholder = "模型 ID",
            enabled = enabled,
            password = false,
            marginTop = 0f,
            flex = 1.4f,
            onChange = { onChange("id", it) },
            colors = colors,
        )
        DshModelsInput(
            value = { model.name },
            placeholder = "显示名称",
            enabled = enabled,
            password = false,
            marginTop = 0f,
            flex = 1f,
            marginLeft = 8f,
            onChange = { onChange("name", it) },
            colors = colors,
        )
        View {
            attr { size(28f, 28f); allCenter(); marginLeft(4f); borderRadius(6f)
                opacity(if (enabled) 1f else 0.4f) }
            Image {
                attr {
                    src(ImageUri.commonAssets("delete.svg"))
                    size(14f, 14f)
                    tintColor(colors().labelTertiary)
                }
            }
            DshHitButton { if (enabled) onRemove() }
        }
    }
}

private fun ViewContainer<*, *>.DshModelsFieldLabel(
    text: String,
    colors: () -> DshColorTokens,
) {
    Text {
        attr {
            text(text)
            marginTop(14f)
            fontSize(12f)
            lineHeight(18f)
            fontWeightMedium()
            color(colors().labelSecondary)
        }
    }
}

private fun ViewContainer<*, *>.DshModelsInput(
    value: () -> String,
    placeholder: String,
    enabled: Boolean,
    password: Boolean,
    marginTop: Float,
    onChange: (String) -> Unit,
    colors: () -> DshColorTokens,
    flex: Float = 1f,
    marginLeft: Float = 0f,
) {
    View {
        attr {
            flex(flex)
            marginLeft(marginLeft)
            height(32f)
            marginTop(marginTop)
            borderRadius(8f)
            border(Border(0.5f, BorderStyle.SOLID, colors().borderL4))
            backgroundColor(colors().bgLayer1)
            paddingLeft(10f)
            paddingRight(10f)
            opacity(if (enabled) 1f else 0.6f)
        }
        Input {
            attr {
                flex(1f)
                text(value())
                fontSize(14f)
                color(colors().labelPrimary)
                placeholder(placeholder)
                placeholderColor(colors().labelDimmed)
                returnKeyTypeDone()
                editable(enabled)
                if (password) keyboardTypePassword()
            }
            event { textDidChange { if (enabled) onChange(it.text) } }
        }
    }
}
