package com.example.dsh.ui.chat

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 对话页顶栏、三点菜单与滚动玻璃
 *
 * 顶栏只放返回、标题、元信息行与菜单；运行状态只由输入条右下角那颗键表达
 */

/** 头部淡紫氛围：只出现在浅色主题，压在内容下面 */
internal fun ViewContainer<*, *>.BusChatHeaderGlow(palette: BusChatPalette) {
    if (palette.isDark) return
    View {
        attr {
            positionType(FlexPositionType.ABSOLUTE)
            top(busChatPx(0f))
            left(busChatPx(0f))
            right(busChatPx(0f))
            height(busChatPx(220f))
            touchEnable(false)
            backgroundLinearGradient(
                Direction.TO_BOTTOM,
                ColorStop(palette.glowTop, 0f),
                ColorStop(palette.glowMid, 0.42f),
                ColorStop(palette.glowClear, 0.85f),
                ColorStop(palette.glowClear, 1f),
            )
        }
    }
}

/**
 * 顶栏一行：返回 · 标题/元信息 · 三点菜单
 *
 * 固定 42 高（4 上边距 + 38 钮），标题与元信息都在这 38 里排版
 */
internal fun ViewContainer<*, *>.BusChatHeader(
    palette: BusChatPalette,
    title: () -> String,
    hostName: () -> String,
    projectName: () -> String,
    agentName: () -> String,
    avatarAsset: String,
    avatarOnly: Boolean,
    dimmed: () -> Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onAgentTap: () -> Unit,
) {
    View {
        attr {
            positionType(FlexPositionType.ABSOLUTE)
            top(busChatPx(0f))
            left(busChatPx(0f))
            right(busChatPx(0f))
            height(busChatPx(42f))
            flexDirectionRow()
            alignItemsFlexStart()
            paddingLeft(BusChatTokens.pageSide)
            paddingRight(BusChatTokens.pageSide)
            paddingTop(BusChatTokens.navTop)
            opacity(if (dimmed()) 0.4f else 1f)
            touchEnable(!dimmed())
        }
        BusChatIconButton(palette, "chevron-left.svg", busChatPx(17f), onBack)
        View {
            attr {
                flex(1f)
                minWidth(busChatPx(0f))
                flexDirectionColumn()
                paddingTop(busChatPx(2f))
                marginLeft(busChatPx(8f))
                marginRight(busChatPx(8f))
            }
            Text {
                attr {
                    text(title())
                    fontSize(BusChatTokens.bodySize + busChatPx(1f))
                    lineHeight((BusChatTokens.bodySize + busChatPx(1f)) * 1.3f)
                    fontWeightSemiBold()
                    color(palette.ink)
                    letterSpacing(busChatPx(-0.2f))
                    lines(1)
                    textOverFlowTail()
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(busChatPx(4f))
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("computer.svg"))
                        size(BusChatTokens.metaIcon, BusChatTokens.metaIcon)
                        tintColor(palette.mut)
                    }
                }
                Text {
                    attr {
                        text(hostName())
                        fontSize(busChatPx(11f))
                        lineHeight(busChatPx(11f))
                        color(palette.mut)
                        marginLeft(busChatPx(5f))
                        maxWidth(busChatPx(128f))
                        lines(1)
                        textOverFlowTail()
                    }
                }
                Text {
                    attr {
                        text("·")
                        fontSize(busChatPx(11f))
                        lineHeight(busChatPx(11f))
                        color(palette.faint)
                        marginLeft(busChatPx(5f))
                    }
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("folder.svg"))
                        size(BusChatTokens.metaIcon, BusChatTokens.metaIcon)
                        tintColor(palette.mut)
                        marginLeft(busChatPx(5f))
                    }
                }
                Text {
                    attr {
                        text(projectName())
                        fontSize(busChatPx(11f))
                        lineHeight(busChatPx(11f))
                        color(palette.mut)
                        marginLeft(busChatPx(5f))
                        maxWidth(busChatPx(150f))
                        lines(1)
                        textOverFlowTail()
                    }
                }
                BusChatAgentBadge(
                    palette = palette,
                    name = agentName(),
                    avatarAsset = avatarAsset,
                    avatarOnly = avatarOnly,
                    onClick = onAgentTap,
                )
            }
        }
        BusChatIconButton(palette, "more-horizontal.svg", busChatPx(18f), onMenu)
    }
}

