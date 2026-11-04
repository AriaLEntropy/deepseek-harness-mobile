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
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 添加电脑：只负责选择连接方式，不在这里选 Agent。
 * 扫码直接拉起二维码扫描；SSH 进入已有的 SSH 配置页。
 */
internal fun ViewContainer<*, *>.BusAddComputerSheet(
    busy: Boolean,
    error: String,
    replaceHint: Boolean,
    colors: DshColorTokens,
    onClose: () -> Unit,
    onScan: () -> Unit,
    onSsh: () -> Unit,
) {
    DshBottomSheet(
        colors = { colors },
        onClose = onClose,
        initialDetent = DshSheetDetent.LARGE,
        panelHeight = 360f,
        panelBackground = { colors.bgBase },
    ) {
        View {
            attr {
                height(busHomePx(42f))
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(busHomePx(20f))
                paddingRight(busHomePx(20f))
                marginTop(busHomePx(6f))
            }
            Text {
                attr {
                    text("添加电脑")
                    fontSize(busHomePx(17f))
                    fontWeightBold()
                    color(colors.labelPrimary)
                    flex(1f)
                }
            }
            View {
                attr {
                    size(busHomePx(30f), busHomePx(30f))
                    borderRadius(busHomePx(15f))
                    backgroundColor(sheetSurface(colors))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { if (!busy) onClose() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("x.svg"))
                        size(busHomePx(13f), busHomePx(13f))
                        tintColor(colors.labelSecondary)
                    }
                }
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                paddingLeft(busHomePx(20f))
                paddingRight(busHomePx(20f))
                paddingTop(busHomePx(4f))
            }
            Text {
                attr {
                    text("选择一种方式连接电脑")
                    fontSize(busHomePx(13f))
                    color(colors.labelTertiary)
                    marginBottom(busHomePx(14f))
                }
            }
            AddComputerOption(
                icon = "camera.svg",
                title = if (busy) "正在扫码…" else "扫码添加电脑",
                subtitle = "扫描电脑 Settings > Remote Access 中的二维码",
                colors = colors,
                enabled = !busy,
                onClick = onScan,
            )
            AddComputerOption(
                icon = "tool-terminal.svg",
                title = "用 SSH 连接",
                subtitle = "填写主机、用户名和私钥",
                colors = colors,
                enabled = !busy,
                onClick = onSsh,
                marginTop = 10f,
            )
            if (error.isNotEmpty()) {
                Text {
                    attr {
                        text(error)
                        fontSize(busHomePx(12f))
                        color(colors.stateErrorPrimary)
                        marginTop(busHomePx(10f))
                    }
                }
            }
            View { attr { flex(1f) } }
            if (replaceHint) {
                Text {
                    attr {
                        text("当前版本只保存一台电脑，添加新电脑会替换当前连接。")
                        fontSize(busHomePx(11f))
                        lineHeight(busHomePx(17f))
                        color(colors.labelTertiary)
                        marginBottom(busHomePx(12f))
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AddComputerOption(
    icon: String,
    title: String,
    subtitle: String,
    colors: DshColorTokens,
    enabled: Boolean,
    onClick: () -> Unit,
    marginTop: Float = 0f,
) {
    View {
        attr {
            height(busHomePx(76f))
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(busHomePx(14f))
            paddingRight(busHomePx(14f))
            borderRadius(busHomePx(14f))
            backgroundColor(colors.bgBase)
            border(Border(1f, BorderStyle.SOLID, colors.borderL1))
            opacity(if (enabled) 1f else 0.55f)
            if (marginTop > 0f) this.marginTop(marginTop)
        }
        event { click { if (enabled) onClick() } }
        View {
            attr {
                size(busHomePx(36f), busHomePx(36f))
                borderRadius(busHomePx(18f))
                backgroundColor(sheetSurface(colors))
                alignItemsCenter()
                justifyContentCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(icon))
                    size(busHomePx(19f), busHomePx(19f))
                    tintColor(colors.labelSecondary)
                }
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                marginLeft(busHomePx(12f))
            }
            Text {
                attr {
                    text(title)
                    fontSize(busHomePx(14f))
                    fontWeightBold()
                    color(colors.labelPrimary)
                }
            }
            Text {
                attr {
                    text(subtitle)
                    fontSize(busHomePx(11f))
                    color(colors.labelTertiary)
                    marginTop(busHomePx(4f))
                }
            }
        }
        Image {
            attr {
                src(ImageUri.commonAssets("chevron-right.svg"))
                size(busHomePx(14f), busHomePx(14f))
                tintColor(colors.labelTertiary)
            }
        }
    }
}

private fun sheetSurface(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF2A2A31) else Color(0xFFF5F5F5)
