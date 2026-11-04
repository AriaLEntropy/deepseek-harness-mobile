package com.example.dsh.ui.home

import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 电脑选择面板
 *
 * 当前协议只持久化一台已配对电脑，所以这里就是「当前设备行 + 添加电脑」，
 * 不做多 host 切换；后续多电脑能力直接扩展 [BusDeviceSheet] 的行列表即可
 *
 * 视觉对齐共用外壳 `BusSheetPanel` 与视觉规范 16.8：行卡白底 radius 14、
 * padding 8/14、行间距 5；当前设备 1.5px #1D1B20 描边，其余 1px #EFEFF2；
 * 36 圆底 #F5F5F7 放项目已有的 `platform-*.svg` 单色线性图标；
 * 右侧标签只有「当前 / 在线 / 离线」三种
 */
internal fun ViewContainer<*, *>.BusDeviceSheet(
    device: BusHomeDevice?,
    pendingCount: Int,
    runningCount: Int,
    palette: BusHomePalette,
    pageWidth: Float,
    pageHeight: Float,
    onClose: () -> Unit,
    onAddComputer: () -> Unit,
) {
    val rowWidth = busSheetRowWidth(pageWidth)
    BusSheetPanel(
        title = "选择电脑",
        palette = palette,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        onClose = onClose,
    ) {
        BusDeviceRow(
            device = device,
            pendingCount = pendingCount,
            runningCount = runningCount,
            rowWidth = rowWidth,
            palette = palette,
            // 只有一台电脑：点当前设备就是确认选择，收起面板
            onSelect = onClose,
        )
        BusAddComputerRow(rowWidth = rowWidth, palette = palette, onClick = onAddComputer)
    }
}

/**
 * 把旧主题色映射成面板配色
 *
 * 设计基线只有浅色，所以浅色直接用 [BusHomeTokens.light]；深色沿用同一套结构、
 * 取旧主题里语义最接近的字段，保证面板在深色下不至于花掉
 */
internal fun busSheetPalette(colors: DshColorTokens): BusHomePalette = if (!colors.isDark) {
    BusHomeTokens.light
} else {
    BusHomePalette(
        bgBase = colors.bgCanvas,
        surface = colors.bgModulePlatform,
        sheet = colors.bgModulePlatform,
        scrim = BusHomeTokens.scrim,
        textPrimary = colors.labelPrimary,
        textSecondary = colors.labelSecondary,
        textTertiary = colors.labelTertiary,
        chipFill = colors.borderL1,
        selectedFill = colors.labelPrimary,
        onSelected = colors.labelPrimaryInverted,
        error = colors.stateErrorPrimary,
        errorFill = colors.stateErrorSecondary,
        topFadeStart = BusHomeTokens.topFadeStartDark,
        topFadeMiddle = BusHomeTokens.topFadeMiddleDark,
        topFadeBody = BusHomeTokens.topFadeBodyDark,
        topFadeClear = BusHomeTokens.topFadeClearDark,
        isDark = true,
    )
}

// ---------- 判定与文案映射（纯函数，页面与单测共用） ----------

/** 设备行右侧标签的种类：当前与在线是重色，离线是灰。 */
internal enum class BusDeviceTagKind {
    Current,
    Online,
    Offline,
}

/**
 * 设备行标签：只有「当前 / 在线 / 离线」
 *
 * 当前设备优先（哪怕离线也标「当前」，副行会说清离线状态）；
 * 不是当前但在线 -> 「在线」；其余 -> 「离线」
 */
internal fun busDeviceRowTag(online: Boolean, isCurrent: Boolean): BusDeviceTagKind = when {
    isCurrent -> BusDeviceTagKind.Current
    online -> BusDeviceTagKind.Online
    else -> BusDeviceTagKind.Offline
}

/** 标签文案。 */
internal fun busDeviceTagLabel(kind: BusDeviceTagKind): String = when (kind) {
    BusDeviceTagKind.Current -> "当前"
    BusDeviceTagKind.Online -> "在线"
    BusDeviceTagKind.Offline -> "离线"
}

/**
 * 标签配色：当前与在线 = 11/600 #1D1B20 on #EFEFEA；
 * 离线 = 11/500 #8E8E93 on #EFEFF2
 */
internal fun busDeviceTagColors(kind: BusDeviceTagKind, palette: BusHomePalette): Pair<Color, Color> =
    when (kind) {
        BusDeviceTagKind.Current, BusDeviceTagKind.Online ->
            palette.textPrimary to BusHomeTokens.tagFill

        BusDeviceTagKind.Offline ->
            palette.textTertiary to palette.chipFill
    }

/** 标签文字字重种类（当前/在线 600，离线 500）。 */
internal fun busDeviceTagIsEmphasized(kind: BusDeviceTagKind): Boolean =
    kind != BusDeviceTagKind.Offline

/**
 * 设备行副文案：离线给"上次同步"时间，在线给待处理/运行中计数；
 * 没有配对电脑时给一句明确指引
 */
internal fun busDeviceRowSubtitle(
    device: BusHomeDevice?,
    pendingCount: Int,
    runningCount: Int,
): String = when {
    device == null -> "在设置里添加电脑"
    !device.online -> "离线 · 上次同步 ${displayTime(device.lastSeenAt)}"
    else -> "待处理 $pendingCount · 运行中 $runningCount"
}

/** 只有已配对的电脑行可点（点一下收起面板）；没有电脑时不响应。 */
internal fun busDeviceRowIsSelectable(device: BusHomeDevice?): Boolean = device != null

// ---------- 渲染 ----------

