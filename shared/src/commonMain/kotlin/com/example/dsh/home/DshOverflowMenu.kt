package com.example.dsh.home

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
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ScrollPicker
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.ScrollParams
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 会话 topbar overflow menu 中的一个操作项。 */
internal data class DshOverflowAction(
    val id: String,
    val label: String,
    val iconAsset: String,
    val danger: Boolean = false,
)

/**
 * 会话 topbar 右上角 overflow menu。
 *
 * 菜单项布局类似 CSS `justify-content: space-between`：左侧文字、右侧图标；
 * 删除项（danger）文字与图标同为错误红；其余三项文字用主文本色、图标用
 * DSH 原版菜单的 tertiary 灰（与重命名/归档原版图标颜色一致）。
 */
internal fun ViewContainer<*, *>.DshOverflowMenu(
    visible: () -> Boolean,
    actions: () -> ObservableList<DshOverflowAction>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    statusBarHeight: Float,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
        val menuWidth = 220f
        // 挂在 topbar（58dp）下方 6dp，右缘与 topbar padding 对齐
        val menuLeft = pageViewWidth - menuWidth - 12f
        val menuTop = statusBarHeight + 58f + 6f
        // 透明点击捕获层：点击空白处关闭菜单
        View {
            attr { absolutePositionAllZero() }
            event { click { onDismiss() } }
            View {
                attr {
                    positionAbsolute()
                    left(menuLeft)
                    top(menuTop)
                    width(menuWidth)
                    borderRadius(12f)
                    border(Border(1f, BorderStyle.SOLID, colors().borderL2))
                    backgroundColor(colors().bgLayer1)
                    boxShadow(BoxShadow(0f, 4f, 16f, Color(0x33000000)))
                    paddingTop(6f)
                    paddingBottom(6f)
                }
                event { click { } } // 消费点击，避免穿透到遮罩
                vfor({ actions() }) { item ->
                    View {
                        attr {
                            height(44f)
                            flexDirectionRow()
                            alignItemsCenter()
                            justifyContentSpaceBetween()
                            paddingLeft(14f)
                            paddingRight(12f)
                            borderRadius(8f)
                        }
                        Text {
                            attr {
                                text(item.label)
                                fontSize(14f)
                                color(if (item.danger) colors().stateErrorPrimary else colors().labelPrimary)
                            }
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets(item.iconAsset))
                                size(18f, 18f)
                                tintColor(if (item.danger) colors().stateErrorPrimary else colors().labelTertiary)
                            }
                        }
                        DshHitButton { onSelect(item.id) }
                    }
                }
            }
        }
    }
}

/**
 * 会话级日志查看弹窗：当前会话的日志列表 + 详情。
 *
 * 列表仅显示当前会话（sessionId 匹配）的日志，包含尚未落盘的内存日志，
 * 按 seq 从新到旧展示；点按某条进入详情。
 */
// 日志弹窗上次可见状态：跨 recompose 追踪由关到开的边沿，用于打开时把筛选值回显到输入框
private var logModalLastVisible = false

