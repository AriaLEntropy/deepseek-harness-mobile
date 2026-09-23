package com.example.dsh.dsh

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// ===== 插件设置页（Host 插件）=====
// 在「设置 → 应用 → Host 插件」下打开，两个标签页：
//   · 插件配置：读写 Host 的 settings 命名空间与 credentials（官方接口）
//   · 插件列表：只读展示官方 pluginInventory/list 快照
// 颜色与 DshSettingsPage 保持一致的硬编码配色。

internal const val PLUGIN_TAB_CONFIG = "config"
internal const val PLUGIN_TAB_LIST = "list"

/** 插件页配色，取值与 DshSettingsPage 的颜色常量一致。 */
internal object DshPluginPalette {
    val canvas = Color(0xFFF1F3F5)
    val card = Color(0xFFFFFFFF)
    val labelPrimary = Color(0xFF0F1115)
    val labelSecondary = Color(0xFF61666B)
    val labelTertiary = Color(0xFF81858C)
    val labelDimmed = Color(0xFF9AA0A6)
    val border = Color(0x0A000000)
    val borderStrong = Color(0x14000000)
    val accent = Color(0xFF4176E6)
    val error = Color(0xFFEC1313)
    val success = Color(0xFF2FA36B)
    val successSoft = Color(0x1A2FA36B)
    val moduleBg = Color(0xFFF5F6F8)
}

private const val PLUGIN_SCREEN_MARGIN = 16f

internal fun ViewContainer<*, *>.DshPluginPage(
    activeTab: () -> String,
    onSelectTab: (String) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    // 插件列表
    listLoading: () -> Boolean,
    listError: () -> String,
    keyword: () -> String,
    hasKeyword: () -> Boolean,
    onKeyword: (String) -> Unit,
    onClearKeyword: () -> Unit,
    onSearchInputRef: (ViewRef<InputView>) -> Unit,
    rows: () -> ObservableList<DshPluginEntry>,
    total: () -> Int,
    expandedId: () -> String,
    onToggleExpand: (DshPluginEntry) -> Unit,
    // 插件配置
    configLoading: () -> Boolean,
    configError: () -> String,
    configWritable: () -> Boolean,
    configCards: () -> ObservableList<DshPluginConfigCard>,
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
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            backgroundColor(DshPluginPalette.canvas)
            paddingTop(pagerData.statusBarHeight)
        }
        // 顶部栏：返回 + 标题 + 刷新
        View {
            attr {
                height(52f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(PLUGIN_SCREEN_MARGIN)
                paddingRight(PLUGIN_SCREEN_MARGIN - 4f)
                backgroundColor(DshPluginPalette.canvas)
            }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { size(40f, 40f); borderRadius(20f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("chevron-left.svg")); size(20f, 20f); tintColor(DshPluginPalette.labelPrimary) } }
                    event { click { onClose() } }
                }
            }
            Text { attr { text("插件"); fontSize(17f); fontWeightBold(); color(DshPluginPalette.labelPrimary) } }
            View {
                attr { flex(1f); flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter() }
                View {
                    attr { size(40f, 40f); allCenter(); opacity(if (listLoading() || configLoading()) 0.4f else 1f) }
                    Image { attr { src(ImageUri.commonAssets("icon-refresh16.svg")); size(18f, 18f); tintColor(DshPluginPalette.labelSecondary) } }
                    event { click { if (!listLoading() && !configLoading()) onRefresh() } }
                }
            }
        }
        Text {
            attr {
                text("配置和查看本部署已安装的 Host 插件。")
                marginLeft(PLUGIN_SCREEN_MARGIN)
                marginRight(PLUGIN_SCREEN_MARGIN)
                fontSize(13f)
                color(DshPluginPalette.labelTertiary)
            }
        }
        // 下划线标签栏
        View {
            attr { flexDirectionRow(); marginLeft(PLUGIN_SCREEN_MARGIN); marginRight(PLUGIN_SCREEN_MARGIN); marginTop(6f) }
            listOf(
                PLUGIN_TAB_CONFIG to "插件配置",
                PLUGIN_TAB_LIST to "插件列表",
            ).forEach { (value, label) ->
                View {
                    attr { height(46f); flexDirectionColumn(); alignItemsCenter(); justifyContentCenter(); marginRight(22f) }
                    event { click { onSelectTab(value) } }
                    Text {
                        attr {
                            text(label)
                            fontSize(13f)
                            color(if (activeTab() == value) DshPluginPalette.labelPrimary else DshPluginPalette.labelTertiary)
                        }
                    }
                    View {
                        attr {
                            height(2f); marginTop(7f); width(34f); borderRadius(2f)
                            backgroundColor(if (activeTab() == value) DshPluginPalette.labelPrimary else Color(0x00000000))
                        }
                    }
                }
            }
        }
        View { attr { height(1f); backgroundColor(DshPluginPalette.border) } }

        vif({ activeTab() == PLUGIN_TAB_CONFIG }) {
            DshPluginConfigView(
                cards = configCards,
                loading = configLoading,
                error = configError,
                writable = configWritable,
                draft = configDraft,
                secretDraft = configSecretDraft,
                collapsed = configCollapsed,
                busyNamespace = configBusyNamespace,
                cardError = configCardError,
                cardNotice = configCardNotice,
                hasChanges = configHasChanges,
                onDraft = onConfigDraft,
                onSecretDraft = onConfigSecretDraft,
                onToggleCollapse = onConfigToggleCollapse,
                onSave = onConfigSave,
                onDiscard = onConfigDiscard,
            )
        }
        vif({ activeTab() == PLUGIN_TAB_LIST }) {
            DshPluginListView(
                loading = listLoading,
                error = listError,
                keyword = keyword,
                hasKeyword = hasKeyword,
                onKeyword = onKeyword,
                onClearKeyword = onClearKeyword,
                onSearchInputRef = onSearchInputRef,
                rows = rows,
                total = total,
                expandedId = expandedId,
                onToggleExpand = onToggleExpand,
            )
        }
    }
}

