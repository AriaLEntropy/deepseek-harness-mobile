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
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Modal
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
    colors: com.example.dsh.theme.DshColorTokens = com.example.dsh.theme.DshDefaultTheme.light,
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
                    border(Border(1f, BorderStyle.SOLID, colors.borderL2))
                    backgroundColor(colors.bgLayer1)
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
                                color(if (item.danger) colors.stateErrorPrimary else colors.labelPrimary)
                            }
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets(item.iconAsset))
                                size(18f, 18f)
                                tintColor(if (item.danger) colors.stateErrorPrimary else colors.labelTertiary)
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
internal fun ViewContainer<*, *>.DshSessionLogModal(
    visible: () -> Boolean,
    events: () -> ObservableList<LogEvent>,
    total: () -> Int,
    selected: () -> LogEvent?,
    detailRaw: () -> String,
    exporting: () -> Boolean,
    timeFilter: () -> Int,
    levelFilter: () -> Set<LogLevel>,
    typeFilter: () -> String,
    typeOptions: () -> ObservableList<String>,
    keyword: () -> String,
    onSelect: (LogEvent?) -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onCopy: (LogEvent) -> Unit,
    onClose: () -> Unit,
    onTimeFilter: (Int) -> Unit,
    onToggleLevel: (LogLevel) -> Unit,
    onTypeFilter: (String) -> Unit,
    onKeyword: (String) -> Unit,
    onClearFilters: () -> Unit,
    statusBarHeight: Float,
    pageViewWidth: Float,
    colors: com.example.dsh.theme.DshColorTokens = com.example.dsh.theme.DshDefaultTheme.light,
) {
    vif({ visible() }) {
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
                    backgroundColor(colors.bgBase)
                }
                vif({ selected() == null }) {
                    // ===== 列表（全屏日志页） =====
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); paddingLeft(12f); paddingRight(12f); height(44f) }
                        // 返回：chevron-left 图标 + 文字
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); width(72f) }
                            event { click { onClose() } }
                            Image {
                                attr { src(ImageUri.commonAssets("chevron-left.svg")); size(16f, 16f); tintColor(colors.stateBusinessPrimary) }
                            }
                            Text {
                                attr { text("返回"); fontSize(14f); color(colors.stateBusinessPrimary); marginLeft(2f) }
                            }
                        }
                        Text {
                            attr {
                                text("会话日志")
                                fontSize(17f)
                                fontWeightBold()
                                color(colors.labelPrimary)
                                flex(1f)
                                textAlignCenter()
                            }
                        }
                        // 导出：右对齐
                        View {
                            attr { flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter(); width(72f) }
                            event { click { if (!exporting()) onExport() } }
                            Text {
                                attr { text(if (exporting()) "导出中..." else "导出"); fontSize(14f); color(colors.stateBusinessPrimary) }
                            }
                        }
                    }
                    // 筛选区（§5.3 四维常显，选值可见）
                    View {
                        attr {
                            marginTop(6f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            paddingTop(8f)
                            paddingBottom(4f)
                        }
                        // 行1：时间范围（分段控制器，选中白底深字）
                        View {
                            attr {
                                marginTop(6f)
                                flexDirectionRow()
                                borderRadius(10f)
                                backgroundColor(Color(0xFFEFF1F4))
                                padding(top = 2f, left = 2f, bottom = 2f, right = 2f)
                            }
                            val timeLabels = listOf("全部", "10分钟", "1小时", "今天")
                            for (i in timeLabels.indices) {
                                vif({ timeFilter() == i }) {
                                    View {
                                        attr {
                                            flex(1f); height(30f); borderRadius(8f)
                                            alignItemsCenter(); justifyContentCenter()
                                            backgroundColor(Color(0xFFFFFFFF))
                                        }
                                        event { click { onTimeFilter(i) } }
                                        Text { attr { text(timeLabels[i]); fontSize(12f); fontWeightBold(); color(Color(0xFF1F2933)) } }
                                    }
                                }
                                vif({ timeFilter() != i }) {
                                    View {
                                        attr {
                                            flex(1f); height(30f); borderRadius(8f)
                                            alignItemsCenter(); justifyContentCenter()
                                            backgroundColor(Color(0x00000000))
                                        }
                                        event { click { onTimeFilter(i) } }
                                        Text { attr { text(timeLabels[i]); fontSize(12f); fontWeightBold(); color(Color(0xFF8B939A)) } }
                                    }
                                }
                            }
                        }
                        // 行2：等级多选（彩色色块，选中实心填充白字）
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); marginTop(10f) }
                            for (lv in LogLevel.entries) {
                                val lvColor = sessionLogLevelColor(lv)
                                vif({ lv in levelFilter() }) {
                                    View {
                                        attr {
                                            marginRight(10f); width(30f); height(30f); borderRadius(15f)
                                            alignItemsCenter(); justifyContentCenter()
                                            border(Border(1f, BorderStyle.SOLID, Color(lvColor)))
                                            backgroundColor(Color(lvColor))
                                        }
                                        event { click { onToggleLevel(lv) } }
                                        Text { attr { text(sessionLogLevelLabel(lv)); fontSize(13f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
                                    }
                                }
                                vif({ lv !in levelFilter() }) {
                                    View {
                                        attr {
                                            marginRight(10f); width(30f); height(30f); borderRadius(15f)
                                            alignItemsCenter(); justifyContentCenter()
                                            border(Border(1f, BorderStyle.SOLID, Color(lvColor)))
                                            backgroundColor(Color(0x00000000))
                                        }
                                        event { click { onToggleLevel(lv) } }
                                        Text { attr { text(sessionLogLevelLabel(lv)); fontSize(13f); fontWeightBold(); color(Color(lvColor)) } }
                                    }
                                }
                            }
                        }
                        // 行3：事件类型（横向滚动 Filter Chip，单选）
                        Scroller {
                            attr { flexDirectionRow(); marginTop(8f); paddingLeft(12f); paddingRight(12f) }
                            vfor({ typeOptions() }) { type ->
                                val sel = if (type == "全部") typeFilter().isEmpty() else typeFilter() == type
                                vif({ sel }) {
                                    View {
                                        attr {
                                            marginRight(8f); padding(left = 12f, right = 12f); height(30f)
                                            borderRadius(15f); alignItemsCenter(); justifyContentCenter()
                                            backgroundColor(Color(0xFF4176E6))
                                        }
                                        event { click { onTypeFilter(if (type == "全部") "" else type) } }
                                        Text { attr { text(type); fontSize(12f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
                                    }
                                }
                                vif({ !sel }) {
                                    View {
                                        attr {
                                            marginRight(8f); padding(left = 12f, right = 12f); height(30f)
                                            borderRadius(15f); alignItemsCenter(); justifyContentCenter()
                                            border(Border(1f, BorderStyle.SOLID, Color(0xFFD9DEE3)))
                                            backgroundColor(Color(0x00000000))
                                        }
                                        event { click { onTypeFilter(if (type == "全部") "" else type) } }
                                        Text { attr { text(type); fontSize(12f); color(Color(0xFF6B7280)) } }
                                    }
                                }
                            }
                        }
                        // 行4：关键词
                        View {
                            attr {
                                height(34f)
                                marginTop(6f)
                                paddingLeft(10f)
                                paddingRight(10f)
                                borderRadius(8f)
                                border(Border(1f, BorderStyle.SOLID, Color(0x24000000)))
                            }
                            Input {
                                attr {
                                    flex(1f)
                                    fontSize(12f)
                                    color(Color(0xFF2C3237))
                                    placeholder("搜索日志内容")
                                    placeholderColor(Color(0xFF98A1A9))
                                    text(keyword())
                                }
                                event { textDidChange { onKeyword(it.text) } }
                            }
                        }
                        View { attr { height(0.5f); marginTop(10f); backgroundColor(Color(0x14000000)) } }
                    }
                    vif({ total() > 0 && total() <= 3 }) {
                        Text {
                            attr {
                                text("仅记录本次连接期间产生的事件；在本会话发送消息或执行任务后，完整事件流将实时写入这里")
                                marginTop(10f)
                                marginLeft(12f)
                                marginRight(12f)
                                fontSize(11f)
                                color(Color(0xFF98A1A9))
                            }
                        }
                    }
                    vif({ total() == 0 }) {
                        Text {
                            attr {
                                text("暂无日志")
                                marginTop(90f)
                                alignSelfCenter()
                                fontSize(14f)
                                color(Color(0xFF98A1A9))
                            }
                        }
                    }
                    vif({ total() > 0 && events().isEmpty() }) {
                        View {
                            attr { flexDirectionColumn(); alignItemsCenter(); marginTop(70f) }
                            Text {
                                attr {
                                    text("无匹配结果")
                                    fontSize(14f)
                                    color(Color(0xFF98A1A9))
                                }
                            }
                            View {
                                attr {
                                    marginTop(12f)
                                    padding(top = 8f, left = 18f, bottom = 8f, right = 18f)
                                    borderRadius(16f)
                                    border(Border(1f, BorderStyle.SOLID, Color(0xFF4176E6)))
                                }
                                event { click { onClearFilters() } }
                                Text {
                                    attr {
                                        text("清除筛选")
                                        fontSize(13f)
                                        color(Color(0xFF4176E6))
                                    }
                                }
                            }
                        }
                    }
                    vif({ events().isNotEmpty() }) {
                        Scroller {
                            attr { flex(1f); marginTop(4f) }
                            vfor({ events() }) { entry ->
                                View {
                                    attr {
                                        minHeight(44f)
                                        flexDirectionRow()
                                        alignItemsCenter()
                                        paddingLeft(12f)
                                        paddingRight(12f)
                                    }
                                    Text {
                                        attr {
                                            text(LogExporter.formatTimestamp(entry.timestamp).substring(11, 23))
                                            width(74f)
                                            fontSize(11f)
                                            color(Color(0xFF8B939A))
                                        }
                                    }
                                    Text {
                                        attr {
                                            text(sessionLogLevelLabel(entry.level))
                                            width(28f)
                                            fontSize(12f)
                                            color(Color(sessionLogLevelColor(entry.level)))
                                        }
                                    }
                                    Text {
                                        attr {
                                            text(entry.type)
                                            width(96f)
                                            fontSize(11f)
                                            color(Color(0xFF4176E6))
                                            lines(1)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text(entry.message)
                                            flex(1f)
                                            marginLeft(6f)
                                            fontSize(12f)
                                            color(Color(0xFF2C3237))
                                            lines(1)
                                        }
                                    }
                                    DshHitButton { onSelect(entry) }
                                }
                            }
                        }
                    }
                }
                vif({ selected() != null }) {
                    // ===== 详情（Chucker 列表到详情；返回保留筛选与列表位置） =====
                    val entry = selected()!!
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); paddingLeft(12f); paddingRight(12f); height(44f) }
                        // 返回：chevron-left 图标 + 文字
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); width(72f) }
                            event { click { onSelect(null) } }
                            Image {
                                attr { src(ImageUri.commonAssets("chevron-left.svg")); size(16f, 16f); tintColor(Color(0xFF4176E6)) }
                            }
                            Text {
                                attr { text("返回"); fontSize(14f); color(Color(0xFF4176E6)); marginLeft(2f) }
                            }
                        }
                        Text {
                            attr {
                                text("日志详情")
                                fontSize(17f)
                                fontWeightBold()
                                color(Color(0xFF1F2933))
                                flex(1f)
                                textAlignCenter()
                            }
                        }
                        // 复制：右对齐
                        View {
                            attr { flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter(); width(72f) }
                            event { click { onCopy(entry) } }
                            Text {
                                attr { text("复制"); fontSize(14f); color(Color(0xFF4176E6)) }
                            }
                        }
                    }
                    Scroller {
                        attr { flex(1f); marginTop(8f); paddingLeft(16f); paddingRight(16f) }
                        Text { attr { text("时间：${LogExporter.formatTimestamp(entry.timestamp)}"); fontSize(13f); lineHeight(22f); color(Color(0xFF68737D)) } }
                        Text { attr { text("序号：${entry.seq}"); marginTop(2f); fontSize(13f); lineHeight(22f); color(Color(0xFF68737D)) } }
                        Text { attr { text("级别：${entry.level.name}"); marginTop(2f); fontSize(13f); lineHeight(22f); color(Color(0xFF68737D)) } }
                        Text { attr { text("类型：${entry.type}"); marginTop(2f); fontSize(13f); lineHeight(22f); color(Color(0xFF68737D)) } }
                        Text { attr { text("会话：${entry.sessionId ?: "-"}"); marginTop(2f); fontSize(13f); lineHeight(22f); color(Color(0xFF68737D)) } }
                        Text { attr { text("RPC：${entry.rpcId ?: "-"}"); marginTop(2f); fontSize(13f); lineHeight(22f); color(Color(0xFF68737D)) } }
                        Text { attr { text("大小：${entry.size} B"); marginTop(2f); fontSize(13f); lineHeight(22f); color(Color(0xFF68737D)) } }
                        Text {
                            attr {
                                text("消息：${entry.message}")
                                marginTop(10f)
                                fontSize(13f)
                                lineHeight(20f)
                                color(Color(0xFF2C3237))
                            }
                        }
                        vif({ detailRaw().isNotEmpty() }) {
                            Text {
                                attr {
                                    text("原文（已脱敏）")
                                    marginTop(16f)
                                    fontSize(13f)
                                    fontWeightMedium()
                                    color(Color(0xFF68737D))
                                }
                            }
                            Text {
                                attr {
                                    text(detailRaw())
                                    marginTop(6f)
                                    fontSize(12f)
                                    lineHeight(18f)
                                    color(Color(0xFF2C3237))
                                }
                            }
                        }
                        vif({ detailRaw().isEmpty() && entry.sessionId != null }) {
                            Text {
                                attr {
                                    text("原文不可用：会话事件流仅内存保留，重连或重启后缺失。")
                                    marginTop(14f)
                                    fontSize(12f)
                                    lineHeight(18f)
                                    color(Color(0xFF8B939A))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 重命名会话 弹窗：输入新标题 → session.rename。 */
internal fun ViewContainer<*, *>.DshSessionRenameDialog(
    visible: () -> Boolean,
    draft: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    pageViewWidth: Float,
    colors: com.example.dsh.theme.DshColorTokens = com.example.dsh.theme.DshDefaultTheme.light,
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
                    backgroundColor(colors.bgLayer1)
                }
                Text { attr { text("重命名会话"); fontSize(18f); fontWeightBold(); color(colors.labelPrimary) } }
                Input {
                    attr {
                        height(38f)
                        marginTop(14f)
                        fontSize(14f)
                        placeholder("会话名称")
                        placeholderColor(colors.labelTertiary)
                        text(draft())
                        color(colors.labelPrimary)
                    }
                    event { textDidChange { onDraftChange(it.text) } }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors.stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors.labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "保存中..." else "保存"); width(78f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors.stateBusinessPrimary) }
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
    colors: com.example.dsh.theme.DshColorTokens = com.example.dsh.theme.DshDefaultTheme.light,
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
                    backgroundColor(colors.bgLayer1)
                }
                Text { attr { text("归档会话?"); fontSize(18f); fontWeightBold(); color(colors.labelPrimary) } }
                Text {
                    attr {
                        text("归档后会话会从主列表隐藏，可稍后在工作区中恢复；不会删除会话或日志。")
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(colors.labelSecondary)
                    }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors.stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors.labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "归档中..." else "确认归档"); width(104f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors.stateBusinessPrimary) }
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
    colors: com.example.dsh.theme.DshColorTokens = com.example.dsh.theme.DshDefaultTheme.light,
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
                    backgroundColor(colors.bgLayer1)
                }
                Text { attr { text("删除会话?"); fontSize(18f); fontWeightBold(); color(colors.labelPrimary) } }
                Text {
                    attr {
                        text("将永久删除该会话及其消息，此操作不可恢复。删除通过 dsh-session-manager 插件执行，若 Host 未安装该插件将无法完成。")
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(colors.labelSecondary)
                    }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors.stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors.labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "删除中..." else "永久删除"); width(112f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors.stateErrorPrimary) }
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