internal fun ViewContainer<*, *>.DshSessionLogModal(
    visible: () -> Boolean,
    events: () -> ObservableList<LogEvent>,
    total: () -> Int,
    exporting: () -> Boolean,
    timeFilter: () -> Int,
    levelFilter: () -> Set<LogLevel>,
    selectedTypes: () -> Set<String>,
    typeOptions: () -> ObservableList<String>,
    keyword: () -> String,
    onSelect: (LogEvent?) -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit,
    onTimeFilter: (Int) -> Unit,
    customStartMs: () -> Long? = { null },
    customEndMs: () -> Long? = { null },
    onLogCustomTime: (Long?, Long?) -> Unit = { _, _ -> },
    onOpenTimePicker: (Boolean) -> Unit = {},
    onToggleLevel: (LogLevel) -> Unit,
    onToggleType: (String) -> Unit,
    onKeyword: (String) -> Unit,
    onClearFilters: () -> Unit,
    onClearRequest: () -> Unit = {},
    onFeedbackPackage: () -> Unit = {},

    clearDialogVisible: () -> Boolean = { false },
    clearing: () -> Boolean = { false },
    onClearDialogCancel: () -> Unit = {},
    onClearDialogConfirm: () -> Unit = {},
    scrollerRef: (com.tencent.kuikly.core.base.ViewRef<com.tencent.kuikly.core.views.ListView<*, *>>) -> Unit = {},
    onLogScroll: (ScrollParams) -> Unit = {},
    newCount: () -> Int = { 0 },
    followBottom: () -> Boolean = { true },
    onJumpToBottom: () -> Unit = {},
    allMode: () -> Boolean = { false },
    sessionTitleProvider: (String) -> String = { it },
    statusBarHeight: Float,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },

    searchExpanded: () -> Boolean = { false },
    typeSheetVisible: () -> Boolean = { false },
    onSetSearchExpanded: (Boolean) -> Unit = {},
    onSetTypeSheetVisible: (Boolean) -> Unit = {},
    onClearTypes: () -> Unit = {},
    sheetKeyword: () -> String = { "" },
    onSetSheetKeyword: (String) -> Unit = {},
) {
    var searchInputRef: com.tencent.kuikly.core.base.ViewRef<InputView>? = null
    var sheetSearchRef: com.tencent.kuikly.core.base.ViewRef<InputView>? = null

    var lastSearchExpanded = false
    var lastTypeSheetVisible = false

    val searchOpening = searchExpanded() && !lastSearchExpanded
    lastSearchExpanded = searchExpanded()
    val sheetOpening = typeSheetVisible() && !lastTypeSheetVisible
    lastTypeSheetVisible = typeSheetVisible()
    if (sheetOpening) { onSetSheetKeyword("") }

    vif({ visible() }) {
        Modal(inWindow = true) {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0x80000000))
            }
            View {
                attr {
                    width(pageViewWidth)
                    absolutePositionAllZero()
                    flexDirectionColumn()
                    backgroundColor(colors().bgBase)
                }

                // ===== 顶部栏（默认态：品牌色渐变 + 标题 + 图标按钮） =====
                vif({ !searchExpanded() }) {
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingTop(statusBarHeight + 6f)
                            paddingBottom(10f)
                            paddingLeft(8f)
                            paddingRight(8f)
                            backgroundColor(com.example.dsh.theme.DshDefaultPalette.deepseek500)
                        }
                        View {
                            attr { width(36f); height(36f); borderRadius(18f); backgroundColor(Color(0xF0F0F0)); alignItemsCenter(); justifyContentCenter(); marginLeft(4f) }
                            event { click { onClose() } }
                            Image { attr { src(ImageUri.commonAssets("chevron-left.svg")); size(18f, 18f); tintColor(Color(0xFF1A1A1A)) } }
                        }
                        View {
                            attr { flex(1f); flexDirectionColumn(); alignItemsCenter() }
                            Text { attr { text("会话日志"); fontSize(17f); fontWeightBold();  } }
                            Text { attr { text(total().toString() + " 条记录"); fontSize(11f);  marginTop(2f) } }
                        }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); width(120f); justifyContentFlexEnd() }
                            View {
                                attr { width(36f); height(36f); alignItemsCenter(); justifyContentCenter() }
                                event { click { onSetSearchExpanded(true) } }
                                Text { attr { text("🔍"); fontSize(16f); color(Color(0xFFFFFFFF)) } }
                            }
                            View {
                                attr { width(36f); height(36f); alignItemsCenter(); justifyContentCenter() }
                                event { click { if (!exporting()) onExport() } }
                                Text { attr { text(if (exporting()) "…" else "↗"); fontSize(16f); color(Color(0xFFFFFFFF)) } }
                            }
                            View {
                                attr { width(36f); height(36f); alignItemsCenter(); justifyContentCenter() }
                                event { click { onClearRequest() } }
                                Text { attr { text("🗑"); fontSize(15f); color(Color(0xFFFFFFFF)) } }
                            }
                        }
                    }
                }

                // ===== 顶部栏（搜索态：SearchView） =====
                vif({ searchExpanded() }) {
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingTop(statusBarHeight + 6f)
                            paddingBottom(10f)
                            paddingLeft(8f)
                            paddingRight(8f)
                            backgroundColor(com.example.dsh.theme.DshDefaultPalette.deepseek500)
                        }
                        View {
                            attr { width(36f); height(36f); borderRadius(18f); backgroundColor(Color(0xF0F0F0)); alignItemsCenter(); justifyContentCenter() }
                            event { click { onSetSearchExpanded(false); onKeyword("") } }
                            Image { attr { src(ImageUri.commonAssets("chevron-left.svg")); size(18f, 18f); tintColor(Color(0xFF1A1A1A)) } }
                        }
                        View {
                            attr {
                                flex(1f); height(34f); flexDirectionRow(); alignItemsCenter()
                                backgroundColor(Color(0x22FFFFFF)); borderRadius(17f)
                                paddingLeft(12f); paddingRight(12f); marginLeft(4f); marginRight(4f)
                            }
                            Text { attr { text("🔍"); fontSize(13f); color(Color(0xCCFFFFFF)); marginRight(6f) } }
                            Input {
                                ref { searchInputRef = it; if (searchOpening) it.view?.setText(keyword()) }
                                attr {
                                    flex(1f); fontSize(14f); color(Color(0xFFFFFFFF))
                                    placeholder("搜索日志内容"); placeholderColor(Color(0x88FFFFFF))
                                }
                                event { textDidChange { onKeyword(it.text) } }
                            }
                            vif({ keyword().isNotEmpty() }) {
                                View {
                                    attr { width(24f); height(24f); alignItemsCenter(); justifyContentCenter() }
                                    event { click { searchInputRef?.view?.setText(""); onKeyword("") } }
                                    Text { attr { text("✕"); fontSize(13f); color(Color(0xCCFFFFFF)) } }
                                }
                            }
                        }
                    }
                }

                // ===== 时间筛选 Tab（Material 风格） =====
                View {
                    attr {
                        flexDirectionRow()
                        backgroundColor(colors().bgLayer1)
                        borderBottom(Border(0.5f, BorderStyle.SOLID, colors().borderL1))
                    }
                    val timeLabels = listOf("全部", "10分钟", "1小时", "今天", "自定义")
                    for (i in timeLabels.indices) {
                        View {
                            attr {
                                flex(1f); height(40f); alignItemsCenter(); justifyContentCenter()
                            }
                            event { click { onTimeFilter(i); if (i == 4) onOpenTimePicker(true) } }
                            vif({ timeFilter() == i }) {
                                View {
                                    attr { absolutePosition(bottom = 0f, left = 12f, right = 12f); height(2f); backgroundColor(colors().stateBusinessPrimary) }
                                }
                            }
                            Text {
                                attr {
                                    text(timeLabels[i]); fontSize(12f)
                                    color(if (timeFilter() == i) colors().stateBusinessPrimary else colors().labelTertiary)
                                    fontWeightBold()
                                }
                            }
                        }
                    }
                }

                // ===== 辅助筛选行（级别 toggle + 类型下拉 + 匹配数） =====
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        paddingLeft(12f); paddingRight(12f)
                        height(40f)
                        backgroundColor(colors().bgLayer1)
                        borderBottom(Border(0.5f, BorderStyle.SOLID, colors().borderL1))
                    }
                    vif({ timeFilter() == 4 }) {
                        View {
                            attr {
                                flexDirectionRow(); alignItemsCenter(); height(26f)
                                backgroundColor(com.example.dsh.theme.DshDefaultPalette.blue50); borderRadius(4f)
                                paddingLeft(8f); paddingRight(8f); marginRight(8f)
                            }
                            event { click { onOpenTimePicker(true) } }
                            Text {
                                attr {
                                    text((customStartMs()?.let { LogExporter.formatTimestamp(it).substring(5, 16).replace('T', ' ') } ?: "不限") + " ~ " + (customEndMs()?.let { LogExporter.formatTimestamp(it).substring(5, 16).replace('T', ' ') } ?: "不限"))
                                    fontSize(10f); color(colors().stateBusinessPrimary)
                                }
                            }
                        }
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); marginRight(8f) }
                        for (lv in LogLevel.entries) {
                            val lvColor = sessionLogLevelColor(lv)


                            View {
                                attr {
                                    width(26f); height(26f); borderRadius(4f)
                                    alignItemsCenter(); justifyContentCenter(); marginRight(6f)
                                    border(Border(if (lv in levelFilter() && levelFilter().size == 1) 2f else 1f, BorderStyle.SOLID, if (lv in levelFilter()) Color(lvColor) else colors().borderL2))
                                    backgroundColor(if (lv in levelFilter()) Color(lvColor) else colors().bgLayer2)
                                    highlightBackgroundColor(Color(0x26000000))
                                }
                                event { click { onToggleLevel(lv) } }
                                Text { attr { text(sessionLogLevelLabel(lv)); fontSize(12f); fontWeightBold(); color(if (lv in levelFilter()) Color(0xFFFFFFFF) else colors().labelTertiary) } }
                            }
                        }
                    }
                    View {
                        attr {
                            flexDirectionRow(); alignItemsCenter(); height(28f)
                            border(Border(1f, BorderStyle.SOLID, colors().borderL2))
                            borderRadius(6f); paddingLeft(8f); paddingRight(8f); flex(1f)
                        }
                        event { click { onSetTypeSheetVisible(true) } }
                        Text { attr { text("类型"); fontSize(11f); color(colors().labelTertiary); marginRight(4f) } }
                        Text {
                            attr {
                                text(if (selectedTypes().isEmpty()) "全部" else selectedTypes().joinToString(", ") { it.substringAfterLast(".") })
                                fontSize(11f); color(colors().labelPrimary); flex(1f); lines(1)
                            }
                        }
                        Text { attr { text("▾"); fontSize(11f); color(colors().labelTertiary); marginLeft(4f) } }
                    }
                    Text {
                        attr { text(events().size.toString()); fontSize(11f); color(colors().labelTertiary); marginLeft(8f) }
                    }
                }

                // ===== 空状态 =====
                vif({ total() == 0 }) {
                    Text { attr { text("暂无日志"); marginTop(90f); alignSelfCenter(); fontSize(14f); color(colors().labelTertiary) } }
                }
                vif({ total() > 0 && events().isEmpty() }) {
                    View {
                        attr { flexDirectionColumn(); alignItemsCenter(); marginTop(70f) }
                        Text { attr { text("无匹配结果"); fontSize(14f); color(colors().labelTertiary) } }
                        View {
                            attr { marginTop(12f); padding(top = 8f, left = 18f, bottom = 8f, right = 18f); borderRadius(16f); border(Border(1f, BorderStyle.SOLID, colors().stateBusinessPrimary)); highlightBackgroundColor(Color(0x0A000000)) }

                            event { click { onClearFilters() } }
                            Text { attr { text("清除筛选"); fontSize(13f); color(colors().stateBusinessPrimary) } }
                        }
                    }
                }

                // ===== 日志列表（Chucker 双行紧凑布局） =====
                vif({ events().isNotEmpty() }) {
                    View {
                        attr { flex(1f); marginTop(2f) }
                        List {
                            attr { absolutePositionAllZero(); firstContentLoadMaxIndex(LOG_INITIAL_RENDER_COUNT) }
                            ref { scrollerRef(it) }
                            event { scroll { params -> onLogScroll(params) } }
                            vforLazy({ events() }, maxLoadItem = LOG_MAX_RENDERED) { entry, _, _ ->
                                View {
                                    attr {
                                        flexDirectionRow()
                                        paddingLeft(12f); paddingRight(12f)
                                        paddingTop(10f); paddingBottom(10f)
                                        borderBottom(Border(0.5f, BorderStyle.SOLID, colors().borderL1))
                                        highlightBackgroundColor(Color(0x0A000000))
                                    }
                                    event { click { onSelect(entry) } }
                                    View {
                                        attr {
                                            width(24f); height(24f); borderRadius(4f)
                                            alignItemsCenter(); justifyContentCenter()
                                            backgroundColor(Color(sessionLogLevelColor(entry.level)))
                                        }
                                        Text { attr { text(sessionLogLevelLabel(entry.level)); fontSize(12f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
                                    }
                                    View {
                                        attr { flex(1f); marginLeft(10f); flexDirectionColumn() }
                                        View {
                                            attr { flexDirectionRow(); alignItemsCenter() }
                                            Text {
                                                attr {
                                                    text(entry.type); fontSize(12f); fontWeightBold()
                                                    color(colors().labelPrimary); flex(1f); lines(1)
                                                }
                                            }
                                            Text {
                                                attr {
                                                    text(LogExporter.formatTimestamp(entry.timestamp).substring(11, 19))
                                                    fontSize(11f); color(colors().labelTertiary)
                                                }
                                            }
                                        }
                                        Text {
                                            attr {
                                                text(entry.message); fontSize(12f); color(colors().labelPrimary)
                                                marginTop(3f); lines(2)
                                            }
                                        }
                                        Text {
                                            attr {
                                                text("seq=" + entry.seq + "  " + entry.size + " B" + if (allMode()) "  " + (if (entry.sessionId.isNullOrEmpty()) "移动端" else sessionTitleProvider(entry.sessionId)) else "")
                                                fontSize(10f); color(colors().labelTertiary); marginTop(3f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        vif({ !followBottom() && newCount() > 0 }) {
                            View {
                                attr {
                                    positionAbsolute(); right(12f); bottom(14f)
                                    flexDirectionRow(); alignItemsCenter()
                                    padding(left = 12f, right = 12f); height(32f); borderRadius(16f)
                                    backgroundColor(colors().stateBusinessPrimary)
                                    boxShadow(BoxShadow(0f, 2f, 8f, Color(0x33000000)))
                                }
                                event { click { onJumpToBottom() } }
                                Text { attr { text("↓ 新日志 " + newCount()); fontSize(12f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
                            }
                        }
                    }
                }
            }
        }

        // ===== 类型选择 Bottom Sheet =====
        vif({ typeSheetVisible() }) {
            Modal(inWindow = true) {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(Color(0x66000000))
                }
                View {
                    attr { absolutePositionAllZero() }
                    event { click { onSetTypeSheetVisible(false) } }
                }
                View {
                    attr {
                        width(pageViewWidth)
                        absolutePosition(bottom = 0f, left = 0f, right = 0f)
                        flexDirectionColumn()
                        backgroundColor(colors().bgLayer1)
                        borderRadius(topLeft = 16f, topRight = 16f, bottomLeft = 0f, bottomRight = 0f)
                        maxHeight(pageViewWidth * 1.2f)
                    }
                    View {
                        attr { alignItemsCenter(); paddingTop(8f); paddingBottom(4f) }
                        View { attr { width(36f); height(4f); borderRadius(2f); backgroundColor(colors().borderL2) } }
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); paddingLeft(16f); paddingRight(16f); paddingBottom(8f) }
                        Text { attr { text("选择事件类型"); fontSize(16f); fontWeightBold(); color(colors().labelPrimary); flex(1f) } }
                        View {
                            attr { width(32f); height(32f); alignItemsCenter(); justifyContentCenter() }
                            event { click { onSetTypeSheetVisible(false) } }
                            Text { attr { text("✕"); fontSize(16f); color(colors().labelTertiary) } }
                        }
                    }
                    View {
                        attr {
                            flexDirectionRow(); alignItemsCenter(); height(36f)
                            marginLeft(16f); marginRight(16f); marginBottom(8f)
                            backgroundColor(colors().bgBase); borderRadius(8f)
                            paddingLeft(10f); paddingRight(10f)
                        }
                        Text { attr { text("🔍"); fontSize(12f); color(colors().labelTertiary); marginRight(6f) } }
                        Input {
                            ref { sheetSearchRef = it; if (sheetOpening) it.view?.setText(sheetKeyword()) }
                            attr { flex(1f); fontSize(13f); color(colors().labelPrimary); placeholder("筛选类型"); placeholderColor(colors().labelTertiary) }
                            event { textDidChange { onSetSheetKeyword(it.text) } }
                        }
                    }
                    View {
                        attr { flex(1f); flexDirectionColumn() }
                        Scroller {
                            attr { absolutePositionAllZero(); showScrollerIndicator(false) }
                            val allTypes = typeOptions().filter { it != "全部" }
                            val kw = sheetKeyword().trim()
                            val filtered = if (kw.isEmpty()) allTypes else allTypes.filter { it.contains(kw, ignoreCase = true) }
                            val groups = filtered.groupBy { it.substringBefore(".") }
                            for ((prefix, types) in groups) {

                                View {
                                    attr { flexDirectionRow(); alignItemsCenter(); paddingLeft(16f); paddingRight(16f); paddingTop(10f); paddingBottom(6f) }
                                    Text { attr { text(prefix + " (" + types.size + ")"); fontSize(12f); fontWeightBold(); color(colors().labelSecondary); flex(1f) } }
                                    View {
                                        event { click { val selAll = !types.all { it in selectedTypes() }; types.forEach { if (selAll && it !in selectedTypes()) onToggleType(it); else if (!selAll && it in selectedTypes()) onToggleType(it) } } }
                                        Text { attr { text(if (types.all { it in selectedTypes() }) "取消全选" else "全选"); fontSize(12f); color(colors().stateBusinessPrimary) } }
                                    }
                                }
                                for (t in types) {

                                    View {
                                        attr {
                                            flexDirectionRow(); alignItemsCenter()
                                            paddingLeft(24f); paddingRight(16f); paddingTop(8f); paddingBottom(8f)
                                        }
                                        event { click { onToggleType(t) } }
                                        Text { attr { text(t); fontSize(13f); color(colors().labelPrimary); flex(1f) } }
                                        View {
                                            attr {
                                                width(20f); height(20f); borderRadius(4f)
                                                border(Border(1.5f, BorderStyle.SOLID, if (t in selectedTypes()) colors().stateBusinessPrimary else colors().borderL2))
                                                backgroundColor(if (t in selectedTypes()) colors().stateBusinessPrimary else Color(0x00000000))
                                                alignItemsCenter(); justifyContentCenter()
                                            }
                                            vif({ t in selectedTypes() }) { Text { attr { text("✓"); fontSize(12f); fontWeightBold(); color(Color(0xFFFFFFFF)) } } }
                                        }
                                    }
                                }
                            }
                            vif({ filtered.isEmpty() }) {
                                Text { attr { text("无匹配类型"); fontSize(13f); color(colors().labelTertiary); alignSelfCenter(); marginTop(30f) } }
                            }
                        }
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); padding(12f); borderTop(Border(0.5f, BorderStyle.SOLID, colors().borderL1)) }
                        View {
                            attr { height(36f); paddingLeft(16f); paddingRight(16f); alignItemsCenter(); justifyContentCenter(); borderRadius(8f) }
                            event { click { onClearTypes() } }
                            Text { attr { text("重置"); fontSize(14f); color(colors().labelTertiary) } }
                        }
                        View { attr { flex(1f) } }
                        View {
                            attr {
                                height(36f); paddingLeft(20f); paddingRight(20f); alignItemsCenter(); justifyContentCenter()
                                borderRadius(8f); backgroundColor(colors().stateBusinessPrimary)
                            }
                            event { click { onSetTypeSheetVisible(false) } }
                            Text { attr { text("应用 (" + selectedTypes().size + ")"); fontSize(14f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
                        }
                    }
                }
            }
        }

        // ===== 清空确认弹窗 =====
        DshSessionLogClearDialog(
            visible = { clearDialogVisible() },
            busy = { clearing() },
            onCancel = { onClearDialogCancel() },
            onConfirm = { onClearDialogConfirm() },
            pageViewWidth = pageViewWidth,
            colors = { colors() },
        )
    }
}

/** 清空本地诊断日志 确认弹窗：只清 dsh_log_events 与内存 pending，不影响会话。 */
internal fun ViewContainer<*, *>.DshSessionLogClearDialog(
    visible: () -> Boolean,
    busy: () -> Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
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
                    width(pageViewWidth - 40f)
                    maxWidth(420f)
                    padding(20f)
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer1)
                }
                Text { attr { text("清空本地诊断日志"); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
                Text {
                    attr {
                        text("将删除手机上的全部本地诊断日志（含所有会话与移动端日志），不影响会话消息、附件和 Host 侧历史。此操作不可恢复。")
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(colors().labelSecondary)
                    }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors().labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "清空中..." else "清空日志"); width(104f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors().stateErrorPrimary) }
                        event { click { if (!busy()) onConfirm() } }
                    }
                }
            }
        }
    }
}/** 重命名会话 弹窗：输入新标题 → session.rename。 */
internal fun ViewContainer<*, *>.DshSessionRenameDialog(
    visible: () -> Boolean,
    draft: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
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
                    width(pageViewWidth - 40f)
                    maxWidth(420f)
                    padding(20f)
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer1)
                }
                Text { attr { text("重命名会话"); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
                Input {
                    attr {
                        height(38f)
                        marginTop(14f)
                        fontSize(14f)
                        placeholder("会话名称")
                        placeholderColor(colors().labelTertiary)
                        text(draft())
                        color(colors().labelPrimary)
                    }
                    event { textDidChange { onDraftChange(it.text) } }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors().stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors().labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "保存中..." else "保存"); width(78f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors().stateBusinessPrimary) }
                        event { click { if (!busy()) onSave() } }
                    }
                }
            }
        }
    }
}

