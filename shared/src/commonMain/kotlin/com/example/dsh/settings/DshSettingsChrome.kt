package com.example.dsh.settings

import com.example.dsh.theme.DshThemeMode

import com.example.dsh.base.DshBottomSheet
import com.example.dsh.models.DshSettingsChoice
import com.example.dsh.models.DshSettingsSnapshot
import com.example.dsh.ui.rendering.DshProcessDisplayMode
import com.example.dsh.ui.rendering.dshProcessDisplayLabel
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

// ===== 设置页（ds 风格：顶部居中标题 + 右上 ×，分组列表，行右侧当前值） =====
internal fun ViewContainer<*, *>.DshSettingsPage(
    loading: () -> Boolean,
    error: () -> String,
    snapshot: () -> DshSettingsSnapshot,
    isRemoteHost: () -> Boolean,
    connectionModeLabel: () -> String,
    modelsSummary: () -> String,
    hostVersion: () -> String,
    themeMode: () -> DshThemeMode,
    processDisplayMode: () -> com.example.dsh.ui.rendering.DshProcessDisplayMode = { com.example.dsh.ui.rendering.DshProcessDisplayMode.UNIFIED },
    agentPresetLabel: () -> String,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenPersonalization: () -> Unit = {},
    onPickPermission: () -> Unit,
    onPickLocale: () -> Unit,
    onPickTheme: () -> Unit,
    onOpenAgentPresets: () -> Unit,
    onOpenDiagnosticLogs: () -> Unit,
    onOpenPlugins: () -> Unit,
    onDisconnect: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            backgroundColor(colors().bgCanvas)
        }
        View {
            attr {
                height(pagerData.statusBarHeight + 52f)
                paddingTop(pagerData.statusBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(DSH_SETTINGS_SCREEN_MARGIN)
                paddingRight(DSH_SETTINGS_SCREEN_MARGIN - 4f)
                backgroundColor(colors().bgCanvas)
            }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                vif({ loading() }) {
                    Text {
                        attr {
                            text("同步中")
                            fontSize(12f)
                            color(colors().labelTertiary)
                        }
                    }
                }
            }
            Text { attr { text("设置"); fontSize(17f); fontWeightBold(); color(colors().labelPrimary) } }
            View {
                attr { flex(1f); flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter() }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
            }
        }
        Scroller {
            attr {
                flex(1f)
                width(pagerData.pageViewWidth)
                backgroundColor(colors().bgCanvas)
            }
            vif({ !loading() && error().isNotEmpty() }) {
                View {
                    attr {
                        margin(16f)
                        padding(12f)
                        borderRadius(10f)
                        backgroundColor(Color(0xFFFFF7E6))
                        flexDirectionColumn()
                    }
                    Text {
                        attr {
                            text("无法读取电脑端配置（可能未连接或版本过旧）")
                            fontSize(13f)
                            color(Color(0xFF7A5B16))
                        }
                    }
                    Text {
                        attr {
                            text("重试")
                            marginTop(6f)
                            fontSize(13f)
                            fontWeightMedium()
                            color(colors().stateBusinessPrimary)
                        }
                        event { click { onRetry() } }
                    }
                }
            }
            vif({ !loading() && error().isEmpty() && !isRemoteHost() }) {
                Text {
                    attr {
                        text("未连接电脑端 DSH，以下设置无法同步")
                        margin(16f)
                        fontSize(12f)
                        color(colors().labelTertiary)
                    }
                }
            }
            vif({ !loading() && error().isEmpty() && isRemoteHost() && !snapshot().writable }) {
                Text {
                    attr {
                        text("电脑端设置当前为只读，仅可查看")
                        margin(16f)
                        fontSize(12f)
                        color(colors().labelTertiary)
                    }
                }
            }

            // 账户
            DshSettingsCardGroupTitle("账户", colors = colors)
            DshSettingsCard(colors = colors) {
                DshSettingsRow("icon-link16.svg", "连接设置", connectionModeLabel, onOpenConnection, colors = colors, showDivider = true)
                // 与电脑端设置页「模型」板块保持一致：同一图标与名称，进入模型详情页。
                DshSettingsRow("icon-data16.svg", "模型", modelsSummary, onOpenModels, colors = colors, showDivider = false)
            }

            // 权限
            DshSettingsCardGroupTitle("权限", colors = colors)
            DshSettingsCard(colors = colors) {
                DshSettingsRow("permission-write.svg", "工作区权限", { dshSettingsPermissionLabel(snapshot()) }, onPickPermission, colors = colors, showDivider = false)
            }

            // 应用
            DshSettingsCardGroupTitle("应用", colors = colors)
            DshSettingsCard(colors = colors) {
                DshSettingsRow("icon-globe14.svg", "语言", { dshSettingsLocaleLabel(snapshot()) }, onPickLocale, colors = colors, showDivider = true)
                DshSettingsRow("icon-followsystem16.svg", "外观", { dshThemeModeLabel(themeMode()) }, onPickTheme, colors = colors, showDivider = true)
                DshSettingsRow(
                    "personalize.svg",
                    "个性化",
                    { com.example.dsh.ui.rendering.dshProcessDisplayLabel(processDisplayMode()) },
                    onOpenPersonalization,
                    colors = colors,
                    showDivider = true,
                )
                DshSettingsRow("icon-agentpreset16.svg", "Agent 预设", agentPresetLabel, onOpenAgentPresets, colors = colors, showDivider = true)
                DshSettingsRow("log.svg", "日志", { "" }, onOpenDiagnosticLogs, colors = colors, showDivider = true)
                DshSettingsRow("icon-plugin16.svg", "Host 插件", { "配置 / 启停" }, onOpenPlugins, colors = colors, showDivider = false)
            }

            // 关于
            DshSettingsCardGroupTitle("关于", colors = colors)
            DshSettingsCard(colors = colors) {
                DshSettingsRow("icon-refresh16.svg", "电脑端 DSH 版本", hostVersion, {}, colors = colors, showDivider = false)
            }

            // 断开连接：独立卡片，与上方分组保持统一的卡片间距。
            DshSettingsCard(colors = colors, topMargin = DSH_SETTINGS_CARD_GAP) {
                DshSettingsRow("", "断开连接", { "" }, onDisconnect, colors = colors, danger = true, showChevron = false, showDivider = false)
            }
            Text {
                attr {
                    text("设置同步至电脑端 DSH（~/.dsh/settings.yaml）")
                    marginTop(20f)
                    marginBottom(28f)
                    marginLeft(DSH_SETTINGS_SCREEN_MARGIN + DSH_SETTINGS_CARD_PADDING)
                    marginRight(DSH_SETTINGS_SCREEN_MARGIN)
                    fontSize(11f)
                    color(colors().labelTertiary)
                }
            }
        }
    }
}

