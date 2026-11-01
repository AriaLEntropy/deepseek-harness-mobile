package com.example.dsh.ui.home

import com.example.dsh.theme.DshColorTokens
import com.example.dsh.ui.settings.DshSettingsCard
import com.example.dsh.ui.settings.DshSettingsCardGroupTitle
import com.example.dsh.ui.settings.DshSettingsRow
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 设置页里的电脑列表项；当前单机，后续多 host 直接复用这个结构。 */
internal data class BusSettingsComputer(
    val id: String,
    val name: String,
    val platform: String,
    val online: Boolean,
    val lastSeenAt: String,
    val appVersion: String,
    val platformAsset: String,
)

/**
 * Bus 版设置页：沿用旧设置页的顶栏与卡片规范，
 * 但只依赖 Bus 快照，不再接 DSH Host 的 settings/models/plugins 接口。
 */
internal fun ViewContainer<*, *>.BusSettingsOverlay(
    computers: List<BusSettingsComputer>,
    agents: List<BusHomeAgent>,
    themeModeLabel: () -> String,
    statusBarHeight: Float,
    pageWidth: Float,
    colors: () -> DshColorTokens,
    onClose: () -> Unit,
    onAddComputer: () -> Unit,
    onRenameComputer: (BusSettingsComputer) -> Unit,
    onRemoveComputer: (BusSettingsComputer) -> Unit,
    onPickTheme: () -> Unit,
) {
    // 不要用 Modal(inWindow=true)：主题选择器本身是 in-window Modal，
    // 两层原生窗口会导致选项点击命中错位。设置页用页面内全屏覆盖层即可。
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(colors().bgCanvas)
        }
        View {
            attr {
                height(statusBarHeight + 52f)
                paddingTop(statusBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(16f)
                paddingRight(12f)
                backgroundColor(colors().bgCanvas)
            }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        size(40f, 40f)
                        borderRadius(20f)
                        backgroundColor(colors().bgModulePlatform)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { onClose() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("chevron-left.svg"))
                            size(20f, 20f)
                            tintColor(colors().labelPrimary)
                        }
                    }
                }
            }
            Text {
                attr {
                    text("设置")
                    fontSize(17f)
                    fontWeightBold()
                    color(colors().labelPrimary)
                }
            }
            View { attr { flex(1f); flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter() } }
        }
        Scroller {
            attr {
                flex(1f)
                width(pageWidth)
                backgroundColor(colors().bgCanvas)
            }
            DshSettingsCardGroupTitle("电脑", colors = colors)
            DshSettingsCard(colors = colors) {
                if (computers.isEmpty()) {
                    DshSettingsRow(
                        icon = "computer.svg",
                        title = "还没有连接电脑",
                        value = { "点击添加" },
                        onClick = onAddComputer,
                        colors = colors,
                        showDivider = false,
                    )
                } else {
                    computers.forEach { computer ->
                        BusComputerItem(
                            computer = computer,
                            colors = colors,
                            onRename = { onRenameComputer(computer) },
                            onRemove = { onRemoveComputer(computer) },
                        )
                        View {
                            attr {
                                height(1f)
                                marginLeft(16f)
                                marginRight(16f)
                                backgroundColor(colors().borderL2)
                            }
                        }
                    }
                }
                DshSettingsRow(
                    icon = "plus.svg",
                    title = "添加电脑",
                    value = { "扫码 / SSH" },
                    onClick = onAddComputer,
                    colors = colors,
                    showDivider = false,
                )
            }

            DshSettingsCardGroupTitle("应用", colors = colors)
            DshSettingsCard(colors = colors) {
                DshSettingsRow(
                    icon = "personalize.svg",
                    title = "外观",
                    value = themeModeLabel,
                    onClick = onPickTheme,
                    colors = colors,
                    showDivider = false,
                )
            }

            DshSettingsCardGroupTitle("Agents", colors = colors)
            DshSettingsCard(colors = colors) {
                if (agents.isEmpty()) {
                    DshSettingsRow(
                        icon = "agent-preset.svg",
                        title = "还没有 Agent",
                        value = { "连接后自动出现" },
                        onClick = {},
                        colors = colors,
                        showChevron = false,
                        showDivider = false,
                    )
                } else {
                    agents.forEachIndexed { index, agent ->
                        DshSettingsRow(
                            icon = "agent-preset.svg",
                            title = agent.name.ifBlank { agent.key },
                            value = { agentStatusLabel(agent) },
                            onClick = {},
                            colors = colors,
                            showChevron = false,
                            showDivider = index < agents.lastIndex,
                        )
                    }
                }
            }

            DshSettingsCardGroupTitle("关于", colors = colors)
            DshSettingsCard(colors = colors) {
                DshSettingsRow(
                    icon = "shield.svg",
                    title = "电脑程序版本",
                    value = { computers.firstOrNull()?.appVersion?.ifBlank { "—" } ?: "—" },
                    onClick = {},
                    colors = colors,
                    showChevron = false,
                )
                DshSettingsRow(
                    icon = "computer.svg",
                    title = "平台",
                    value = { platformLabel(computers.firstOrNull()?.platform.orEmpty()) },
                    onClick = {},
                    colors = colors,
                    showChevron = false,
                    showDivider = false,
                )
            }
            View { attr { height(24f) } }
        }
    }
}