/** 归档会话 确认弹窗 → workspace.archiveSession。 */
internal fun ViewContainer<*, *>.DshSessionArchiveDialog(
    visible: () -> Boolean,
    busy: () -> Boolean,
    error: () -> String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
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
                    width(pageViewWidth - 40f)
                    maxWidth(420f)
                    padding(20f)
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer1)
                }
                Text { attr { text("归档会话?"); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
                Text {
                    attr {
                        text("归档后会话会从主列表隐藏，可稍后在工作区中恢复；不会删除会话或日志。")
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(colors().labelSecondary)
                    }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors().stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors().labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "归档中..." else "确认归档"); width(104f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors().stateBusinessPrimary) }
                        event { click { if (!busy()) onConfirm() } }
                    }
                }
            }
        }
    }
}

/** 删除会话 确认弹窗（danger，红色按钮）→ dsh-session-manager 插件 /delete 端点。 */
internal fun ViewContainer<*, *>.DshSessionDeleteDialog(
    visible: () -> Boolean,
    busy: () -> Boolean,
    error: () -> String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
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
                    width(pageViewWidth - 40f)
                    maxWidth(420f)
                    padding(20f)
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer1)
                }
                Text { attr { text("删除会话?"); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
                Text {
                    attr {
                        text("将永久删除该会话及其消息，此操作不可恢复。删除通过 dsh-session-manager 插件执行，若 Host 未安装该插件将无法完成。")
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(colors().labelSecondary)
                    }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors().stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors().labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "删除中..." else "永久删除"); width(112f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors().stateErrorPrimary) }
                        event { click { if (!busy()) onConfirm() } }
                    }
                }
            }
        }
    }
}

