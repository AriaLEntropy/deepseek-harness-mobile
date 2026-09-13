package com.example.dsh.ui.plugin

import com.example.dsh.plugin.DshPluginConfigCard
import com.example.dsh.plugin.DshPluginEntry
import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.*
import com.example.dsh.ui.home.DshHitButton

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
    null -> colors.labelTertiary
    else -> colors.stateBusinessPrimary
}

private const val PLUGIN_TAB_CONFIG = "config"
private const val PLUGIN_TAB_LIST = "list"
private const val PLUGIN_TAB_SWITCH = "switch"

/**
 * 移动端「插件」设置页，对齐 dsh 原版右侧主界面：标题 + 说明 + 下划线标签
 * （插件配置 / 插件列表 / 插件启停）。插件列表复用官方库存卡质感，
 * 插件启停在其上提供开关；电脑端受官方只读限制，开关放在并列标签页。
 */
internal fun ViewContainer<*, *>.DshPluginSettingsView(
    activeTab: () -> String,
    onSelectTab: (String) -> Unit,
    loading: () -> Boolean,
    error: () -> String,
    keyword: () -> String,
    onKeyword: (String) -> Unit,
    hasKeyword: () -> Boolean,
    onClearKeyword: () -> Unit,
    onSearchInputRef: (ViewRef<InputView>) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    rows: () -> ObservableList<DshPluginEntry>,
    total: () -> Int,
    expandedId: () -> String,
    busyId: () -> String,
    onToggleExpand: (DshPluginEntry) -> Unit,
    actionError: () -> String,
    actionNotice: () -> String,
    onToggleEnabled: (DshPluginEntry, Boolean) -> Unit,
    onReload: (DshPluginEntry) -> Unit,
    confirmEntry: () -> DshPluginEntry?,
    confirmAction: () -> String,
    onConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
    configCards: () -> ObservableList<DshPluginConfigCard>,
    configLoading: () -> Boolean,
    configError: () -> String,
    configWritable: () -> Boolean,
    configDraft: (String, String) -> String,
    configSecretDraft: (String) -> String,
    configCollapsed: (String) -> Boolean,
    configBusyNamespace: () -> String,
    configCardError: (String) -> String,
    configCardNotice: (String) -> String,
    configHasChanges: (String) -> Boolean,
    onConfigDraft: (String, String, String) -> Unit,
    onConfigSecretDraft: (String, String) -> Unit,
    onConfigToggleCollapse: (String) -> Unit,
    onConfigSave: (String) -> Unit,
    onConfigDiscard: (String) -> Unit,
    colors: () -> DshColorTokens,
) {
    Modal(inWindow = true) {
        attr { absolutePositionAllZero(); backgroundColor(colors().bgLayer2)
            paddingTop(pagerData.statusBarHeight); paddingBottom(pagerData.safeAreaInsets.bottom) }
        View {
            attr { height(52f); flexDirectionRow(); alignItemsCenter(); paddingLeft(16f); paddingRight(16f) }
            View {
                attr { width(56f); height(44f); flexDirectionRow(); alignItemsCenter() }
                event { click { onClose() } }
                Image {
                    attr { src(ImageUri.commonAssets("chevron-left.svg")); size(18f, 18f)
                        tintColor(colors().labelPrimary) }
                }
            }
            Text { attr { text("插件"); flex(1f); textAlignCenter(); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
            View {
                attr { width(56f); height(44f); flexDirectionRow(); alignItemsCenter(); justifyContentFlexEnd() }
                vif({ activeTab() != PLUGIN_TAB_CONFIG }) {
                    Image {
                        attr { src(ImageUri.commonAssets("icon-refresh16.svg")); size(18f, 18f)
                            tintColor(colors().labelSecondary); opacity(if (loading()) 0.4f else 1f) }
                        event { click { if (!loading()) onRefresh() } }
                    }
                }
            }
        }
        Text { attr { text("配置和查看本部署已安装的插件。"); marginLeft(16f); marginRight(16f); fontSize(13f)
            color(colors().labelTertiary) } }
        // 下划线标签栏
        View {
            attr { flexDirectionRow(); marginLeft(16f); marginRight(16f); marginTop(6f) }
            listOf(
                PLUGIN_TAB_CONFIG to "插件配置",
                PLUGIN_TAB_LIST to "插件列表",
                PLUGIN_TAB_SWITCH to "插件启停",
            ).forEach { (value, label) ->
                View {
                    attr { height(46f); flexDirectionColumn(); alignItemsCenter(); justifyContentCenter(); marginRight(22f) }
                    event { click { onSelectTab(value) } }
                    Text { attr { text(label); fontSize(13f)
                        color(if (activeTab() == value) colors().labelPrimary else colors().labelTertiary) } }
                    View { attr { height(2f); marginTop(7f); width(34f); borderRadius(2f)
                        backgroundColor(if (activeTab() == value) colors().labelPrimary else Color(0x00000000)) } }
                }
            }
        }
        View { attr { height(1f); backgroundColor(colors().borderL2) } }

        vif({ activeTab() == PLUGIN_TAB_CONFIG }) {
            DshPluginConfigView(
                cards = configCards, loading = configLoading, error = configError, writable = configWritable,
                draft = configDraft, secretDraft = configSecretDraft, collapsed = configCollapsed,
                busyNamespace = configBusyNamespace, cardError = configCardError, cardNotice = configCardNotice,
                hasChanges = configHasChanges, onDraft = onConfigDraft, onSecretDraft = onConfigSecretDraft,
                onToggleCollapse = onConfigToggleCollapse, onSave = onConfigSave, onDiscard = onConfigDiscard,
                colors = colors,
            )
        }
        vif({ activeTab() == PLUGIN_TAB_LIST }) {
            DshPluginInventoryListView(
                showSwitch = false, loading = loading, error = error, keyword = keyword, onKeyword = onKeyword,
                hasKeyword = hasKeyword, onClearKeyword = onClearKeyword, onSearchInputRef = onSearchInputRef,
                rows = rows, total = total, expandedId = expandedId, busyId = busyId,
                onToggleExpand = onToggleExpand, actionError = actionError, actionNotice = actionNotice,
                onToggleEnabled = onToggleEnabled, onReload = onReload, colors = colors,
            )
        }
        vif({ activeTab() == PLUGIN_TAB_SWITCH }) {
            DshPluginInventoryListView(
                showSwitch = true, loading = loading, error = error, keyword = keyword, onKeyword = onKeyword,
                hasKeyword = hasKeyword, onClearKeyword = onClearKeyword, onSearchInputRef = onSearchInputRef,
                rows = rows, total = total, expandedId = expandedId, busyId = busyId,
                onToggleExpand = onToggleExpand, actionError = actionError, actionNotice = actionNotice,
                onToggleEnabled = onToggleEnabled, onReload = onReload, colors = colors,
            )
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
                        Text { attr { text("处理中…"); marginTop(10f); fontSize(13f); color(colors().labelTertiary) } }
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

private fun ViewContainer<*, *>.DshPluginInventoryListView(
    showSwitch: Boolean,
    loading: () -> Boolean,
    error: () -> String,
    keyword: () -> String,
    onKeyword: (String) -> Unit,
    hasKeyword: () -> Boolean,
    onClearKeyword: () -> Unit,
    onSearchInputRef: (ViewRef<InputView>) -> Unit,
    rows: () -> ObservableList<DshPluginEntry>,
    total: () -> Int,
    expandedId: () -> String,
    busyId: () -> String,
    onToggleExpand: (DshPluginEntry) -> Unit,
    actionError: () -> String,
    actionNotice: () -> String,
    onToggleEnabled: (DshPluginEntry, Boolean) -> Unit,
    onReload: (DshPluginEntry) -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr { height(36f); marginLeft(16f); marginRight(16f); marginTop(14f); backgroundColor(colors().bgLayer1)
            borderRadius(8f); border(Border(1f, BorderStyle.SOLID, colors().borderL2))
            flexDirectionRow(); alignItemsCenter() }
        Input {
            attr { flex(1f); height(36f); marginLeft(12f); fontSize(13f); text(keyword())
                placeholder("搜索插件名称或 ID"); color(colors().labelPrimary); placeholderColor(colors().labelTertiary) }
            ref { onSearchInputRef(it) }
            event { textDidChange { onKeyword(it.text) } }
        }
        vif({ hasKeyword() }) {
            View {
                attr { size(28f, 28f); allCenter(); marginRight(4f) }
                Image {
                    attr { src(ImageUri.commonAssets("x.svg")); size(16f, 16f); tintColor(colors().labelTertiary) }
                }
                DshHitButton { onClearKeyword() }
            }
        }
    }
    View {
        attr { flexDirectionRow(); alignItemsFlexEnd(); marginLeft(16f); marginRight(16f); marginTop(12f) }
        Text { attr { text("插件列表"); fontSize(13f); fontWeightBold(); color(colors().labelPrimary) } }
        Text { attr { text(" ${total()}"); fontSize(12f); color(colors().labelTertiary) } }
    }
    vif({ actionNotice().isNotEmpty() }) {
        Text { attr { text(actionNotice()); marginLeft(16f); marginRight(16f); marginTop(8f); fontSize(12f)
            color(colors().stateSuccessPrimary) } }
    }
    vif({ actionError().isNotEmpty() }) {
        Text { attr { text(actionError()); marginLeft(16f); marginRight(16f); marginTop(8f); fontSize(12f)
            color(colors().stateErrorPrimary) } }
    }
    vif({ error().isNotEmpty() }) {
        Text { attr { text(error()); margin(16f); fontSize(13f); color(colors().stateErrorPrimary) } }
    }
    vif({ loading() }) {
        Text { attr { text("正在读取 Host 插件清单…"); margin(16f); fontSize(13f); color(colors().labelTertiary) } }
    }
    vif({ !loading() && error().isEmpty() && rows().isEmpty() }) {
        Text { attr { text(if (total() == 0) "Host 暂无插件" else "无匹配插件，请调整搜索")
            margin(20f); fontSize(13f); color(colors().labelTertiary) } }
    }
    List {
        attr { flex(1f); marginTop(10f) }
        vforLazy({ rows() }) { entry, _, _ ->
            PluginInventoryRow(
                entry = entry,
                expandedId = expandedId,
                busyId = busyId,
                showSwitch = showSwitch,
                onToggleExpand = { onToggleExpand(entry) },
                onToggleEnabled = { desired -> onToggleEnabled(entry, desired) },
                onReload = { onReload(entry) },
                colors = colors,
            )
        }
    }
}

private fun ViewContainer<*, *>.PluginInventoryRow(
    entry: DshPluginEntry,
    expandedId: () -> String,
    busyId: () -> String,
    showSwitch: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onReload: () -> Unit,
    colors: () -> DshColorTokens,
) {
    val open = expandedId() == entry.id
    View {
        attr {
            flexDirectionColumn()
            marginLeft(16f)
            marginRight(16f)
            marginBottom(10f)
            backgroundColor(colors().bgLayer3)
            borderRadius(10f)
            border(Border(1f, BorderStyle.SOLID, if (open) colors().borderL1 else colors().borderL2))
            if (open) boxShadow(BoxShadow(0f, 2f, 10f, Color(0x14000000)))
            overflow(false)
        }
        View {
            attr { minHeight(52f); flexDirectionRow(); alignItemsCenter(); paddingLeft(14f); paddingRight(14f) }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                event { click { onToggleExpand() } }
                Text { attr { text(entry.name); fontSize(14f); fontWeightBold(); color(colors().labelPrimary)
                    lines(1); textOverFlowTail(); flex(1f) } }
                vif({ entry.enabled && entry.phase != null }) {
                    View { attr { size(7f, 7f); borderRadius(4f); marginLeft(7f)
                        backgroundColor(pluginPhaseColor(entry.phase, colors())) } }
                }
            }
            View {
                attr { borderRadius(5f); paddingLeft(6f); paddingRight(6f); paddingTop(1f); paddingBottom(1f)
                    backgroundColor(if (entry.enabled) colors().stateSuccessTertiary else colors().bgLayer1) }
                Text { attr { text(if (entry.enabled) "已启用" else "已停用"); fontSize(11f)
                    color(if (entry.enabled) colors().stateSuccessPrimary else colors().labelSecondary) } }
            }
            if (showSwitch) {
                View { attr { marginLeft(8f) } }
                PluginToggle(
                    enabled = entry.enabled,
                    interactive = { entry.canToggle && busyId() != entry.id },
                    colors = colors,
                    onToggle = onToggleEnabled,
                )
            }
            Text { attr { text(if (open) "▾" else "▸"); fontSize(10f); marginLeft(7f); color(colors().labelTertiary) }
                event { click { onToggleExpand() } } }
        }
        vif({ open }) {
            View { attr { height(1f); backgroundColor(colors().borderL2) } }
            View {
                attr { flexDirectionColumn(); backgroundColor(colors().bgModulePlatform)
                    paddingLeft(14f); paddingRight(14f); paddingTop(10f); paddingBottom(12f) }
                PluginDetailRow("Loader 条目", entry.id, colors().labelSecondary, colors, mono = true)
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
                vif({ showSwitch && entry.enabled && entry.canToggle && busyId() != entry.id }) {
                    View {
                        attr { flexDirectionRow(); justifyContentFlexEnd(); marginTop(10f) }
                        View {
                            attr { height(28f); paddingLeft(10f); paddingRight(10f); allCenter(); borderRadius(14f)
                                border(Border(1f, BorderStyle.SOLID, colors().borderL2)) }
                            Text { attr { text("重载"); fontSize(12f); color(colors().labelPrimary) } }
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
    mono: Boolean = false,
) {
    View {
        attr { flexDirectionRow(); marginTop(6f) }
        Text { attr { text(label); width(78f); fontSize(11f); color(colors().labelTertiary) } }
        Text { attr { text(value); flex(1f); fontSize(12f); color(valueColor)
            if (mono) fontFamily("monospace") } }
    }
}

private fun ViewContainer<*, *>.PluginDetailBlock(
    label: String,
    value: String,
    colors: () -> DshColorTokens,
) {
    Text { attr { text(label); marginTop(10f); fontSize(11f); color(colors().labelTertiary) } }
    Text { attr { text(value); marginTop(4f); fontSize(12f); fontFamily("monospace"); color(colors().labelSecondary) } }
}