private fun ViewContainer<*, *>.BusComputerItem(
    computer: BusSettingsComputer,
    colors: () -> DshColorTokens,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    View {
        attr {
            flexDirectionColumn()
            paddingLeft(16f)
            paddingRight(16f)
            paddingTop(13f)
            paddingBottom(12f)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    size(34f, 34f)
                    borderRadius(17f)
                    backgroundColor(if (colors().isDark) Color(0xFF2A2A31) else Color(0xFFF5F5F5))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets(computer.platformAsset.ifBlank { "computer.svg" }))
                        size(20f, 20f)
                        tintColor(colors().labelSecondary)
                    }
                }
            }
            View {
                attr { flex(1f); flexDirectionColumn(); marginLeft(12f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    View {
                        attr {
                            size(6f, 6f)
                            borderRadius(3f)
                            backgroundColor(
                                if (computer.online) colors().stateSuccessPrimary else colors().labelTertiary,
                            )
                        }
                    }
                    Text {
                        attr {
                            text(computer.name)
                            fontSize(14f)
                            fontWeightBold()
                            color(colors().labelPrimary)
                            marginLeft(6f)
                        }
                    }
                }
                Text {
                    attr {
                        text(
                            if (computer.online) "在线"
                            else "离线 · 上次同步 ${displayTime(computer.lastSeenAt)}",
                        )
                        fontSize(11f)
                        color(colors().labelTertiary)
                        marginTop(3f)
                    }
                }
                Text {
                    attr {
                        val detail = buildString {
                            append(platformLabel(computer.platform))
                            if (computer.appVersion.isNotBlank()) append(" · Agent Host ${computer.appVersion}")
                        }
                        text(detail)
                        fontSize(11f)
                        color(colors().labelTertiary)
                        marginTop(2f)
                    }
                }
            }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(10f)
                paddingLeft(46f)
            }
            Text {
                attr {
                    text("编辑名称")
                    fontSize(13f)
                    fontWeightMedium()
                    color(colors().stateBusinessPrimary)
                }
                event { click { onRename() } }
            }
            Text {
                attr {
                    text("移除连接")
                    fontSize(13f)
                    fontWeightMedium()
                    color(colors().stateErrorPrimary)
                    marginLeft(20f)
                }
                event { click { onRemove() } }
            }
        }
    }
}

private fun agentStatusLabel(agent: BusHomeAgent): String = when {
    !agent.available -> "未安装"
    !agent.enabled -> "已停用"
    agent.status == "degraded" -> "需确认"
    agent.status == "error" -> "异常"
    else -> "可用"
}

private fun platformLabel(platform: String): String = when (platform.lowercase()) {
    "macos", "mac", "darwin" -> "macOS"
    "windows", "win32" -> "Windows"
    "linux" -> "Linux"
    "harmonyos", "ohos" -> "HarmonyOS"
    else -> "未知"
}

// ===========================================================================
// 面板共用外壳（Agent 选择面板 + 设备面板）
//
// 两个面板同构：同一套遮罩、起点、圆角、拖拽条、标题行与滚动区，
// 所以共用外壳放在这里（B 号只拥有 BusAgentSheet / BusDeviceSheet / 本文件）。
// 没复用 ui-kit 的 DshBottomSheet：它把抓握条 40×5、遮罩 50% 黑、
// 设备屏幕圆角、0 -4 16 阴影都写死了，而设计基线逐条给定了不同的值。
// ===========================================================================

/**
 * 面板共用的尺寸与颜色（视觉规范 16.8）。
 *
 * 只放 [BusV86Tokens] 里没收录、又确实两个面板都要用的值；
 * 已经有的语义色（sheet/scrim/sheetBorder/sheetShadow/cardFill/field/chipFill…）
 * 一律直接用 [BusV86Tokens]，这里不重复定义。
 */
