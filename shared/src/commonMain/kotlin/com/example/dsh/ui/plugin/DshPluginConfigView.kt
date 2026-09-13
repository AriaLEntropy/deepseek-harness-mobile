package com.example.dsh.ui.plugin

import com.example.dsh.plugin.DshPluginConfigCard
import com.example.dsh.plugin.DshPluginConfigField
import com.example.dsh.plugin.DshPluginFieldKind
import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.*

/**
 * 移动端「插件配置」标签页：对齐官方 PluginCard / fields 的卡片、分隔线与控件质感。
 * 卡片用 vforLazy 渲染，保证异步加载完成后响应式补上。字段写回 settings.mutate，密钥写回 credentials.set。
 */
internal fun ViewContainer<*, *>.DshPluginConfigView(
    cards: () -> ObservableList<DshPluginConfigCard>,
    loading: () -> Boolean,
    error: () -> String,
    writable: () -> Boolean,
    draft: (String, String) -> String,
    secretDraft: (String) -> String,
    collapsed: (String) -> Boolean,
    busyNamespace: () -> String,
    cardError: (String) -> String,
    cardNotice: (String) -> String,
    hasChanges: (String) -> Boolean,
    onDraft: (String, String, String) -> Unit,
    onSecretDraft: (String, String) -> Unit,
    onToggleCollapse: (String) -> Unit,
    onSave: (String) -> Unit,
    onDiscard: (String) -> Unit,
    colors: () -> DshColorTokens,
) {
    vif({ loading() }) {
        Text { attr { text("正在读取插件配置…"); margin(16f); fontSize(13f); color(colors().labelTertiary) } }
    }
    vif({ error().isNotEmpty() }) {
        Text { attr { text(error()); margin(16f); fontSize(13f); color(colors().stateErrorPrimary) } }
    }
    vif({ !writable() && !loading() && error().isEmpty() }) {
        Text { attr { text("本部署的设置为只读。"); margin(16f); fontSize(12f); color(colors().labelTertiary) } }
    }
    vif({ !loading() && error().isEmpty() && cards().isEmpty() }) {
        Text { attr { text("本部署没有开放任何插件设置。"); margin(16f); fontSize(13f); color(colors().labelTertiary) } }
    }
    List {
        attr { flex(1f); width(pagerData.pageViewWidth) }
        vforLazy({ cards() }) { card, _, _ ->
            PluginConfigCardView(
                card = card,
                draft = draft,
                secretDraft = secretDraft,
                collapsed = collapsed,
                busyNamespace = busyNamespace,
                cardError = cardError,
                cardNotice = cardNotice,
                hasChanges = hasChanges,
                onDraft = onDraft,
                onSecretDraft = onSecretDraft,
                onToggleCollapse = onToggleCollapse,
                onSave = onSave,
                onDiscard = onDiscard,
                writable = writable,
                colors = colors,
            )
        }
    }
}