private fun ViewContainer<*, *>.BusChatIconButton(
    palette: BusChatPalette,
    asset: String,
    iconSize: Float,
    onClick: () -> Unit,
) {
    View {
        attr {
            size(BusChatTokens.iconButton, BusChatTokens.iconButton)
            borderRadius(BusChatTokens.iconButtonRadius)
            backgroundColor(palette.surface)
            border(Border(1f, BorderStyle.SOLID, palette.surfaceBorder))
            boxShadow(
                BoxShadow(0f, 6f, 16f, BusChatTokens.buttonShadow),
            )
            alignItemsCenter()
            justifyContentCenter()
        }
        event { click { onClick() } }
        Image {
            attr {
                src(ImageUri.commonAssets(asset))
                size(iconSize, iconSize)
                tintColor(palette.ink)
            }
        }
    }
}

/**
 * 元信息行末尾的 Agent 标记
 *
 * 有专属连接器只放 20 圆头像；没有专属连接器放默认头像 + 名字；
 * 电脑没上报 Agent 时页面不挂这个组件
 */
internal fun ViewContainer<*, *>.BusChatAgentBadge(
    palette: BusChatPalette,
    name: String,
    avatarAsset: String,
    avatarOnly: Boolean,
    onClick: () -> Unit,
) {
    if (name.isBlank()) return
    val fallback = avatarAsset.isBlank() || avatarAsset == "agent-default.svg"
    val size = if (avatarOnly && !fallback) BusChatTokens.agentAvatar else BusChatTokens.badgeAvatar
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            borderRadius(busChatPx(999f))
            if (avatarOnly && !fallback) {
                padding(busChatPx(0f))
            } else {
                backgroundColor(palette.surface)
                border(Border(1f, BorderStyle.SOLID, palette.surfaceBorder))
                // 通用样式：头像 + 名字整体居中，左右留一样的 7
                padding(busChatPx(2f), busChatPx(7f), busChatPx(2f), busChatPx(7f))
            }
            marginLeft(busChatPx(5f))
        }
        event { click { onClick() } }
        View {
            attr {
                size(size, size)
                borderRadius(size / 2f)
                backgroundColor(if (fallback) palette.field else palette.surface)
                border(Border(0.5f, BorderStyle.SOLID, palette.surfaceBorder))
                boxShadow(BoxShadow(0f, 1f, 2f, BusChatTokens.buttonShadow))
                alignItemsCenter()
                justifyContentCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(if (fallback) "agent-default.svg" else avatarAsset))
                    size(size, size)
                    resizeCover()
                }
            }
        }
        if (!(avatarOnly && !fallback)) {
            Text {
                attr {
                    text(name)
                    fontSize(busChatPx(10.5f))
                    lineHeight(busChatPx(10.5f))
                    fontWeightSemiBold()
                    color(palette.deep)
                    marginLeft(busChatPx(5f))
                    lines(1)
                }
            }
        }
    }
}

/**
 * 三点菜单：挂在顶栏下 14，宽度 206
 *
 * [top] 由页面给（已经算上状态栏），不是屏幕固定像素，顶栏高度变了它跟着走
 */