internal object BusSheetTokens {
    /** 行卡圆角 14 */
    const val rowRadius: Float = 14f

    /** 行卡描边宽度（基线 1px） */
    const val rowBorderWidth: Float = 1f

    /** 强调描边宽度（当前设备 / 报错行 1.5px） */
    const val rowBorderWidthStrong: Float = 1.5f

    /** 行卡内边距 8 */
    const val rowPaddingVertical: Float = 8f

    /** 行卡内边距 14 */
    const val rowPaddingHorizontal: Float = 14f

    /** 禁用行内边距纵向收为 4 */
    const val rowPaddingDisabledVertical: Float = 4f

    /** 禁用行内边距横向收为 12 */
    const val rowPaddingDisabledHorizontal: Float = 12f

    /** 禁用行透明度 .55 */
    const val rowDisabledOpacity: Float = 0.55f

    /** 行卡下间距 5 */
    const val rowGap: Float = 5f

    /** 行内图标底盘 36 圆 */
    const val rowIconSize: Float = 36f

    /** 行内元素间距 10（对应 CSS gap:10） */
    const val rowGapInner: Float = 10f

    /** 拖拽条所在区域高 6 */
    const val grabberZoneHeight: Float = 6f

    /** 拖拽条 36×4，圆角 2 */
    const val grabberWidth: Float = 36f
    const val grabberHeight: Float = 4f
    const val grabberRadius: Float = 2f

    /** 标题行高 27 */
    const val titleRowHeight: Float = 27f

    /** 标题行左右内边距 20（= [BusV86Tokens.spacePage]，这里单独具名便于阅读） */
    const val titleRowPaddingHorizontal: Float = 20f

    /** 面板右上角关闭按钮 28×28，内 16×16 ✕ */
    const val closeSize: Float = 28f
    const val closeIconSize: Float = 16f

    /** 滚动区左右 padding 16 */
    const val scrollPadding: Float = 16f

    /** 面板上抛阴影：offsetY -12、blur 36 */
    const val shadowOffsetY: Float = -12f
    const val shadowRadius: Float = 36f

    /** 浮层阴影 0 8 24 rgba(17,17,26,.16) */
    const val popupOffsetY: Float = 8f
    const val popupRadius: Float = 14f
    val popupShadow: Color = Color(0x2911111A)

    /** 浮层相对面板的位置：拖拽条 6 + 标题行 27 + 设计稿内 70 */
    const val popupTop: Float = 103f

    /** 浮层左右内边距 12/14 */
    const val popupPaddingVertical: Float = 12f
    const val popupPaddingHorizontal: Float = 14f

    /** 报错行与浮层的红描边 #E5484D / #FDE2E2（BusV86Tokens 只有 #D92D20 文字红） */
    val errorBorder: Color = Color(0xFFE5484D)
    val popupBorder: Color = Color(0xFFFDE2E2)

    /** 拖拽条 #D8D8DE */
    val handle: Color = Color(0xFFD8D8DE)

    /** 展开区分隔线 #F0F0F2（比 [BusV86Tokens.hairline] 更实，设计稿给的是不透明值） */
    val rowDivider: Color = Color(0xFFF0F0F2)

    /** 能力矩阵行高 18 */
    const val capabilityRowHeight: Float = 18f
}

/**
 * 面板高度：设计稿 812 - 起点 432 = 380。
 * 矮屏（横屏/小窗）时按 45% 页高兜底，并且永远不超过页高。
 */
internal fun busSheetHeight(pageHeight: Float): Float {
    val minimum = pageHeight * 0.45f
    return (pageHeight - BusV86Tokens.sheetTop)
        .coerceAtLeast(minimum)
        .coerceAtMost(pageHeight)
        .coerceAtLeast(0f)
}

/** 行卡宽度 = 页宽 - 滚动区左右 padding（设计稿 375 - 32 = 343）。 */
internal fun busSheetRowWidth(pageWidth: Float): Float =
    (pageWidth - BusSheetTokens.scrollPadding * 2f).coerceAtLeast(0f)

/**
 * 行卡底色。
 *
 * v86 基线只有浅色（行卡就是 #FFFFFF），深色只把底色换成面板 `surface`，
 * 免得在深色面板上贴一片纯白；深色对比度复核是后续任务。
 */
internal fun busSheetRowFill(palette: BusV86Palette): Color =
    if (palette.isDark) palette.surface else BusV86Tokens.cardFill

/** 行内 36 圆图标底盘底色，同上只做深浅色切换。 */
internal fun busSheetIconDiscFill(palette: BusV86Palette): Color =
    if (palette.isDark) palette.chipFill else BusV86Tokens.field

