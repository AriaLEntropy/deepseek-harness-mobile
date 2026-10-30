package com.example.dsh.ui.home

import com.example.dsh.theme.DshColorTokens
import com.example.dsh.ui.rendering.DshBottomSheet
import com.example.dsh.ui.rendering.DshSheetDetent
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 电脑选择弹层（M2首页设备弹窗v45）。
 *
 * 当前协议只持久化一台已配对电脑，所以这里展示当前电脑 + 添加电脑入口，
 * 不做多 host 切换；后续多电脑能力再扩展列表。
 */
internal fun ViewContainer<*, *>.BusDeviceSheet(
    device: BusHomeDevice?,
    pendingCount: Int,
    runningCount: Int,
    contentWidth: Float,
    colors: DshColorTokens,
    onClose: () -> Unit,
    onAddComputer: () -> Unit,
) {
    DshBottomSheet(
        colors = { colors },
        onClose = onClose,
        initialDetent = DshSheetDetent.LARGE,
        // 当前只有一台电脑 + 添加电脑，280dp 比原型四台设备的 380dp 更贴合实际内容。
        panelHeight = 280f,
        panelBackground = { colors.bgBase },
    ) {
        View {
            attr {
                height(42f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(20f)
                paddingRight(20f)
                marginTop(6f)
            }
            Text {
                attr {
                    text("选择电脑")
                    fontSize(15f)
                    fontWeightBold()
                    color(colors.labelPrimary)
                    flex(1f)
                }
            }
            View {
                attr {
                    size(30f, 30f)
                    borderRadius(15f)
                    backgroundColor(busSheetSurface(colors))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { onClose() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("x.svg"))
                        size(13f, 13f)
                        tintColor(colors.labelSecondary)
                    }
                }
            }
        }
        Scroller {
            attr {
                flex(1f)
                marginTop(6f)
                paddingLeft(20f)
                paddingRight(20f)
            }
            View {
                attr {
                    width(contentWidth)
                    flexDirectionColumn()
                }
                BusDeviceCard(
                    device = device,
                    pendingCount = pendingCount,
                    runningCount = runningCount,
                    contentWidth = contentWidth,
                    colors = colors,
                    onClose = onClose,
                )
                View {
                    attr {
                        height(56f)
                        marginTop(10f)
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                        borderRadius(14f)
                        border(Border(1f, BorderStyle.DASHED, colors.borderL1))
                    }
                    event { click { onAddComputer() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("plus.svg"))
                            size(16f, 16f)
                            tintColor(colors.stateBusinessPrimary)
                            marginRight(7f)
                        }
                    }
                    Text {
                        attr {
                            text("添加电脑")
                            fontSize(13f)
                            fontWeightBold()
                            color(colors.stateBusinessPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.BusDeviceCard(
    device: BusHomeDevice?,
    pendingCount: Int,
    runningCount: Int,
    contentWidth: Float,
    colors: DshColorTokens,
    onClose: () -> Unit,
) {
    View {
        attr {
            width(contentWidth)
            height(64f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(14f)
            paddingRight(14f)
            borderRadius(14f)
            backgroundColor(if (device != null) busSelectedBg(colors) else colors.bgBase)
            border(
                Border(
                    1.5f,
                    BorderStyle.SOLID,
                    if (device != null) colors.stateBusinessPrimary else colors.borderL1,
                ),
            )
        }
        if (device != null) event { click { onClose() } }
        View {
            attr {
                size(34f, 34f)
                borderRadius(17f)
                backgroundColor(busSheetSurface(colors))
                alignItemsCenter()
                justifyContentCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(device?.platformAsset ?: "computer.svg"))
                    size(22f, 22f)
                    tintColor(colors.labelSecondary)
                }
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                marginLeft(12f)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        size(6f, 6f)
                        borderRadius(3f)
                        backgroundColor(if (device?.online == true) colors.stateSuccessPrimary else colors.labelTertiary)
                    }
                }
                Text {
                    attr {
                        text(device?.name ?: "未连接电脑")
                        fontSize(14f)
                        fontWeightBold()
                        color(if (device != null) colors.labelPrimary else colors.labelSecondary)
                        marginLeft(6f)
                    }
                }
            }
            Text {
                attr {
                    text(
                        when {
                            device == null -> "在设置里添加电脑"
                            !device.online -> "离线 · 上次同步 ${displayTime(device.lastSeenAt)}"
                            else -> "待处理 $pendingCount · 运行中 $runningCount"
                        },
                    )
                    fontSize(11f)
                    color(colors.labelTertiary)
                    marginTop(3f)
                }
            }
        }
        if (device != null) {
            View {
                attr {
                    backgroundColor(colors.specificSelector)
                    borderRadius(9f)
                    paddingLeft(9f)
                    paddingRight(9f)
                    paddingTop(3f)
                    paddingBottom(3f)
                }
                Text {
                    attr {
                        text("当前")
                        fontSize(11f)
                        fontWeightBold()
                        color(colors.labelPrimary)
                    }
                }
            }
        }
    }
}

private fun busSheetSurface(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF2A2A31) else Color(0xFFF5F5F5)

private fun busSelectedBg(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF24242B) else Color(0xFFFBFAFF)
