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
 * 但只依赖 Bus 快照，不再接 DSH Host 的 settings/models/plugins 接口
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
    // 两层原生窗口会导致选项点击命中错位。设置页用页面内全屏覆盖层即可
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
                paddingLeft(busHomePx(16f))
                paddingRight(busHomePx(12f))
                backgroundColor(colors().bgCanvas)
            }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        size(busHomePx(40f), busHomePx(40f))
                        borderRadius(busHomePx(20f))
                        backgroundColor(colors().bgModulePlatform)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { onClose() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("chevron-left.svg"))
                            size(busHomePx(20f), busHomePx(20f))
                            tintColor(colors().labelPrimary)
                        }
                    }
                }
            }
            Text {
                attr {
                    text("设置")
                    fontSize(busHomePx(17f))
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
                                marginLeft(busHomePx(16f))
                                marginRight(busHomePx(16f))
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
            View { attr { height(maxOf(24f, pagerData.safeAreaInsets.bottom)) } }
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
            paddingLeft(busHomePx(16f))
            paddingRight(busHomePx(16f))
            paddingTop(busHomePx(13f))
            paddingBottom(busHomePx(12f))
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    size(busHomePx(34f), busHomePx(34f))
                    borderRadius(busHomePx(17f))
                    backgroundColor(if (colors().isDark) Color(0xFF2A2A31) else Color(0xFFF5F5F5))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets(computer.platformAsset.ifBlank { "computer.svg" }))
                        size(busHomePx(20f), busHomePx(20f))
                        tintColor(colors().labelSecondary)
                    }
                }
            }
            View {
                attr { flex(1f); flexDirectionColumn(); marginLeft(busHomePx(12f)) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    View {
                        attr {
                            size(busHomePx(6f), busHomePx(6f))
                            borderRadius(busHomePx(3f))
                            backgroundColor(
                                if (computer.online) colors().stateSuccessPrimary else colors().labelTertiary,
                            )
                        }
                    }
                    Text {
                        attr {
                            text(computer.name)
                            fontSize(busHomePx(14f))
                            fontWeightBold()
                            color(colors().labelPrimary)
                            marginLeft(busHomePx(6f))
                        }
                    }
                }
                Text {
                    attr {
                        text(
                            if (computer.online) "在线"
                            else "离线 · 上次同步 ${displayTime(computer.lastSeenAt)}",
                        )
                        fontSize(busHomePx(11f))
                        color(colors().labelTertiary)
                        marginTop(busHomePx(3f))
                    }
                }
                Text {
                    attr {
                        val detail = buildString {
                            append(platformLabel(computer.platform))
                            if (computer.appVersion.isNotBlank()) append(" · Agent Host ${computer.appVersion}")
                        }
                        text(detail)
                        fontSize(busHomePx(11f))
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
                marginTop(busHomePx(10f))
                paddingLeft(busHomePx(46f))
            }
            Text {
                attr {
                    text("编辑名称")
                    fontSize(busHomePx(13f))
                    fontWeightMedium()
                    color(colors().stateBusinessPrimary)
                }
                event { click { onRename() } }
            }
            Text {
                attr {
                    text("移除连接")
                    fontSize(busHomePx(13f))
                    fontWeightMedium()
                    color(colors().stateErrorPrimary)
                    marginLeft(busHomePx(20f))
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
// 所以共用外壳放在这里（B 号只拥有 BusAgentSheet / BusDeviceSheet / 本文件）
// 没复用 ui-kit 的 DshBottomSheet：它把抓握条 40×5、遮罩 50% 黑、
// 设备屏幕圆角、0 -4 16 阴影都写死了，而设计基线逐条给定了不同的值
// ===========================================================================

/**
 * 面板共用的尺寸与颜色（视觉规范 16.8）
 *
 * 只放 [BusHomeTokens] 里没收录、又确实两个面板都要用的值；
 * 已经有的语义色（sheet/scrim/sheetBorder/sheetShadow/cardFill/field/chipFill…）
 * 一律直接用 [BusHomeTokens]，这里不重复定义
 */
internal object BusSheetTokens {
    /** 行卡圆角 14 */
    val rowRadius: Float get() = busHomePx(14f)

    /** 行卡描边宽度（基线 1px） */
    const val rowBorderWidth: Float = 1f

    /** 强调描边宽度（当前设备 / 报错行 1.5px） */
    val rowBorderWidthStrong: Float get() = busHomePx(1.5f)

    /** 行卡内边距 8 */
    val rowPaddingVertical: Float get() = busHomePx(8f)

    /** 行卡内边距 14 */
    val rowPaddingHorizontal: Float get() = busHomePx(14f)

    /** 禁用行内边距纵向收为 4 */
    val rowPaddingDisabledVertical: Float get() = busHomePx(4f)

    /** 禁用行内边距横向收为 12 */
    val rowPaddingDisabledHorizontal: Float get() = busHomePx(12f)

    /** 禁用行透明度 .55 */
    const val rowDisabledOpacity: Float = 0.55f

    /** 行卡下间距 5 */
    val rowGap: Float get() = busHomePx(5f)

    /** 行内图标底盘 36 圆 */
    val rowIconSize: Float get() = busHomePx(36f)

    /** 行内元素间距 10（对应 CSS gap:10） */
    val rowGapInner: Float get() = busHomePx(10f)

    /** 拖拽条所在区域高 14：比设计稿的 6 再松一点，避免条贴着面板顶边 */
    val grabberZoneHeight: Float get() = busHomePx(14f)

    /** 拖拽条 36×4，圆角 2 */
    val grabberWidth: Float get() = busHomePx(36f)
    val grabberHeight: Float get() = busHomePx(4f)
    val grabberRadius: Float get() = busHomePx(2f)

    /** 标题行高 36：拖拽条下移后标题同步下沉，保持与首行的呼吸感 */
    val titleRowHeight: Float get() = busHomePx(36f)

    /** 标题行左右内边距 20（= [BusHomeTokens.spacePage]，这里单独具名便于阅读） */
    val titleRowPaddingHorizontal: Float get() = busHomePx(20f)

    /** 面板右上角关闭按钮 28×28，内 16×16 ✕ */
    val closeSize: Float get() = busHomePx(28f)
    val closeIconSize: Float get() = busHomePx(16f)

    /** 滚动区左右 padding 16 */
    val scrollPadding: Float get() = busHomePx(16f)

    /** 滚动区与标题行之间的上间距 4 */
    val scrollTopPadding: Float get() = busHomePx(4f)

    /**
     * 滚动区底部余量 16
     *
     * 面板底边停在系统导航栏上方（宿主只做了顶部沉浸式），最后一行贴着这个下沿
     * 会被切成硬边；留 16 让它能滚上来一点，读起来是「面板里还有内容」而不是被截断
     */
    val scrollBottomPadding: Float get() = busHomePx(16f)

    /** 面板上抛阴影：offsetY -12、blur 36 */
    const val shadowOffsetY: Float = -12f
    val shadowRadius: Float get() = busHomePx(36f)

    /** 浮层阴影 0 8 24 rgba(17,17,26,.16) */
    val popupOffsetY: Float get() = busHomePx(8f)
    val popupRadius: Float get() = busHomePx(14f)
    val popupShadow: Color = Color(0x2911111A)

    /** 浮层相对面板的位置：拖拽条 6 + 标题行 27 + 设计稿内 70 */
    val popupTop: Float get() = busHomePx(103f)

    /** 浮层左右内边距 12/14 */
    val popupPaddingVertical: Float get() = busHomePx(12f)
    val popupPaddingHorizontal: Float get() = busHomePx(14f)

    /** 报错行与浮层的红描边 #E5484D / #FDE2E2（BusHomeTokens 只有 #D92D20 文字红） */
    val errorBorder: Color = Color(0xFFE5484D)
    val popupBorder: Color = Color(0xFFFDE2E2)

    /** 拖拽条 #D8D8DE */
    val handle: Color = Color(0xFFD8D8DE)

    /** 展开区分隔线 #F0F0F2（比 [BusHomeTokens.hairline] 更实，设计稿给的是不透明值） */
    val rowDivider: Color = Color(0xFFF0F0F2)

    /** 能力矩阵行高 18 */
    val capabilityRowHeight: Float get() = busHomePx(18f)
}

/**
 * 面板高度：按设计稿的顶边比例（432 / 812）换算，而不是固定 432dp
 *
 * 设计画布 812 高时正好是 812 - 432 = 380；屏幕更高时面板同步下移，
 * 保持「面板占下方约 47%」的比例。矮屏时按 45% 页高兜底，永不超过页高
 */
internal fun busSheetHeight(pageHeight: Float): Float {
    val designTop = pageHeight * BusHomeTokens.sheetTopRatio
    val minimum = pageHeight * 0.45f
    return (pageHeight - designTop)
        .coerceAtLeast(minimum)
        .coerceAtMost(pageHeight)
        .coerceAtLeast(0f)
}

/** 行卡宽度 = 页宽 - 滚动区左右 padding（设计稿 375 - 32 = 343）。 */
internal fun busSheetRowWidth(pageWidth: Float): Float =
    (pageWidth - BusSheetTokens.scrollPadding * 2f).coerceAtLeast(0f)

/**
 * 行卡底色
 *
 * v86 基线只有浅色（行卡就是 #FFFFFF），深色只把底色换成面板 `surface`，
 * 免得在深色面板上贴一片纯白；深色对比度复核是后续任务
 */
internal fun busSheetRowFill(palette: BusHomePalette): Color =
    if (palette.isDark) palette.surface else BusHomeTokens.cardFill

/** 行内 36 圆图标底盘底色，同上只做深浅色切换。 */
internal fun busSheetIconDiscFill(palette: BusHomePalette): Color =
    if (palette.isDark) palette.chipFill else BusHomeTokens.field

/**
 * v86 面板外壳：遮罩 + 贴底面板（拖拽条、标题行、右上角 ✕、隐藏滚动条的滚动区）
 *
 * 面板贴底摆放，高度取 [busSheetHeight]；在设计画布（812）上正好落在 top 432
 * 状态与副作用都由调用方持有：这里只在关闭（遮罩点击 / ✕）时回调 [onClose]
 *
 * @param title 标题行文案
 * @param palette 面板配色（[BusHomeTokens.light] 或深色映射）
 * @param pageWidth 页面宽度，用于算面板宽度与行宽
 * @param pageHeight 页面高度，用于算面板高度
 * @param onClose 关闭回调
 * @param headerAction 标题行右侧、✕ 之前的附加动作（如 Agent 面板的快捷全选），不需要就传 null
 * @param floating 浮在面板上的层（长按 lastError 浮层），绝对定位、盖在内容之上
 * @param content 滚动区内容（行卡按 [busSheetRowWidth] 宽度排版）
 */
internal fun ViewContainer<*, *>.BusSheetPanel(
    title: String,
    palette: BusHomePalette,
    pageWidth: Float,
    pageHeight: Float,
    onClose: () -> Unit,
    headerAction: (ViewContainer<*, *>.() -> Unit)? = null,
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
                    BusHomeTokens.radiusSheet,
                    BusHomeTokens.radiusSheet,
                    0f,
                    0f,
                )
                backgroundColor(palette.sheet)
                boxShadow(
                    BoxShadow(
                        0f,
                        BusSheetTokens.shadowOffsetY,
                        BusSheetTokens.shadowRadius,
                        BusHomeTokens.sheetShadow,
                    ),
                )
            }
            // 面板空白处的点击在这里消费掉，避免穿透到遮罩引起误关闭
            event { click { } }
            // border-top 1px rgba(255,255,255,.85)：左右各内缩一个圆角半径，
            // 否则 1px 高光线会戳出 24 的圆角之外（Kuikly 的 Border 只能四边同值）
            View {
                attr {
                    alignSelfCenter()
                    width((pageWidth - BusHomeTokens.radiusSheet * 2f).coerceAtLeast(0f))
                    height(1f)
                    backgroundColor(BusHomeTokens.sheetBorder)
                }
            }
            // 拖拽条：区域内居中 36×4
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
            // 标题行：左标题 15/600，右 28×28 关闭按钮（面板保留 ✕，与审批卡不同）
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
                        fontSize(BusHomeTokens.fontSizeTitle)
                        fontWeightBold()
                        color(palette.textPrimary)
                        flex(1f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
                // 附加动作：靠右排在 ✕ 之前，自身负责与 ✕ 的间距
                headerAction?.invoke(this)
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
                            tintColor(BusHomeTokens.textTertiary)
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
                        paddingTop(BusSheetTokens.scrollTopPadding)
                        flexDirectionColumn()
                    }
                    content.invoke(this)
                    // 末行下方的余量用占位片而不是 paddingBottom：Scroller 对 padding 的
                    // 内容高度计算在各端不一致，审批列表已经在用同一种尾部占位。
                    // 整屏沉浸之后面板会画到物理底边：余量取设计值与实际导航栏高度的较大者，
                    // 有栏时最后一行不被压在按键/手势条下面，没栏时也不凭空空出一格
                    View {
                        attr {
                            height(maxOf(BusSheetTokens.scrollBottomPadding, pagerData.safeAreaInsets.bottom))
                        }
                    }
                }
            }
            floating.invoke(this)
        }
    }
}
