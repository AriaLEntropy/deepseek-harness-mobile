package com.example.dsh.home

import com.example.dsh.conversation.DshPluginEntry
import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.*

internal fun pluginActionLabel(action: String): String = when (action) {
    "enable" -> "启用"
    "disable" -> "停用"
    "reload" -> "重载"
    else -> "操作"
}

internal fun ViewContainer<*, *>.DshPluginInventoryView(
    rows: () -> ObservableList<DshPluginEntry>,
    total: () -> Int,
    loading: () -> Boolean,
    error: () -> String,
    keyword: () -> String,
    phase: () -> String,
    detail: () -> DshPluginEntry?,
    confirmAction: () -> String,
    actionBusy: () -> Boolean,
    actionError: () -> String,
    actionNotice: () -> String,
    onKeyword: (String) -> Unit,
    onPhase: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onOpenDetail: (DshPluginEntry) -> Unit,
    onCloseDetail: () -> Unit,
    onRequestAction: (DshPluginEntry, String) -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit,
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
        Text { attr { text("点击插件查看详情；对照 Host 当前加载状态")
            margin(12f); fontSize(12f); color(colors().labelSecondary) } }
        View {
            attr { height(40f); marginLeft(12f); marginRight(12f); backgroundColor(colors().bgLayer2); borderRadius(8f) }
            Input {
                attr { flex(1f); height(40f); marginLeft(10f); marginRight(10f); text(keyword()); fontSize(14f)
                    placeholder("搜索插件名称或 ID"); color(colors().labelPrimary); placeholderColor(colors().labelTertiary) }
                event { textDidChange { onKeyword(it.text) } }
            }
        }
        View {
            attr { flexDirectionRow(); flexWrap(FlexWrap.WRAP); margin(12f) }
            listOf("" to "全部", "pending" to "pending", "loading" to "loading", "active" to "active",
                "failed" to "failed", "unloading" to "unloading", "none" to "无实例").forEach { (value, label) ->
                View {
                    attr { padding(8f); marginRight(6f); marginBottom(6f); borderRadius(8f)
                        backgroundColor(if (phase() == value) colors().specificSelector else colors().bgLayer2) }
                    Text { attr { text(label); fontSize(12f); color(if (phase() == value) colors().stateBusinessPrimary else colors().labelSecondary) } }
                    event { click { onPhase(value) } }
                }
            }
        }
        vif({ error().isNotEmpty() }) {
            Text { attr { text(error()); margin(12f); fontSize(14f); color(colors().stateErrorPrimary) } }
        }
        vif({ loading() }) {
            Text { attr { text("正在读取 Host 插件清单…"); margin(12f); fontSize(14f); color(colors().labelSecondary) } }
        }
        vif({ !loading() && error().isEmpty() && rows().isEmpty() }) {
            Text { attr { text(if (total() == 0) "Host 暂无插件" else "无匹配插件，请调整搜索或状态筛选")
                margin(20f); fontSize(14f); color(colors().labelSecondary) } }
        }
        List {
            attr { flex(1f) }
            vforLazy({ rows() }) { entry, _, _ ->
                View {
                    attr { padding(14f); marginBottom(8f); backgroundColor(colors().bgLayer1) }
                    Text { attr { text(entry.name); fontSize(16f); color(colors().labelPrimary) } }
                    Text { attr { text("${if (entry.enabled) "已启用" else "未启用"} · ${entry.phase ?: "无运行实例"}")
                        marginTop(6f); fontSize(13f); color(if (entry.phase == "failed") colors().stateErrorPrimary else colors().labelSecondary) } }
                    Text { attr { text("ID：${entry.id}"); marginTop(4f); fontSize(11f); color(colors().labelTertiary) } }
                    Text { attr { text("配置摘要：${entry.configSummary}"); marginTop(8f); fontSize(12f); color(colors().labelSecondary) } }
                    if (entry.failureSummary != null) {
                        Text { attr { text("失败原因：${entry.failureSummary}"); marginTop(8f); fontSize(13f); color(colors().stateErrorPrimary) } }
                    }
                    event { click { onOpenDetail(entry) } }
                }
            }
        }
        // 详情浮层：完整脱敏配置 + 启停/重载操作
        vif({ detail() != null }) {
            val current = detail()
            View {
                attr { absolutePositionAllZero(); backgroundColor(Color(0x99000000)) }
                View {
                    attr {
                        positionAbsolute(); left(0f); right(0f); bottom(0f)
                        backgroundColor(colors().bgLayer1); borderRadius(16f)
                        maxHeight(pagerData.pageViewHeight * 0.82f)
                        padding(16f)
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter() }
                        Text { attr { text(current?.name ?: ""); flex(1f); fontSize(17f); color(colors().labelPrimary) } }
                        Text { attr { text("关闭"); fontSize(14f); color(colors().labelSecondary) } }
                            event { click { onCloseDetail() } }
                    }
                    Text { attr {
                        text("${if (current?.enabled == true) "已启用" else "未启用"} · ${current?.phase ?: "无运行实例"}")
                        marginTop(6f); fontSize(13f); color(if (current?.phase == "failed") colors().stateErrorPrimary else colors().labelSecondary)
                    } }
                    Text { attr { text("ID：${current?.id ?: ""}"); marginTop(4f); fontSize(11f); color(colors().labelTertiary) } }
                    Scroller {
                        attr { height(pagerData.pageViewHeight * 0.42f); marginTop(12f) }
                        View {
                            attr { flexDirectionColumn() }
                            if (current?.failureSummary != null) {
                                Text { attr { text("失败原因\n${current.failureSummary}"); fontSize(13f); color(colors().stateErrorPrimary) } }
                            }
                            if (current?.disabledExpr != null) {
                                Text { attr { text("禁用表达式：${current.disabledExpr}"); marginTop(8f); fontSize(12f); color(colors().labelSecondary) } }
                            }
                            if (current != null && !current.canToggle && current.toggleHint.isNotEmpty()) {
                                Text { attr { text("不可启停：${current.toggleHint}"); marginTop(8f); fontSize(12f); color(colors().stateWarnPrimary) } }
                            }
                            if (!current?.injectDetail.isNullOrEmpty() && current?.injectDetail != "null") {
                                Text { attr { text("Injects\n${current?.injectDetail}"); marginTop(10f); fontSize(12f); fontFamily("monospace"); color(colors().labelSecondary) } }
                            }
                            Text { attr { text("配置详情\n${current?.configDetail ?: ""}"); marginTop(10f); fontSize(12f); fontFamily("monospace"); color(colors().labelSecondary) } }
                        }
                    }
                    vif({ actionNotice().isNotEmpty() }) {
                        Text { attr { text(actionNotice()); marginTop(10f); fontSize(13f); color(colors().stateBusinessPrimary) } }
                    }
                    vif({ actionError().isNotEmpty() }) {
                        Text { attr { text(actionError()); marginTop(10f); fontSize(13f); color(colors().stateErrorPrimary) } }
                    }
                    vif({ current?.canToggle == true }) {
                        View {
                            attr { flexDirectionRow(); marginTop(14f); justifyContentFlexEnd() }
                            if (current?.enabled == true) {
                                PluginActionButton("重载", "reload", current, actionBusy, onRequestAction, colors)
                                PluginActionButton("停用", "disable", current, actionBusy, onRequestAction, colors)
                            } else {
                                PluginActionButton("启用", "enable", current, actionBusy, onRequestAction, colors)
                            }
                        }
                    }
                }
            }
        }
        // 二次确认
        vif({ detail() != null && confirmAction().isNotEmpty() }) {
            val current = detail()
            View {
                attr { absolutePositionAllZero(); backgroundColor(Color(0x99000000)); allCenter(); padding(24f) }
                View {
                    attr { width(pagerData.pageViewWidth - 48f); maxWidth(420f); backgroundColor(colors().bgLayer1); borderRadius(14f); padding(18f) }
                    Text { attr { text("确认${pluginActionLabel(confirmAction())}「${current?.name ?: ""}」？")
                        fontSize(16f); color(colors().labelPrimary) } }
                    Text { attr {
                        text(if (confirmAction() == "disable") "停用会立即卸载该插件；若被其他插件依赖，可能影响对话。"
                        else "重载会先停用再重新启动该插件。")
                        marginTop(10f); fontSize(13f); color(colors().labelSecondary) } }
                    vif({ actionBusy() }) {
                        Text { attr { text("处理中…"); marginTop(10f); fontSize(13f); color(colors().labelSecondary) } }
                    }
                    vif({ actionError().isNotEmpty() }) {
                        Text { attr { text(actionError()); marginTop(10f); fontSize(13f); color(colors().stateErrorPrimary) } }
                    }
                    View {
                        attr { flexDirectionRow(); marginTop(16f); justifyContentFlexEnd() }
                        View {
                            attr { height(40f); paddingLeft(14f); paddingRight(14f); allCenter() }
                            Text { attr { text("取消"); fontSize(14f); color(colors().labelSecondary) } }
                            event { click { if (!actionBusy()) onCancelAction() } }
                        }
                        View {
                            attr { height(40f); paddingLeft(14f); paddingRight(14f); allCenter(); marginLeft(8f) }
                            Text { attr { text(if (actionBusy()) "处理中" else "确认"); fontSize(14f); color(colors().stateBusinessPrimary) } }
                            event { click { if (!actionBusy()) onConfirmAction() } }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.PluginActionButton(
    label: String,
    action: String,
    entry: DshPluginEntry?,
    busy: () -> Boolean,
    onRequestAction: (DshPluginEntry, String) -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr { height(38f); paddingLeft(14f); paddingRight(14f); allCenter(); marginLeft(8f)
            borderRadius(8f); backgroundColor(colors().stateBusinessTertiary) }
        Text { attr { text(label); fontSize(14f); color(colors().stateBusinessPrimary) } }
        event { click { if (!busy() && entry != null) onRequestAction(entry, action) } }
    }
}