internal fun ViewContainer<*, *>.BusChatMenuLayer(
    palette: BusChatPalette,
    top: Float,
    artifactCount: () -> Int,
    canDelete: () -> Boolean,
    busy: () -> Boolean,
    onArtifacts: () -> Unit,
    onOpenOnComputer: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    View {
        attr {
            positionType(FlexPositionType.ABSOLUTE)
            this.top(0f)
            left(busChatPx(0f))
            right(busChatPx(0f))
            bottom(busChatPx(0f))
        }
        // 透明点击层：点菜单外面收起
        View {
            attr {
                positionType(FlexPositionType.ABSOLUTE)
                this.top(0f)
                left(busChatPx(0f))
                right(busChatPx(0f))
                bottom(busChatPx(0f))
            }
            event { click { onDismiss() } }
        }
        View {
            attr {
                positionType(FlexPositionType.ABSOLUTE)
                this.top(top)
                right(BusChatTokens.pageSide)
                width(busChatPx(206f))
                flexDirectionColumn()
                borderRadius(busChatPx(16f))
                padding(busChatPx(5f))
                backgroundColor(palette.surface)
                border(Border(1f, BorderStyle.SOLID, palette.surfaceBorder))
                boxShadow(BoxShadow(0f, 18f, 44f, BusChatTokens.overlayShadow))
            }
            BusChatMenuItem(palette, "file-generic.svg", "产物", count = artifactCount()) {
                onArtifacts()
            }
            BusChatMenuItem(palette, "icon-refresh16.svg", "在电脑上打开") {
                onOpenOnComputer()
            }
            BusChatMenuItem(palette, "archive.svg", "从列表移除") {
                onRemove()
            }
            View {
                attr {
                    height(busChatPx(1f))
                    backgroundColor(palette.line)
                    margin(busChatPx(4f), busChatPx(6f), busChatPx(4f), busChatPx(6f))
                }
            }
            BusChatMenuItem(
                palette = palette,
                asset = "delete.svg",
                label = "永久删除会话",
                danger = true,
                enabled = canDelete() && !busy(),
                onClick = onDelete,
            )
        }
    }
}

private fun ViewContainer<*, *>.BusChatMenuItem(
    palette: BusChatPalette,
    asset: String,
    label: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    count: Int = 0,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(busChatPx(34f))
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(busChatPx(10f))
            paddingRight(busChatPx(10f))
            borderRadius(busChatPx(11f))
            opacity(if (enabled) 1f else 0.4f)
        }
        event { click { if (enabled) onClick() } }
        Image {
            attr {
                src(ImageUri.commonAssets(asset))
                size(busChatPx(16f), busChatPx(16f))
                tintColor(if (danger) palette.bad else palette.deep)
            }
        }
        Text {
            attr {
                text(label)
                fontSize(busChatPx(13f))
                lineHeight(busChatPx(13f))
                color(if (danger) palette.bad else palette.ink2)
                marginLeft(busChatPx(9f))
                flex(1f)
                lines(1)
            }
        }
        if (count > 0) {
            Text {
                attr {
                    text(count.toString())
                    fontSize(busChatPx(11f))
                    color(palette.mut)
                }
            }
        }
    }
}

/**
 * 滚动后的顶栏玻璃板
 *
 * 稿子 `.phone.floatnav .nav` 是「一层渐变 × 一条 mask-image」叠出来的：
 *   background: linear-gradient(180deg, rgba(243,243,246,.9) 0%, .72 46%, .42 70%, .16 88%, 0 100%)
 *   mask-image: linear-gradient(180deg, #000 0, #000 56px, rgba(0,0,0,.62) 78px,
 *                                              rgba(0,0,0,.26) 96px, rgba(0,0,0,.07) 110px, transparent 120px)
 * 元素高 42（顶栏）+ 52（padding-bottom）= 94，mask 在 94 处还没走完（约 .29）。
 * Kuikly 没有 mask-image，所以把两者逐点相乘成一条渐变——原来只搬了渐变那一半，
 * 尾巴比稿子硬，滚起来就是突兀的一层雾。合成结果（px → α，括号里是占 94 的比例）：
 *   0(.00)→.90 · 22(.23)→.81 · 43(.46)→.72 · 56(.60)→.55 · 66(.70)→.42
 *   70(.74)→.27 · 78(.83)→.14 · 83(.88)→.08 · 90(.96)→.01 · 94(1.0)→0
 * 顶栏那 42 高保持 .9 左右（正文从它下面穿过时该被压住），后面 52 高一路化到零。
 * Kuikly 没有 backdrop blur，底色带 alpha 近似，边缘不留硬线。
 */