/**
 * v86 面板外壳：遮罩 + 贴底面板（拖拽条、标题行、右上角 ✕、隐藏滚动条的滚动区）。
 *
 * 面板贴底摆放，高度取 [busSheetHeight]；在设计画布（812）上正好落在 top 432。
 * 状态与副作用都由调用方持有：这里只在关闭（遮罩点击 / ✕）时回调 [onClose]。
 *
 * @param title 标题行文案
 * @param palette 面板配色（[BusV86Tokens.light] 或深色映射）
 * @param pageWidth 页面宽度，用于算面板宽度与行宽
 * @param pageHeight 页面高度，用于算面板高度
 * @param onClose 关闭回调
 * @param floating 浮在面板上的层（长按 lastError 浮层），绝对定位、盖在内容之上
 * @param content 滚动区内容（行卡按 [busSheetRowWidth] 宽度排版）
 */
internal fun ViewContainer<*, *>.BusSheetPanel(
    title: String,
    palette: BusV86Palette,
    pageWidth: Float,
    pageHeight: Float,
    onClose: () -> Unit,
    floating: ViewContainer<*, *>.() -> Unit = {},
    content: ViewContainer<*, *>.() -> Unit,
) {
    val rowWidth = busSheetRowWidth(pageWidth)
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            backgroundColor(palette.scrim)
        }
        // 整屏点击捕获层：点遮罩关闭；面板挂在它之后，点面板不会走到这里
        View {
            attr { absolutePositionAllZero() }
            event { click { onClose() } }
        }
        View {
            attr {
                positionType(FlexPositionType.ABSOLUTE)
                left(0f)
                bottom(0f)
                width(pageWidth)
                height(busSheetHeight(pageHeight))
                flexDirectionColumn()
                borderRadius(
                    BusV86Tokens.radiusSheet,
                    BusV86Tokens.radiusSheet,
                    0f,
                    0f,
                )
                backgroundColor(palette.sheet)
                boxShadow(
                    BoxShadow(
                        0f,
                        BusSheetTokens.shadowOffsetY,
                        BusSheetTokens.shadowRadius,
                        BusV86Tokens.sheetShadow,
                    ),
                )
            }
            // 面板空白处的点击在这里消费掉，避免穿透到遮罩引起误关闭
            event { click { } }
            // border-top 1px rgba(255,255,255,.85)：左右各内缩一个圆角半径，
            // 否则 1px 高光线会戳出 24 的圆角之外（Kuikly 的 Border 只能四边同值）。
            View {
                attr {
                    alignSelfCenter()
                    width((pageWidth - BusV86Tokens.radiusSheet * 2f).coerceAtLeast(0f))
                    height(1f)
                    backgroundColor(BusV86Tokens.sheetBorder)
                }
            }
            // 拖拽条：6 高区域内居中 36×4
            View {
                attr {
                    height(BusSheetTokens.grabberZoneHeight)
                    alignItemsCenter()
                    justifyContentCenter()
                }
                View {
                    attr {
                        size(BusSheetTokens.grabberWidth, BusSheetTokens.grabberHeight)
                        borderRadius(BusSheetTokens.grabberRadius)
                        backgroundColor(BusSheetTokens.handle)
                    }
                }
            }
            // 标题行：高 27，左标题 15/600，右 28×28 关闭按钮（面板保留 ✕，与审批卡不同）
            View {
                attr {
                    height(BusSheetTokens.titleRowHeight)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(BusSheetTokens.titleRowPaddingHorizontal)
                    paddingRight(BusSheetTokens.titleRowPaddingHorizontal)
                }
                Text {
                    attr {
                        text(title)
                        fontSize(BusV86Tokens.fontSizeTitle)
                        fontWeightBold()
                        color(palette.textPrimary)
                        flex(1f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
                View {
                    attr {
                        size(BusSheetTokens.closeSize, BusSheetTokens.closeSize)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { onClose() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("x.svg"))
                            size(BusSheetTokens.closeIconSize, BusSheetTokens.closeIconSize)
                            tintColor(BusV86Tokens.textTertiary)
                        }
                    }
                }
            }
            // 滚动区：padding 0 16，滚动条必须隐藏，否则展开时整行会左移
            Scroller {
                attr {
                    flex(1f)
                    showScrollerIndicator(false)
                }
                View {
                    attr {
                        width(rowWidth)
                        marginLeft(BusSheetTokens.scrollPadding)
                        flexDirectionColumn()
                    }
                    content.invoke(this)
                }
            }
            floating.invoke(this)
        }
    }
}