private fun sessionLogLevelLabel(level: LogLevel): String = when (level) {
    LogLevel.DEBUG -> "D"
    LogLevel.INFO -> "I"
    LogLevel.WARN -> "W"
    LogLevel.ERROR -> "E"
}

private fun sessionLogLevelColor(level: LogLevel): Long = when (level) {
    LogLevel.DEBUG -> 0xFF8B939A
    LogLevel.INFO -> 0xFF4176E6
    LogLevel.WARN -> 0xFFDD8629
    LogLevel.ERROR -> 0xFFD25A5A
}

internal fun ViewContainer<*, *>.DshLogTimePickerModal(
    visible: () -> Boolean,
    targetStart: () -> Boolean,
    pkYear: () -> Int,
    pkMonth: () -> Int,
    pkDay: () -> Int,
    pkHour: () -> Int,
    pkMinute: () -> Int,
    customStartMs: () -> Long?,
    customEndMs: () -> Long?,
    onLogCustomTime: (Long?, Long?) -> Unit,
    onSetVisible: (Boolean) -> Unit,
    onSetTargetStart: (Boolean) -> Unit,
    onSetPkValue: (Int, Int) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens,
    pageViewWidth: () -> Float
) {
    fun ViewContainer<*, *>.renderPickerColumns() {
        ScrollPicker(itemList = Array(8) { (pkYear() - 7 + it).toString() }, defaultIndex = 7) {
            attr { itemWidth = 48f; itemHeight = 40f; countPerScreen = 3; itemTextColor = colors().labelPrimary }
            event { scrollEndEvent { v, _ -> onSetPkValue(0, v.toInt()) } }
        }
        ScrollPicker(itemList = Array(12) { (it + 1).toString() }, defaultIndex = pkMonth() - 1) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = colors().labelPrimary }
            event { scrollEndEvent { v, _ -> onSetPkValue(1, v.toInt()) } }
        }
        ScrollPicker(itemList = Array(31) { (it + 1).toString() }, defaultIndex = pkDay() - 1) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = colors().labelPrimary }
            event { scrollEndEvent { v, _ -> onSetPkValue(2, v.toInt()) } }
        }
        ScrollPicker(itemList = Array(24) { (if (it < 10) "0" else "") + it }, defaultIndex = pkHour()) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = colors().labelPrimary }
            event { scrollEndEvent { v, _ -> onSetPkValue(3, v.toInt()) } }
        }
        ScrollPicker(itemList = Array(60) { (if (it < 10) "0" else "") + it }, defaultIndex = pkMinute()) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = colors().labelPrimary }
            event { scrollEndEvent { v, _ -> onSetPkValue(4, v.toInt()) } }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f, right = 0f)
                height(40f)
                backgroundLinearGradient(Direction.TO_BOTTOM, ColorStop(colors().bgLayer1, 0f), ColorStop(Color.TRANSPARENT, 1f))
            }
        }
        View {
            attr {
                absolutePosition(bottom = 0f, left = 0f, right = 0f)
                height(40f)
                backgroundLinearGradient(Direction.TO_TOP, ColorStop(colors().bgLayer1, 0f), ColorStop(Color.TRANSPARENT, 1f))
            }
        }
        View {
            attr {
                absolutePosition(top = 40f, left = 0f, right = 0f)
                height(38f)
                borderTop(Border(1f, BorderStyle.SOLID, colors().borderL1))
                borderBottom(Border(1f, BorderStyle.SOLID, colors().borderL1))
            }
        }
    }

    vif({ visible() }) {
        Modal(inWindow = true) {
            attr {
                absolutePositionAllZero()
                allCenter()
                paddingLeft(20f)
                paddingRight(20f)
                backgroundColor(Color(0x80000000))
            }
            View {
                attr {
                    width(pageViewWidth() - 40f)
                    maxWidth(420f)
                    padding(16f)
                    borderRadius(14f)
                    backgroundColor(colors().bgLayer1)
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    View {
                        attr { flex(1f); alignItemsCenter(); padding(top = 8f, bottom = 8f); borderRadius(8f); backgroundColor(if (targetStart()) colors().specificSelector else Color(0x00000000)) }
                        event { click { onSetTargetStart(true) } }
                        Text { attr { text("开始时间"); fontSize(14f); fontWeightBold(); color(if (targetStart()) colors().labelPrimary else colors().labelTertiary) } }
                    }
                    View {
                        attr { flex(1f); alignItemsCenter(); padding(top = 8f, bottom = 8f); borderRadius(8f); backgroundColor(if (!targetStart()) colors().specificSelector else Color(0x00000000)) }
                        event { click { onSetTargetStart(false) } }
                        Text { attr { text("结束时间"); fontSize(14f); fontWeightBold(); color(if (!targetStart()) colors().labelPrimary else colors().labelTertiary) } }
                    }
                }
                View {
                    attr { flexDirectionRow(); justifyContentCenter(); marginTop(8f); height(120f) }
                    vif({ targetStart() }) {
                        View {
                            attr { flexDirectionRow(); height(120f) }
                            renderPickerColumns()
                        }
                    }
                    vif({ !targetStart() }) {
                        View {
                            attr { flexDirectionRow(); height(120f) }
                            renderPickerColumns()
                        }
                    }
                }
                Text {
                    attr {
                        text((if (targetStart()) customStartMs() else customEndMs())?.let { formatCustomTime(it) } ?: "不限")
                        fontSize(12f); color(colors().labelSecondary); marginTop(6f); textAlignCenter()
                    }
                }
                View {
                    attr { height(40f); marginTop(12f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors().labelTertiary) }
                        event { click { onSetVisible(false) } }
                    }
                    Text {
                        attr { text("确定"); width(78f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); fontWeightBold(); color(colors().stateBusinessPrimary) }
                        event {
                            click {
                                val start = customStartMs()
                                val end = customEndMs()
                                if (targetStart()) {
                                    onLogCustomTime(LogExporter.parseEpoch(pkYear(), pkMonth(), pkDay(), pkHour(), pkMinute()), end)
                                } else {
                                    onLogCustomTime(start, LogExporter.parseEpoch(pkYear(), pkMonth(), pkDay(), pkHour(), pkMinute()))
                                }
                                onSetVisible(false)
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun formatCustomTime(ms: Long): String =
    LogExporter.formatTimestamp(ms).substring(0, 16).replace('T', ' ')

/** 日志详情弹窗：页面级平级 Modal，避免嵌套在日志弹窗内导致 Kuikly 渲染树崩溃 */
internal fun ViewContainer<*, *>.DshLogDetailModal(
    visible: () -> Boolean,
    entry: () -> LogEvent?,
    detailRaw: () -> String,
    allMode: () -> Boolean = { false },
    onClose: () -> Unit,
    onCopy: (LogEvent) -> Unit,
    onJumpToSession: (String) -> Unit = {},
    statusBarHeight: Float,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
        val e = entry() ?: return@vif
        Modal(inWindow = true) {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0x66000000))
            }
            View {
                attr {
                    width(pageViewWidth)
                    absolutePositionAllZero()
                    flexDirectionColumn()
                    paddingTop(18f + statusBarHeight)
                    paddingBottom(10f)
                    paddingLeft(10f)
                    paddingRight(10f)
                }
                View {
                    attr {
                        flex(1f)
                        borderRadius(16f)
                        backgroundColor(colors().bgLayer1)
                        flexDirectionColumn()
                    }
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); paddingLeft(12f); paddingRight(12f); height(44f) }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); width(72f) }
                            event { click { onClose() } }
                            Image {
                                attr { src(ImageUri.commonAssets("chevron-left.svg")); size(16f, 16f); tintColor(colors().stateBusinessPrimary) }
                            }
                            Text { attr { text("返回"); fontSize(14f); color(colors().stateBusinessPrimary); marginLeft(2f) } }
                        }
                        Text {
                            attr {
                                text("日志详情")
                                fontSize(17f)
                                fontWeightBold()
                                color(colors().labelPrimary)
                                flex(1f)
                                textAlignCenter()
                            }
                        }
                        View {
                            attr { flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter(); width(72f) }
                            event { click { onCopy(e) } }
                            Text { attr { text("复制"); fontSize(14f); color(colors().stateBusinessPrimary) } }
                        }
                    }
                    Scroller {
                        attr { flex(1f); marginTop(8f); paddingLeft(16f); paddingRight(16f) }
                        Text { attr { text("时间：" + LogExporter.formatTimestamp(e.timestamp)); fontSize(13f); lineHeight(22f); color(colors().labelSecondary) } }
                        Text { attr { text("序号：" + e.seq); marginTop(2f); fontSize(13f); lineHeight(22f); color(colors().labelSecondary) } }
                        Text { attr { text("级别：" + e.level.name); marginTop(2f); fontSize(13f); lineHeight(22f); color(colors().labelSecondary) } }
                        Text { attr { text("类型：" + e.type); marginTop(2f); fontSize(13f); lineHeight(22f); color(colors().labelSecondary) } }
                        Text { attr { text("会话：" + (e.sessionId ?: "-")); marginTop(2f); fontSize(13f); lineHeight(22f); color(colors().labelSecondary) } }
                        Text { attr { text("RPC：" + (e.rpcId ?: "-")); marginTop(2f); fontSize(13f); lineHeight(22f); color(colors().labelSecondary) } }
                        Text { attr { text("大小：" + e.size + " B"); marginTop(2f); fontSize(13f); lineHeight(22f); color(colors().labelSecondary) } }
                        Text {
                            attr {
                                text("消息：" + e.message)
                                marginTop(10f); fontSize(13f); lineHeight(20f); color(colors().labelPrimary)
                            }
                        }
                        vif({ allMode() && !e.sessionId.isNullOrEmpty() }) {
                            View {
                                attr {
                                    marginTop(16f); height(40f); flexDirectionRow(); alignItemsCenter()
                                    justifyContentCenter(); borderRadius(8f); backgroundColor(colors().stateBusinessPrimary)
                                }
                                event { click { onJumpToSession(e.sessionId!!) } }
                                Text { attr { text("跳回该会话"); fontSize(14f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
                            }
                        }
                        vif({ detailRaw().isNotEmpty() }) {
                            Text { attr { text("原文（已脱敏）"); marginTop(16f); fontSize(13f); fontWeightMedium(); color(colors().labelSecondary) } }
                            Text { attr { text(detailRaw()); marginTop(6f); fontSize(12f); lineHeight(18f); color(colors().labelPrimary) } }
                        }
                        vif({ detailRaw().isEmpty() && e.sessionId != null }) {
                            Text { attr { text("原文不可用：会话事件流仅内存保存，重连或重启后缺失。"); marginTop(14f); fontSize(12f); lineHeight(18f); color(colors().labelTertiary) } }
                        }
                    }
                }
            }
        }
    }
}