internal fun ViewContainer<*, *>.BusChatGlassBackdrop(
    palette: BusChatPalette,
    visible: () -> Boolean,
) {
    val glass = palette.glassBase
    View {
        attr {
            positionType(FlexPositionType.ABSOLUTE)
            top(busChatPx(0f))
            left(busChatPx(0f))
            right(busChatPx(0f))
            height(BusChatTokens.glassHeight)
            touchEnable(false)
            opacity(if (visible()) 1f else 0f)
            animation(Animation.easeInOut(0.18f), if (visible()) 1f else 0f)
            backgroundLinearGradient(
                Direction.TO_BOTTOM,
                ColorStop(glass.opacity(0.90f), 0f),
                ColorStop(glass.opacity(0.81f), 0.23f),
                ColorStop(glass.opacity(0.72f), 0.46f),
                ColorStop(glass.opacity(0.55f), 0.60f),
                ColorStop(glass.opacity(0.42f), 0.70f),
                ColorStop(glass.opacity(0.27f), 0.74f),
                ColorStop(glass.opacity(0.14f), 0.83f),
                ColorStop(glass.opacity(0.08f), 0.88f),
                ColorStop(glass.opacity(0.01f), 0.96f),
                ColorStop(glass.opacity(0f), 1f),
            )
        }
    }
}

/** 消息区底部 44px 反方向淡出，最后一行不硬撞输入胶囊 */
internal fun ViewContainer<*, *>.BusChatBottomFade(palette: BusChatPalette) {
    View {
        attr {
            positionType(FlexPositionType.ABSOLUTE)
            left(busChatPx(0f))
            right(busChatPx(0f))
            bottom(busChatPx(0f))
            height(BusChatTokens.bottomFade)
            touchEnable(false)
            // 对应 HTML mask: 44px 处 alpha 1 → 26px 处 .62 → 10px 处 .28 → 0
            // 叠加层用相反的 alpha: 0 → .38 → .72 → 1
            backgroundLinearGradient(
                Direction.TO_BOTTOM,
                ColorStop(palette.fade0, 0f),
                ColorStop(palette.fade38, 0.41f),
                ColorStop(palette.fade64, 0.773f),
                ColorStop(palette.fade100, 1f),
            )
        }
    }
}

/** 离开贴底后才挂的「回到底部」 */
internal fun ViewContainer<*, *>.BusChatBackToBottom(
    palette: BusChatPalette,
    bottom: Float,
    visible: () -> Boolean,
    onClick: () -> Unit,
) {
    vif({ visible() }) {
        View {
            attr {
                positionType(FlexPositionType.ABSOLUTE)
                this.bottom(bottom)
                left(busChatPx(0f))
                right(busChatPx(0f))
                alignItemsCenter()
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    borderRadius(busChatPx(999f))
                    padding(busChatPx(5f), busChatPx(11f), busChatPx(5f), busChatPx(11f))
                    backgroundColor(palette.surface)
                    border(Border(1f, BorderStyle.SOLID, palette.surfaceBorder))
                    boxShadow(BoxShadow(0f, 8f, 20f, BusChatTokens.overlayShadow))
                }
                event { click { onClick() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("chevron-down.svg"))
                        size(busChatPx(11f), busChatPx(11f))
                        tintColor(palette.ink2)
                    }
                }
                Text {
                    attr {
                        text("回到底部")
                        fontSize(busChatPx(11.5f))
                        lineHeight(busChatPx(11.5f))
                        fontWeightSemiBold()
                        color(palette.ink2)
                        marginLeft(busChatPx(4f))
                    }
                }
            }
        }
    }
}

/** 轻提示：贴在输入条上方，两秒后由页面清空 */
internal fun ViewContainer<*, *>.BusChatToast(
    palette: BusChatPalette,
    text: () -> String,
    bottom: () -> Float,
) {
    vif({ text().isNotBlank() }) {
        View {
            attr {
                positionType(FlexPositionType.ABSOLUTE)
                this.bottom(bottom())
                left(busChatPx(0f))
                right(busChatPx(0f))
                alignItemsCenter()
                touchEnable(false)
            }
            View {
                attr {
                    padding(busChatPx(8f), busChatPx(13f), busChatPx(8f), busChatPx(13f))
                    borderRadius(busChatPx(999f))
                    backgroundColor(palette.surface)
                    border(Border(1f, BorderStyle.SOLID, palette.surfaceBorder))
                    boxShadow(BoxShadow(0f, 8f, 20f, BusChatTokens.overlayShadow))
                }
                Text {
                    attr {
                        text(text())
                        fontSize(busChatPx(12f))
                        lineHeight(busChatPx(12f))
                        fontWeightSemiBold()
                        color(palette.ink2)
                    }
                }
            }
        }
    }
}