private fun ViewContainer<*, *>.DshPluginListView(
    loading: () -> Boolean,
    error: () -> String,
    keyword: () -> String,
    hasKeyword: () -> Boolean,
    onKeyword: (String) -> Unit,
    onClearKeyword: () -> Unit,
    onSearchInputRef: (ViewRef<InputView>) -> Unit,
    rows: () -> ObservableList<DshPluginEntry>,
    total: () -> Int,
    expandedId: () -> String,
    onToggleExpand: (DshPluginEntry) -> Unit,
) {
    View {
        attr {
            height(36f); marginLeft(PLUGIN_SCREEN_MARGIN); marginRight(PLUGIN_SCREEN_MARGIN); marginTop(14f)
            backgroundColor(DshPluginPalette.card); borderRadius(8f)
            border(Border(1f, BorderStyle.SOLID, DshPluginPalette.borderStrong))
            flexDirectionRow(); alignItemsCenter()
        }
        Input {
            attr {
                flex(1f); height(36f); marginLeft(12f); fontSize(13f); text(keyword())
                placeholder("搜索插件名称或 ID"); color(DshPluginPalette.labelPrimary)
                placeholderColor(DshPluginPalette.labelTertiary)
            }
            ref { onSearchInputRef(it) }
            event { textDidChange { onKeyword(it.text) } }
        }
        vif({ hasKeyword() }) {
            View {
                attr { size(28f, 28f); allCenter(); marginRight(4f) }
                Image { attr { src(ImageUri.commonAssets("x.svg")); size(16f, 16f); tintColor(DshPluginPalette.labelTertiary) } }
                DshHitButton { onClearKeyword() }
            }
        }
    }
    View {
        attr { flexDirectionRow(); alignItemsFlexEnd(); marginLeft(PLUGIN_SCREEN_MARGIN); marginRight(PLUGIN_SCREEN_MARGIN); marginTop(12f) }
        Text { attr { text("插件列表"); fontSize(13f); fontWeightBold(); color(DshPluginPalette.labelPrimary) } }
        Text { attr { text(" ${total()}"); fontSize(12f); color(DshPluginPalette.labelTertiary) } }
    }
    vif({ error().isNotEmpty() }) {
        Text { attr { text(error()); margin(PLUGIN_SCREEN_MARGIN); fontSize(13f); color(DshPluginPalette.error) } }
    }
    vif({ loading() }) {
        Text { attr { text("正在读取 Host 插件清单…"); margin(PLUGIN_SCREEN_MARGIN); fontSize(13f); color(DshPluginPalette.labelTertiary) } }
    }
    vif({ !loading() && error().isEmpty() && rows().isEmpty() }) {
        Text {
            attr {
                text(if (total() == 0) "Host 暂无插件" else "无匹配插件，请调整搜索")
                margin(20f); fontSize(13f); color(DshPluginPalette.labelTertiary)
            }
        }
    }
    List {
        attr { flex(1f); marginTop(10f) }
        vforLazy({ rows() }) { entry, _, _ ->
            PluginInventoryRow(
                entry = entry,
                expandedId = expandedId,
                onToggleExpand = { onToggleExpand(entry) },
            )
        }
    }
}