internal fun dshSettingsPermissionLabel(snapshot: DshSettingsSnapshot): String {
    val current = snapshot.permissionPreset
    if (current.isEmpty()) return "电脑端未提供"
    return snapshot.permissionChoices.firstOrNull { it.value == current }?.label?.ifEmpty { current } ?: current
}

internal fun dshSettingsLocaleLabel(snapshot: DshSettingsSnapshot): String = when (snapshot.localeValue) {
    "zh" -> "简体中文"
    "en" -> "English"
    else -> "跟随电脑端"
}

/** 由页面的 observable 主题模式驱动，颜色不变时也能更新外观文字。 */
internal fun dshThemeModeLabel(mode: DshThemeMode): String = when (mode) {
    DshThemeMode.LIGHT -> "浅色"
    DshThemeMode.DARK -> "深色"
    DshThemeMode.SYSTEM -> "跟随系统"
    DshThemeMode.SUNRISE_SUNSET -> "日出日落"
}


// ===== 设置页卡片规范 =====
// 统一设置页三档间距：卡片到屏幕边缘、卡片内部内容到卡片边缘、卡片与卡片之间。
private const val DSH_SETTINGS_SCREEN_MARGIN = 16f
private const val DSH_SETTINGS_CARD_PADDING = 16f
private const val DSH_SETTINGS_CARD_RADIUS = 12f
private const val DSH_SETTINGS_CARD_GAP = 24f
private const val DSH_SETTINGS_TITLE_GAP = 8f
private const val DSH_SETTINGS_ROW_HEIGHT = 52f