private const val DEVICE_TAG_RADIUS = 9f
private const val DEVICE_TAG_PADDING_VERTICAL = 2f
private const val DEVICE_TAG_PADDING_HORIZONTAL = 8f

/** 行名 14/600（[BusHomeTokens] 只有 13 与 15，这里补一个局部常量） */
private const val DEVICE_ROW_NAME_FONT_SIZE = 14f

/** 平台图标在 36 圆内的可见尺寸（设计稿 padding 7） */
private const val DEVICE_PLATFORM_ICON_SIZE = 22f

private fun ViewContainer<*, *>.BusDeviceRow(
    device: BusHomeDevice?,
    pendingCount: Int,
    runningCount: Int,
    rowWidth: Float,
    palette: BusHomePalette,
    onSelect: () -> Unit,
) {
    val selectable = busDeviceRowIsSelectable(device)
    val tagKind = busDeviceRowTag(online = device?.online == true, isCurrent = selectable)
    val tagColors = busDeviceTagColors(tagKind, palette)
    View {
        attr {
            width(rowWidth)
            marginBottom(BusSheetTokens.rowGap)
            borderRadius(BusSheetTokens.rowRadius)
            backgroundColor(busSheetRowFill(palette))
            border(
                Border(
                    if (selectable) BusSheetTokens.rowBorderWidthStrong else BusSheetTokens.rowBorderWidth,
                    BorderStyle.SOLID,
                    if (selectable) palette.selectedFill else palette.chipFill,
                ),
            )
            paddingTop(BusSheetTokens.rowPaddingVertical)
            paddingBottom(BusSheetTokens.rowPaddingVertical)
            paddingLeft(BusSheetTokens.rowPaddingHorizontal)
            paddingRight(BusSheetTokens.rowPaddingHorizontal)
            opacity(if (selectable) 1f else BusSheetTokens.rowDisabledOpacity)
        }
        if (selectable) event { click { onSelect() } }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    size(BusSheetTokens.rowIconSize, BusSheetTokens.rowIconSize)
                    borderRadius(BusSheetTokens.rowIconSize / 2f)
                    backgroundColor(busSheetIconDiscFill(palette))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets(device?.platformAsset ?: "computer.svg"))
                        size(DEVICE_PLATFORM_ICON_SIZE, DEVICE_PLATFORM_ICON_SIZE)
                        resizeContain()
                        tintColor(palette.textSecondary)
                    }
                }
            }
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    marginLeft(BusSheetTokens.rowGapInner)
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            text(device?.name ?: "未连接电脑")
                            fontSize(DEVICE_ROW_NAME_FONT_SIZE)
                            fontWeightBold()
                            color(if (selectable) palette.textPrimary else palette.textTertiary)
                            flex(1f)
                            lines(1)
                            textOverFlowTail()
                        }
                    }
                    if (selectable) {
                        View {
                            attr {
                                borderRadius(DEVICE_TAG_RADIUS)
                                backgroundColor(tagColors.second)
                                paddingLeft(DEVICE_TAG_PADDING_HORIZONTAL)
                                paddingRight(DEVICE_TAG_PADDING_HORIZONTAL)
                                paddingTop(DEVICE_TAG_PADDING_VERTICAL)
                                paddingBottom(DEVICE_TAG_PADDING_VERTICAL)
                                marginLeft(busHomePx(8f))
                            }
                            Text {
                                attr {
                                    text(busDeviceTagLabel(tagKind))
                                    fontSize(BusHomeTokens.fontSizeCaption)
                                    color(tagColors.first)
                                    if (busDeviceTagIsEmphasized(tagKind)) fontWeightBold() else fontWeightMedium()
                                }
                            }
                        }
                    }
                }
                Text {
                    attr {
                        text(busDeviceRowSubtitle(device, pendingCount, runningCount))
                        fontSize(BusHomeTokens.fontSizeCaption)
                        color(palette.textTertiary)
                        marginTop(2f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
            }
        }
    }
}

/**
 * 添加电脑入口
 *
 * 设计稿面板里没有这一行（原型是四台设备），但它要保留 §6.6 的连接入口，
 * 所以按同一套行卡规范补一条虚线描边的入口
 */
private fun ViewContainer<*, *>.BusAddComputerRow(
    rowWidth: Float,
    palette: BusHomePalette,
    onClick: () -> Unit,
) {
    View {
        attr {
            width(rowWidth)
            marginBottom(BusSheetTokens.rowGap)
            borderRadius(BusSheetTokens.rowRadius)
            backgroundColor(busSheetRowFill(palette))
            border(Border(BusSheetTokens.rowBorderWidth, BorderStyle.DASHED, BusHomeTokens.lineDashed))
            paddingTop(BusSheetTokens.rowPaddingVertical)
            paddingBottom(BusSheetTokens.rowPaddingVertical)
            paddingLeft(BusSheetTokens.rowPaddingHorizontal)
            paddingRight(BusSheetTokens.rowPaddingHorizontal)
        }
        event { click { onClick() } }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    size(BusSheetTokens.rowIconSize, BusSheetTokens.rowIconSize)
                    borderRadius(BusSheetTokens.rowIconSize / 2f)
                    backgroundColor(busSheetIconDiscFill(palette))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("plus.svg"))
                        size(busHomePx(18f), busHomePx(18f))
                        tintColor(palette.textSecondary)
                    }
                }
            }
            Text {
                attr {
                    text("添加电脑")
                    fontSize(DEVICE_ROW_NAME_FONT_SIZE)
                    fontWeightBold()
                    color(palette.textPrimary)
                    marginLeft(BusSheetTokens.rowGapInner)
                }
            }
        }
    }
}