private fun ViewContainer<*, *>.PluginInventoryRow(
    entry: DshPluginEntry,
    expandedId: () -> String,
    onToggleExpand: () -> Unit,
) {
    View {
        attr {
            flexDirectionColumn()
            marginLeft(PLUGIN_SCREEN_MARGIN)
            marginRight(PLUGIN_SCREEN_MARGIN)
            marginBottom(10f)
            backgroundColor(DshPluginPalette.card)
            borderRadius(10f)
            // 注意：展开态必须在 attr 块内联求值，才会订阅 expandedId 的变化。
            border(
                Border(
                    1f,
                    BorderStyle.SOLID,
                    if (expandedId() == entry.id) DshPluginPalette.borderStrong else DshPluginPalette.border,
                ),
            )
            overflow(false)
        }
        View {
            attr { minHeight(52f); flexDirectionRow(); alignItemsCenter(); paddingLeft(14f); paddingRight(14f) }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                event { click { onToggleExpand() } }
                Text {
                    attr {
                        text(entry.name); fontSize(14f); fontWeightBold(); color(DshPluginPalette.labelPrimary)
                        lines(1); textOverFlowTail(); flex(1f)
                    }
                }
                vif({ entry.enabled && entry.phase != null }) {
                    View {
                        attr {
                            size(7f, 7f); borderRadius(4f); marginLeft(7f)
                            backgroundColor(pluginPhaseColor(entry.phase))
                        }
                    }
                }
            }
            View {
                attr {
                    borderRadius(5f); paddingLeft(6f); paddingRight(6f); paddingTop(1f); paddingBottom(1f)
                    backgroundColor(if (entry.enabled) DshPluginPalette.successSoft else DshPluginPalette.moduleBg)
                }
                Text {
                    attr {
                        text(if (entry.enabled) "已启用" else "已停用"); fontSize(11f)
                        color(if (entry.enabled) DshPluginPalette.success else DshPluginPalette.labelSecondary)
                    }
                }
            }
            Text {
                attr {
                    text(if (expandedId() == entry.id) "▾" else "▸"); fontSize(10f); marginLeft(7f)
                    color(DshPluginPalette.labelTertiary)
                }
                event { click { onToggleExpand() } }
            }
        }
        vif({ expandedId() == entry.id }) {
            View { attr { height(1f); backgroundColor(DshPluginPalette.border) } }
            View {
                attr {
                    flexDirectionColumn(); backgroundColor(DshPluginPalette.moduleBg)
                    paddingLeft(14f); paddingRight(14f); paddingTop(10f); paddingBottom(12f)
                }
                PluginDetailRow("Loader 条目", entry.id, mono = true)
                if (entry.enabled) {
                    PluginDetailRow("Cordis 状态", pluginPhaseLabel(entry.phase), mono = false)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.PluginDetailRow(label: String, value: String, mono: Boolean) {
    Text { attr { text(label); marginTop(8f); fontSize(11f); color(DshPluginPalette.labelTertiary) } }
    Text {
        attr {
            text(value); marginTop(4f); fontSize(12f); color(DshPluginPalette.labelSecondary)
            if (mono) fontFamily("monospace")
        }
    }
}