/** 卡片容器：统一卡片底色、圆角与左右外边距，行内自带分隔线。 */
internal fun ViewContainer<*, *>.DshSettingsCard(
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    topMargin: Float = 0f,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            marginTop(topMargin)
            marginLeft(DSH_SETTINGS_SCREEN_MARGIN)
            marginRight(DSH_SETTINGS_SCREEN_MARGIN)
            flexDirectionColumn()
            borderRadius(DSH_SETTINGS_CARD_RADIUS)
            backgroundColor(colors().bgCard)
        }
        content()
    }
}

/** 设置页分组标题：与卡片内图标左边距对齐。 */
internal fun ViewContainer<*, *>.DshSettingsCardGroupTitle(
    title: String,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Text {
        attr {
            text(title)
            marginTop(DSH_SETTINGS_CARD_GAP)
            marginBottom(DSH_SETTINGS_TITLE_GAP)
            marginLeft(DSH_SETTINGS_SCREEN_MARGIN + DSH_SETTINGS_CARD_PADDING)
            fontSize(13f)
            color(colors().labelTertiary)
        }
    }
}

internal fun ViewContainer<*, *>.DshSettingsGroupTitle(title: String, colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light }) {
    Text {
        attr {
            text(title)
            marginTop(20f)
            marginBottom(4f)
            marginLeft(16f)
            fontSize(13f)
            color(colors().labelTertiary)
        }
    }
}

internal fun ViewContainer<*, *>.DshSettingsRow(
    icon: String,
    title: String,
    value: () -> String,
    onClick: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    danger: Boolean = false,
    showChevron: Boolean = true,
    showDivider: Boolean = true,
) {
    View {
        attr {
            height(DSH_SETTINGS_ROW_HEIGHT)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(DSH_SETTINGS_CARD_PADDING)
            paddingRight(DSH_SETTINGS_CARD_PADDING)
            backgroundColor(Color(0x00000000))
        }
        if (icon.isNotEmpty()) {
            Image {
                attr {
                    src(ImageUri.commonAssets(icon))
                    size(20f, 20f)
                    tintColor(if (danger) colors().stateErrorPrimary else colors().labelSecondary)
                }
            }
        }
        Text {
            attr {
                text(title)
                flex(1f)
                if (icon.isNotEmpty()) marginLeft(12f)
                fontSize(14f)
                color(if (danger) colors().stateErrorPrimary else colors().labelPrimary)
            }
        }
        vif({ value().isNotEmpty() }) {
            Text {
                attr {
                    text(value())
                    fontSize(13f)
                    color(colors().labelTertiary)
                }
            }
        }
        if (showChevron) {
            Image {
                attr {
                    src(ImageUri.commonAssets("chevron-right.svg"))
                    size(16f, 16f)
                    marginLeft(6f)
                    tintColor(if (danger) colors().stateErrorPrimary else colors().labelTertiary)
                }
            }
        }
        event { click { onClick() } }
    }
    if (showDivider) {
        View {
            attr {
                height(1f)
                marginLeft(DSH_SETTINGS_CARD_PADDING)
                marginRight(DSH_SETTINGS_CARD_PADDING)
                backgroundColor(colors().borderL2)
            }
        }
    }
}

