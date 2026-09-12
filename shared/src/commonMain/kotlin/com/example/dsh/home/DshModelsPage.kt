package com.example.dsh.home

import com.example.dsh.theme.DshColorTokens

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
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

/**
 * 设置页「模型」详情页：对齐电脑端 DSH settings.models 的右侧主内容区。
 * 移动端只保留内容列（桌面端左右分栏在移动端折叠为单列），
 * 交互为：提供方列表 → 展开编辑卡片 → 保存写回电脑端。
 */
internal fun ViewContainer<*, *>.DshModelsPage(
    loading: () -> Boolean,
    error: () -> String,
    writable: () -> Boolean,
    providers: () -> ObservableList<DshProviderConfig>,
    editingProvider: () -> String,
    draftBaseUrl: () -> String,
    draftApiKey: () -> String,
    draftModels: () -> ObservableList<DshProviderModel>,
    saving: () -> Boolean,
    saveError: () -> String,
    deleteTarget: () -> DshProviderConfig?,
    deleting: () -> Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (DshProviderConfig) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (Int, String, String) -> Unit,
    onAddModel: () -> Unit,
    onRemoveModel: (Int) -> Unit,
    onApply: (DshProviderConfig) -> Unit,
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
        // 顶部返回栏
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
                        text("模型")
                        fontSize(16f)
                        fontWeightMedium()
                        color(colors().labelPrimary)
                    }
                }
                Text {
                    attr {
                        text("填入各提供方的 API 密钥即可使用其模型。")
                        marginTop(6f)
                        fontSize(14f)
                        lineHeight(22f)
                        color(colors().labelTertiary)
                    }
                }
                vif({ !loading() && error().isNotEmpty() }) {
                    View {
                        attr {
                            marginTop(12f)
                            padding(12f)
                            borderRadius(10f)
                            flexDirectionColumn()
                            backgroundColor(colors().bgLayer1)
                            border(Border(1f, BorderStyle.SOLID, colors().borderL2))
                        }
                        Text {
                            attr {
                                text("加载提供方目录失败：${error()}")
                                fontSize(13f)
                                lineHeight(20f)
                                color(colors().stateErrorPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("重试")
                                marginTop(8f)
                                fontSize(13f)
                                fontWeightMedium()
                                color(colors().stateBusinessPrimary)
                            }
                            event { click { onRetry() } }
                        }
                    }
                }
                vif({ !loading() && error().isEmpty() && !writable() }) {
                    Text {
                        attr {
                            text("电脑端设置当前为只读，仅可查看。")
                            marginTop(10f)
                            fontSize(12f)
                            color(colors().labelTertiary)
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
                        vforIndex({ providers() }) { provider, _, _ ->
                            DshModelsProviderCard(
                                provider = provider,
                                editing = editingProvider() == provider.provider,
                                writable = writable(),
                                saving = saving() && editingProvider() == provider.provider,
                                draftBaseUrl = draftBaseUrl,
                                draftApiKey = draftApiKey,
                                draftModels = draftModels,
                                saveError = { if (editingProvider() == provider.provider) saveError() else "" },
                                onEdit = { onEdit(provider) },
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
                    }
                }
            }
        }
    }

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
                    maxWidth(420f)
                    flexDirectionColumn()
                    padding(20f)
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer3)
                }
                Text {
                    attr {
                        text("删除 ${deleteTarget()?.displayName.orEmpty()}？")
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                Text {
                    attr {
                        text("删除会移除该提供方的配置；其使用的密钥（如由本页管理）也会一并清除。")
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(colors().labelSecondary)
                    }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr {
                            text("取消")
                            width(78f)
                            height(38f)
                            textAlignCenter()
                            fontSize(14f)
                            color(colors().labelTertiary)
                        }
                        event { click { if (!deleting()) onCancelDelete() } }
                    }
                    Text {
                        attr {
                            text(if (deleting()) "删除中…" else "删除")
                            width(88f)
                            height(38f)
                            marginLeft(8f)
                            textAlignCenter()
                            fontSize(14f)
                            color(colors().stateErrorPrimary)
                        }
                        event { click { if (!deleting()) onConfirmDelete() } }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshModelsProviderCard(
    provider: DshProviderConfig,
    editing: Boolean,
    writable: Boolean,
    saving: Boolean,
    draftBaseUrl: () -> String,
    draftApiKey: () -> String,
    draftModels: () -> ObservableList<DshProviderModel>,
    saveError: () -> String,
    onEdit: () -> Unit,
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
            borderRadius(12f)
            backgroundColor(colors().bgLayer1)
            border(Border(1f, BorderStyle.SOLID, colors().borderL2))
        }
        // 提供方行头：名称 + 密钥状态点 + 操作
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            val credential = provider.credential
            if (credential != null) {
                View {
                    attr {
                        size(8f, 8f)
                        marginRight(8f)
                        borderRadius(4f)
                        backgroundColor(if (credential.configured) colors().stateSuccessPrimary else colors().stateErrorPrimary)
                    }
                }
            }
            Text {
                attr {
                    text(provider.displayName)
                    flex(1f)
                    fontSize(14f)
                    fontWeightMedium()
                    color(colors().labelPrimary)
                }
            }
            vif({ !editing }) {
                View {
                    attr {
                        paddingLeft(10f)
                        paddingRight(10f)
                        paddingTop(6f)
                        paddingBottom(6f)
                    }
                    Text {
                        attr {
                            text("编辑")
                            fontSize(13f)
                            color(colors().stateBusinessPrimary)
                        }
                    }
                    event { click { onEdit() } }
                }
                vif({ provider.removable }) {
                    View {
                        attr {
                            paddingLeft(6f)
                            paddingRight(4f)
                            paddingTop(6f)
                            paddingBottom(6f)
                        }
                        Text {
                            attr {
                                text("删除")
                                fontSize(13f)
                                color(colors().stateErrorPrimary)
                            }
                        }
                        event { click { onRequestDelete() } }
                    }
                }
            }
        }
        vif({ editing }) {
            View {
                attr { flexDirectionColumn(); marginTop(14f) }

                // API 密钥
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
                        Text {
                            attr {
                                text("由启动环境提供（只读）")
                                fontSize(13f)
                                color(colors().labelTertiary)
                            }
                        }
                    }
                } else {
                    DshModelsInput(
                        value = draftApiKey,
                        placeholder = when {
                            credential?.configured == true -> "已配置——输入新值可替换"
                            provider.apiKeyEnv.isEmpty() -> "输入 API 密钥，或留空使用环境认证"
                            else -> "输入 API 密钥"
                        },
                        enabled = writable,
                        password = true,
                        marginTop = 8f,
                        onChange = onApiKeyChange,
                        colors = colors,
                    )
                }

                // API 地址
                DshModelsFieldLabel("API 地址", colors)
                DshModelsInput(
                    value = draftBaseUrl,
                    placeholder = "提供方默认",
                    enabled = writable,
                    password = false,
                    marginTop = 8f,
                    onChange = onBaseUrlChange,
                    colors = colors,
                )

                // 模型目录
                DshModelsFieldLabel("模型目录", colors)
                Text {
                    attr {
                        text(if (provider.modelsOverridden) "已自定义模型目录" else "正在使用适配器默认模型")
                        marginTop(4f)
                        fontSize(11f)
                        color(colors().labelTertiary)
                    }
                }
                View {
                    attr { flexDirectionColumn(); marginTop(6f) }
                    vforIndex({ draftModels() }) { model, index, _ ->
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); marginTop(6f) }
                            DshModelsInput(
                                value = { model.id },
                                placeholder = "模型 ID",
                                enabled = writable,
                                password = false,
                                marginTop = 0f,
                                flex = 1.2f,
                                onChange = { onModelChange(index, "id", it) },
                                colors = colors,
                            )
                            DshModelsInput(
                                value = { model.name },
                                placeholder = "显示名称",
                                enabled = writable,
                                password = false,
                                marginTop = 0f,
                                flex = 1f,
                                marginLeft = 8f,
                                onChange = { onModelChange(index, "name", it) },
                                colors = colors,
                            )
                            View {
                                attr { size(32f, 32f); allCenter(); marginLeft(4f) }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("delete.svg"))
                                        size(16f, 16f)
                                        tintColor(colors().labelTertiary)
                                    }
                                }
                                DshHitButton { onRemoveModel(index) }
                            }
                        }
                    }
                }
                Text {
                    attr {
                        text("+ 添加模型")
                        marginTop(8f)
                        fontSize(13f)
                        color(colors().stateBusinessPrimary)
                    }
                    event { click { onAddModel() } }
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
                    attr { height(40f); marginTop(16f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr {
                            text("收起")
                            width(72f)
                            height(38f)
                            textAlignCenter()
                            fontSize(14f)
                            color(colors().labelTertiary)
                        }
                        event { click { onEdit() } }
                    }
                    Button {
                        attr {
                            height(38f)
                            marginLeft(8f)
                            paddingLeft(18f)
                            paddingRight(18f)
                            borderRadius(8f)
                            backgroundColor(if (saving || !writable) colors().stateBusinessTertiary else colors().stateBusinessPrimary)
                            titleAttr {
                                text(if (saving) "保存中…" else "保存")
                                fontSize(14f)
                                color(Color.WHITE)
                            }
                        }
                        event { click { if (!saving && writable) onApply() } }
                    }
                }
            }
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
            height(44f)
            marginTop(marginTop)
            borderRadius(8f)
            border(Border(1f, BorderStyle.SOLID, colors().borderL2))
            backgroundColor(colors().bgBase)
            paddingLeft(10f)
            paddingRight(10f)
        }
        Input {
            attr {
                flex(1f)
                text(value())
                fontSize(14f)
                color(colors().labelPrimary)
                placeholder(placeholder)
                placeholderColor(colors().labelTertiary)
                returnKeyTypeDone()
                editable(enabled)
                if (password) keyboardTypePassword()
            }
            event { textDidChange { if (enabled) onChange(it.text) } }
        }
    }
}