private fun ViewContainer<*, *>.PluginConfigCardView(
    card: DshPluginConfigCard,
    draft: (String, String) -> String,
    secretDraft: (String) -> String,
    collapsed: (String) -> Boolean,
    busyNamespace: () -> String,
    cardError: (String) -> String,
    cardNotice: (String) -> String,
    hasChanges: (String) -> Boolean,
    onDraft: (String, String, String) -> Unit,
    onSecretDraft: (String, String) -> Unit,
    onToggleCollapse: (String) -> Unit,
    onSave: (String) -> Unit,
    onDiscard: (String) -> Unit,
    writable: () -> Boolean,
    colors: () -> DshColorTokens,
) {
    val namespace = card.namespace
    View {
        attr {
            flexDirectionColumn()
            marginLeft(12f)
            marginRight(12f)
            marginBottom(10f)
            backgroundColor(colors().bgLayer3)
            borderRadius(12f)
            border(Border(1f, BorderStyle.SOLID, colors().borderL2))
            overflow(false)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter(); paddingLeft(16f); paddingRight(16f); paddingTop(14f); paddingBottom(14f) }
            event { click { onToggleCollapse(namespace) } }
            View {
                attr { flex(1f); flexDirectionColumn() }
                Text { attr { text(card.title); fontSize(15f); fontWeightBold(); color(colors().labelPrimary) } }
                Text { attr { text(card.description); marginTop(4f); fontSize(13f); color(colors().labelTertiary) } }
            }
            Text { attr { text(if (collapsed(namespace)) "▸" else "▾"); fontSize(11f); color(colors().labelTertiary) } }
        }
        vif({ !collapsed(namespace) }) {
            View { attr { height(1f); marginLeft(16f); marginRight(16f); backgroundColor(colors().borderL2) } }
            View {
                attr { flexDirectionColumn(); paddingLeft(16f); paddingRight(16f); paddingBottom(14f) }
                for (field in card.fields) {
                    PluginConfigFieldRow(
                        namespace = namespace,
                        field = field,
                        secretSet = card.secretSet,
                        draft = draft,
                        secretDraft = secretDraft,
                        onDraft = onDraft,
                        onSecretDraft = onSecretDraft,
                        colors = colors,
                    )
                }
                vif({ cardNotice(namespace).isNotEmpty() }) {
                    Text { attr { text(cardNotice(namespace)); marginTop(10f); fontSize(12f)
                        color(colors().stateSuccessPrimary) } }
                }
                vif({ cardError(namespace).isNotEmpty() }) {
                    Text { attr { text(cardError(namespace)); marginTop(10f); fontSize(12f)
                        color(colors().stateErrorPrimary) } }
                }
                View { attr { height(1f); marginTop(12f); backgroundColor(colors().borderL2) } }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); justifyContentFlexEnd(); marginTop(12f) }
                    vif({ hasChanges(namespace) && busyNamespace() != namespace }) {
                        View {
                            attr { height(32f); paddingLeft(14f); paddingRight(14f); allCenter(); borderRadius(8f)
                                border(Border(1f, BorderStyle.SOLID, colors().borderL2)) }
                            Text { attr { text("放弃修改"); fontSize(13f); color(colors().labelSecondary) } }
                            event { click { onDiscard(namespace) } }
                        }
                    }
                    val busy = busyNamespace() == namespace
                    val disabled = !writable() || !hasChanges(namespace) || busy
                    View {
                        attr { height(32f); paddingLeft(14f); paddingRight(14f); allCenter(); borderRadius(8f); marginLeft(8f)
                            backgroundColor(if (disabled) colors().buttonPrimaryDimmed else colors().labelPrimary) }
                        Text {
                            attr {
                                text(if (busy) "保存中…" else "保存")
                                fontSize(13f)
                                color(colors().labelPrimaryInverted)
                            }
                            event { click { if (!disabled) onSave(namespace) } }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.PluginConfigFieldRow(
    namespace: String,
    field: DshPluginConfigField,
    secretSet: Boolean,
    draft: (String, String) -> String,
    secretDraft: (String) -> String,
    onDraft: (String, String, String) -> Unit,
    onSecretDraft: (String, String) -> Unit,
    colors: () -> DshColorTokens,
) {
    val isSecret = field.kind == DshPluginFieldKind.SECRET
    val placeholder = when {
        !isSecret -> ""
        secretSet -> "已配置密钥，留空保持不变"
        else -> "未配置密钥，输入以设置"
    }
    View {
        attr { flexDirectionColumn(); paddingTop(12f); paddingBottom(12f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text { attr { text(field.label); flex(1f); fontSize(13f); fontWeightMedium(); color(colors().labelPrimary) } }
            if (isSecret) {
                View {
                    attr { borderRadius(999f); paddingLeft(8f); paddingRight(8f); paddingTop(1f); paddingBottom(1f)
                        backgroundColor(colors().bgModulePlatform) }
                    Text { attr { text(if (secretSet) "已配置密钥" else "未配置密钥"); fontSize(11f)
                        color(colors().labelSecondary) } }
                }
            }
        }
        View {
            attr { height(34f); marginTop(6f); backgroundColor(colors().bgLayer3); borderRadius(8f)
                border(Border(1f, BorderStyle.SOLID, colors().borderL2)) }
            Input {
                attr { flex(1f); height(34f); marginLeft(10f); marginRight(10f); fontSize(13f)
                    text(if (isSecret) secretDraft(namespace) else draft(namespace, field.key))
                    placeholder(placeholder); color(colors().labelPrimary); placeholderColor(colors().labelDimmed) }
                event { textDidChange {
                    if (isSecret) onSecretDraft(namespace, it.text) else onDraft(namespace, field.key, it.text)
                } }
            }
        }
        Text { attr { text(field.hint); marginTop(6f); fontSize(12f); color(colors().labelTertiary) } }
    }
}