// 通用选项选择器（设置页底部弹出：权限预设 / 语言 / 外观）
internal fun ViewContainer<*, *>.DshSettingsChoicePicker(
    title: String,
    options: () -> ObservableList<DshSettingsChoice>,
    selectedValue: () -> String,
    busy: () -> Boolean,
    onClose: () -> Unit,
    onSelect: (DshSettingsChoice) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    DshBottomSheet(
        colors = colors,
        onClose = { if (!busy()) onClose() },
        panelHeight = 420f,
    ) {
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                padding(18f)
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(title)
                        fontSize(17f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(colors().labelSecondary) } }
                    event { click { if (!busy()) onClose() } }
                }
            }
            vfor({ options() }) { option ->
                View {
                    attr {
                        height(52f)
                        marginTop(4f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(14f)
                        paddingRight(14f)
                        borderRadius(9f)
                        backgroundColor(if (option.value == selectedValue()) colors().stateBusinessTertiary else Color(0x00FFFFFF))
                    }
                    Text {
                        attr {
                            text(option.label.ifEmpty { option.value })
                            flex(1f)
                            fontSize(14f)
                            color(if (option.value == selectedValue()) colors().stateBusinessPrimary else colors().labelPrimary)
                        }
                    }
                    vif({ option.value == selectedValue() }) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets("check.svg"))
                                size(18f, 18f)
                                tintColor(colors().stateBusinessPrimary)
                            }
                        }
                    }
                    event { click { if (!busy()) onSelect(option) } }
                }
            }
        }
    }
}

// ===== 设置页「个性化」子页面 =====
// 顶部与设置页一致的居中标题 + 右上 ×；下方按「选择规则」分组：
// 「过程展示」为互斥单选，「展开方式」为可叠加开关，接近移动端设置范式。
internal fun ViewContainer<*, *>.DshPersonalizationPage(
    mode: () -> com.example.dsh.ui.rendering.DshProcessDisplayMode,
    expandInModal: () -> Boolean,
    showConnectors: () -> Boolean,
    showResultCards: () -> Boolean,
    onPickMode: (com.example.dsh.ui.rendering.DshProcessDisplayMode) -> Unit,
    onToggleExpandInModal: (Boolean) -> Unit,
    onToggleConnectors: (Boolean) -> Unit,
    onToggleResultCards: (Boolean) -> Unit,
    onClose: () -> Unit,
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
                paddingLeft(12f)
                paddingRight(8f)
                backgroundColor(colors().bgLayer2)
            }
            View { attr { flex(1f) } }
            Text { attr { text("个性化"); fontSize(17f); fontWeightBold(); color(colors().labelPrimary) } }
            View {
                attr { flex(1f); flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter() }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("x.svg"))
                            size(20f, 20f)
                            tintColor(colors().labelSecondary)
                        }
                    }
                    event { click { onClose() } }
                }
            }
        }
        View {
            attr { height(1f); backgroundColor(colors().borderL1) }
        }
        Scroller {
            attr {
                flex(1f)
                width(pagerData.pageViewWidth)
                backgroundColor(colors().bgLayer2)
            }
            DshSettingsGroupTitle("过程展示", colors = colors)
            DshPersonalizationChoiceRow(
                title = "统一折叠",
                subtitle = "把一轮里的思考、工具调用与过程消息折叠成一条摘要（对齐最新 dsh）",
                selected = { mode() == com.example.dsh.ui.rendering.DshProcessDisplayMode.UNIFIED },
                onClick = { onPickMode(com.example.dsh.ui.rendering.DshProcessDisplayMode.UNIFIED) },
                colors = colors,
            )
            DshPersonalizationChoiceRow(
                title = "经典",
                subtitle = "思考与工具调用逐条展开，不做外层统一折叠（对齐电脑端 rc）",
                selected = { mode() == com.example.dsh.ui.rendering.DshProcessDisplayMode.CLASSIC },
                onClick = { onPickMode(com.example.dsh.ui.rendering.DshProcessDisplayMode.CLASSIC) },
                colors = colors,
            )
            DshSettingsGroupTitle("展开方式", colors = colors)
            DshPersonalizationSwitchRow(
                title = "弹窗查看",
                subtitle = "点击展开时从底部弹出，内容平铺滚动，不显示灰色容器",
                checked = { expandInModal() },
                onToggle = onToggleExpandInModal,
                colors = colors,
            )
            DshSettingsGroupTitle("细节", colors = colors)
            DshPersonalizationSwitchRow(
                title = "装饰连接线",
                subtitle = "在连续的工具 / 思考行左侧绘制竖向连接线",
                checked = { showConnectors() },
                onToggle = onToggleConnectors,
                colors = colors,
            )
            DshPersonalizationSwitchRow(
                title = "结果卡片",
                subtitle = "网页检索 / 抓取与 grep / glob 用结构化卡片展示，链接可点击",
                checked = { showResultCards() },
                onToggle = onToggleResultCards,
                colors = colors,
            )
            View { attr { height(32f) } }
        }
    }
}

