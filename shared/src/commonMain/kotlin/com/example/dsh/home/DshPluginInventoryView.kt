package com.example.dsh.home

import com.example.dsh.conversation.DshPluginEntry
import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.*

internal fun pluginActionLabel(action: String): String = when (action) {
    "enable" -> "启用"
    "disable" -> "停用"
    "reload" -> "重载"
    else -> "操作"
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

private fun pluginPhaseColor(phase: String?, colors: DshColorTokens): Color = when (phase) {
    "active" -> colors.stateSuccessPrimary
    "failed" -> colors.stateErrorPrimary
    null -> colors.labelDimmed
    else -> colors.stateWarnPrimary
}

/**
 * 移动端“插件列表”页：对齐 dsh 原版右侧主界面的可搜索紧凑折叠卡片。
 * 每行显示名称、fiber 状态点、启停标签与开关；展开后展示脱敏配置与重载。
 * 电脑端官方只读列表通过独立的浏览器插件补齐同一套启停能力。
 */
internal fun ViewContainer<*, *>.DshPluginInventoryView(
    rows: () -> ObservableList<DshPluginEntry>,
    total: () -> Int,
    loading: () -> Boolean,
    error: () -> String,
    keyword: () -> String,
    expandedId: () -> String,
    busyId: () -> String,
    actionError: () -> String,
    actionNotice: () -> String,
    confirmEntry: () -> DshPluginEntry?,
    confirmAction: () -> String,
    onKeyword: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onToggleExpand: (DshPluginEntry) -> Unit,
    onToggleEnabled: (DshPluginEntry, Boolean) -> Unit,
    onReload: (DshPluginEntry) -> Unit,
    onConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
    colors: () -> DshColorTokens,
) {
    Modal(inWindow = true) {
        attr { absolutePositionAllZero(); backgroundColor(colors().bgBase)
            paddingTop(pagerData.statusBarHeight); paddingBottom(pagerData.safeAreaInsets.bottom) }
        View {
            attr { height(52f); flexDirectionRow(); alignItemsCenter(); paddingLeft(16f); paddingRight(16f) }
            Text { attr { text("返回"); fontSize(14f); color(colors().labelPrimary) }; event { click { onClose() } } }
            Text { attr { text("Host 插件"); flex(1f); textAlignCenter(); fontSize(18f); color(colors().labelPrimary) } }
            Text { attr { text(if (loading()) "读取中" else "刷新"); fontSize(14f); color(colors().stateBusinessPrimary) }
                event { click { if (!loading()) onRefresh() } } }
        }
        View {
            attr { height(40f); marginLeft(12f); marginRight(12f); marginTop(6f); backgroundColor(colors().bgLayer2); borderRadius(8f) }
            Input {
                attr { flex(1f); height(40f); marginLeft(10f); marginRight(10f); text(keyword()); fontSize(14f)
                    placeholder("搜索插件名称或 ID"); color(colors().labelPrimary); placeholderColor(colors().labelTertiary) }
                event { textDidChange { onKeyword(it.text) } }
            }
        }
        Text { attr { text("插件列表 · ${total()}"); margin(12f); fontSize(12f); color(colors().labelTertiary) } }
        vif({ actionNotice().isNotEmpty() }) {
            Text { attr { text(actionNotice()); marginLeft(16f); marginRight(16f); marginBottom(8f); fontSize(12f)
                color(colors().stateBusinessPrimary) } }
        }
        vif({ actionError().isNotEmpty() }) {
            Text { attr { text(actionError()); marginLeft(16f); marginRight(16f); marginBottom(8f); fontSize(12f)
                color(colors().stateErrorPrimary) } }
        }
        vif({ error().isNotEmpty() }) {
            Text { attr { text(error()); margin(12f); fontSize(14f); color(colors().stateErrorPrimary) } }
        }
        vif({ loading() }) {
            Text { attr { text("正在读取 Host 插件清单…"); margin(12f); fontSize(14f); color(colors().labelSecondary) } }
        }
        vif({ !loading() && error().isEmpty() && rows().isEmpty() }) {
            Text { attr { text(if (total() == 0) "Host 暂无插件" else "无匹配插件，请调整搜索")
                margin(20f); fontSize(14f); color(colors().labelSecondary) } }
        }
        List {
            attr { flex(1f) }
            vforLazy({ rows() }) { entry, _, _ ->
                PluginInventoryCard(
                    entry = entry,
                    expandedId = expandedId,
                    busyId = busyId,
                    onToggleExpand = { onToggleExpand(entry) },
                    onToggleEnabled = { desired -> onToggleEnabled(entry, desired) },
                    onReload = { onReload(entry) },
                    colors = colors,
                )
            }
        }
        // 二次确认：停用 / 重载
        vif({ confirmEntry() != null && confirmAction().isNotEmpty() }) {
            val current = confirmEntry()
            View {
                attr { absolutePositionAllZero(); backgroundColor(Color(0x99000000)); allCenter(); padding(24f) }
                View {
                    attr { width(pagerData.pageViewWidth - 48f); maxWidth(420f); backgroundColor(colors().bgLayer1)
                        borderRadius(14f); padding(18f); border(Border(1f, BorderStyle.SOLID, colors().borderL1)) }
                    Text { attr { text("确认${pluginActionLabel(confirmAction())}「${current?.name ?: ""}」？")
                        fontSize(16f); color(colors().labelPrimary) } }
                    Text { attr {
                        text(if (confirmAction() == "disable") "停用会立即卸载该插件；若被其他插件依赖，可能影响对话。"
                        else "重载会先停用再重新启动该插件。")
                        marginTop(10f); fontSize(13f); color(colors().labelSecondary) } }
                    vif({ busyId().isNotEmpty() }) {
                        Text { attr { text("处理中…"); marginTop(10f); fontSize(13f); color(colors().labelSecondary) } }
                    }
                    View {
                        attr { flexDirectionRow(); marginTop(16f); justifyContentFlexEnd() }
                        View {
                            attr { height(40f); paddingLeft(14f); paddingRight(14f); allCenter() }
                            Text { attr { text("取消"); fontSize(14f); color(colors().labelSecondary) } }
                            event { click { if (busyId().isEmpty()) onCancelConfirm() } }
                        }
                        View {
                            attr { height(40f); paddingLeft(14f); paddingRight(14f); allCenter(); marginLeft(8f) }
                            Text { attr { text(if (busyId().isNotEmpty()) "处理中" else "确认"); fontSize(14f)
                                color(colors().stateBusinessPrimary) } }
                            event { click { if (busyId().isEmpty()) onConfirm() } }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.PluginInventoryCard(
    entry: DshPluginEntry,
    expandedId: () -> String,
    busyId: () -> String,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onReload: () -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr { flexDirectionColumn(); marginLeft(12f); marginRight(12f); marginBottom(8f)
            backgroundColor(colors().bgLayer1); borderRadius(12f); border(Border(1f, BorderStyle.SOLID, colors().borderL1)) }
        View {
            attr { height(52f); flexDirectionRow(); alignItemsCenter(); paddingLeft(14f); paddingRight(10f) }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                event { click { onToggleExpand() } }
                Text { attr { text(entry.name); fontSize(15f); color(colors().labelPrimary)
                    lines(1); textOverFlowTail(); flex(1f) } }
                vif({ entry.enabled && entry.phase != null }) {
                    View { attr { size(8f, 8f); borderRadius(4f); marginLeft(8f)
                        backgroundColor(pluginPhaseColor(entry.phase, colors())) } }
                }
            }
            Text { attr { text(if (entry.enabled) "已启用" else "已停用"); fontSize(11f); marginLeft(8f)
                color(if (entry.enabled) colors().labelSecondary else colors().labelTertiary) } }
            PluginToggle(
                enabled = entry.enabled,
                interactive = { entry.canToggle && busyId() != entry.id },
                colors = colors,
                onToggle = onToggleEnabled,
            )
            Text { attr { text(if (expandedId() == entry.id) "▾" else "▸"); fontSize(11f); marginLeft(6f)
                color(colors().labelTertiary) }
                event { click { onToggleExpand() } } }
        }
        vif({ expandedId() == entry.id }) {
            View {
                attr { flexDirectionColumn(); paddingLeft(14f); paddingRight(14f); paddingBottom(12f) }
                PluginDetailRow("ID", entry.id, colors().labelSecondary, colors)
                if (entry.enabled) {
                    PluginDetailRow("Cordis 状态", pluginPhaseLabel(entry.phase), colors().labelSecondary, colors)
                }
                if (entry.failureSummary != null) {
                    PluginDetailRow("失败原因", entry.failureSummary, colors().stateErrorPrimary, colors)
                }
                if (entry.disabledExpr != null) {
                    PluginDetailRow("禁用表达式", entry.disabledExpr, colors().labelSecondary, colors)
                }
                if (!entry.canToggle && entry.toggleHint.isNotEmpty()) {
                    PluginDetailRow("不可启停", entry.toggleHint, colors().stateWarnLabel, colors)
                }
                if (entry.configDetail.isNotEmpty() && entry.configDetail != "null") {
                    PluginDetailBlock("脱敏配置", entry.configDetail, colors)
                }
                if (entry.injectDetail.isNotEmpty() && entry.injectDetail != "null") {
                    PluginDetailBlock("Injects", entry.injectDetail, colors)
                }
                vif({ entry.enabled && entry.canToggle && busyId() != entry.id }) {
                    View {
                        attr { flexDirectionRow(); justifyContentFlexEnd(); marginTop(10f) }
                        View {
                            attr { height(34f); paddingLeft(12f); paddingRight(12f); allCenter(); borderRadius(8f)
                                backgroundColor(colors().stateBusinessTertiary) }
                            Text { attr { text("重载"); fontSize(13f); color(colors().stateBusinessPrimary) } }
                            event { click { onReload() } }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.PluginToggle(
    enabled: Boolean,
    interactive: () -> Boolean,
    colors: () -> DshColorTokens,
    onToggle: (Boolean) -> Unit,
) {
    View {
        attr { size(44f, 24f); borderRadius(12f)
            backgroundColor(if (enabled) colors().stateBusinessPrimary else colors().borderL4)
            opacity(if (interactive()) 1f else 0.4f) }
        View {
            attr { positionAbsolute(); top(2f); left(if (enabled) 22f else 2f); size(20f, 20f)
                borderRadius(10f); backgroundColor(Color(0xFFFFFFFF)) }
        }
        event { click { if (interactive()) onToggle(!enabled) } }
    }
}

private fun ViewContainer<*, *>.PluginDetailRow(
    label: String,
    value: String,
    valueColor: Color,
    colors: () -> DshColorTokens,
) {
    View {
        attr { flexDirectionRow(); marginTop(8f) }
        Text { attr { text(label); width(84f); fontSize(12f); color(colors().labelTertiary) } }
        Text { attr { text(value); flex(1f); fontSize(12f); color(valueColor) } }
    }
}

private fun ViewContainer<*, *>.PluginDetailBlock(
    label: String,
    value: String,
    colors: () -> DshColorTokens,
) {
    Text { attr { text(label); marginTop(10f); fontSize(12f); color(colors().labelTertiary) } }
    Text { attr { text(value); marginTop(4f); fontSize(11f); fontFamily("monospace"); color(colors().labelSecondary) } }
}