/** 互斥单选行：标题 + 说明 + 右侧选中态圆勾。 */
internal fun ViewContainer<*, *>.DshPersonalizationChoiceRow(
    title: String,
    subtitle: String,
    selected: () -> Boolean,
    onClick: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            padding(14f, 16f, 14f, 16f)
            backgroundColor(Color(0x00000000))
        }
        View {
            attr { flex(1f); flexDirectionColumn() }
            Text {
                attr {
                    text(title)
                    fontSize(15f)
                    fontWeightMedium()
                    color(if (selected()) colors().stateBusinessPrimary else colors().labelPrimary)
                }
            }
            Text {
                attr {
                    text(subtitle)
                    marginTop(3f)
                    fontSize(12f)
                    lineHeight(18f)
                    color(colors().labelTertiary)
                }
            }
        }
        View {
            attr {
                size(20f, 20f)
                marginLeft(12f)
                borderRadius(10f)
                allCenter()
                border(Border(1.5f, BorderStyle.SOLID, if (selected()) colors().stateBusinessPrimary else colors().borderL2))
                backgroundColor(if (selected()) colors().stateBusinessPrimary else Color(0x00FFFFFF))
            }
            vif({ selected() }) {
                Image {
                    attr {
                        src(ImageUri.commonAssets("check.svg"))
                        size(13f, 13f)
                        tintColor(Color.WHITE)
                    }
                }
            }
        }
        // 全行点击热区置于最上层：避免子 View（圆勾等）截获触摸导致点不动。
        View {
            attr {
                absolutePositionAllZero()
                zIndex(2)
                backgroundColor(Color(0x00000000))
            }
            event { click { onClick() } }
        }
    }
    View {
        attr { height(1f); marginLeft(16f); backgroundColor(colors().borderL2) }
    }
}

/** 开关行：左侧标题 + 说明，右侧自绘 pill 开关。 */
internal fun ViewContainer<*, *>.DshPersonalizationSwitchRow(
    title: String,
    subtitle: String,
    checked: () -> Boolean,
    onToggle: (Boolean) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            padding(14f, 16f, 14f, 16f)
            backgroundColor(Color(0x00000000))
        }
        View {
            attr { flex(1f); flexDirectionColumn() }
            Text {
                attr { text(title); fontSize(15f); fontWeightMedium(); color(colors().labelPrimary) }
            }
            Text {
                attr {
                    text(subtitle)
                    marginTop(3f)
                    fontSize(12f)
                    lineHeight(18f)
                    color(colors().labelTertiary)
                }
            }
        }
        View {
            attr {
                width(46f)
                height(28f)
                marginLeft(12f)
                borderRadius(14f)
                backgroundColor(if (checked()) colors().stateBusinessPrimary else colors().borderL2)
            }
            View {
                attr {
                    positionAbsolute()
                    top(3f)
                    left(if (checked()) 21f else 3f)
                    size(22f, 22f)
                    borderRadius(11f)
                    backgroundColor(Color.WHITE)
                }
            }
        }
        // 全行点击热区置于最上层：整行（含标题）都能切换，且不被开关滑块截获。
        View {
            attr {
                absolutePositionAllZero()
                zIndex(2)
                backgroundColor(Color(0x00000000))
            }
            event { click { onToggle(!checked()) } }
        }
    }
    View {
        attr { height(1f); marginLeft(16f); backgroundColor(colors().borderL2) }
    }
}
