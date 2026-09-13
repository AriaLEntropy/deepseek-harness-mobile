package com.example.dsh.ui.interaction


import com.example.dsh.ui.rendering.DshBottomSheet
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.example.dsh.ui.home.DshHitButton

internal data class DshPermissionOption(
    val value: String,
    val label: String,
    val selected: Boolean = false,
)

// dsh 语义：权限态 → 盾牌图标（read-only=盾牌+对勾，workspace-write=盾牌+铅笔，full-access=盾牌+感叹号）
internal fun dshPermissionIcon(value: String): String = when (value) {
    "read-only" -> "permission-read.svg"
    "danger-full-access" -> "permission-danger.svg"
    "full-access" -> "permission-danger.svg"
    else -> "permission-write.svg"
}

internal fun dshPermissionTint(
    value: String,
    selected: Boolean,
    colors: com.example.dsh.theme.DshColorTokens,
): Color = when {
    value == "danger-full-access" || value == "full-access" -> colors.stateErrorPrimary
    selected -> colors.stateBusinessPrimary
    else -> colors.labelSecondary
}

// 权限选择弹窗（会话开始前，users 在底部工具栏点击权限 chip 打开）。
// 三个圆角卡片：盾牌 svg 在上、权限名在下，选中卡片蓝底高亮 + 右上对勾（cf412 样式）。
internal fun ViewContainer<*, *>.DshPermissionPicker(
    options: () -> ObservableList<DshPermissionOption>,
    onClose: () -> Unit,
    onSelect: (DshPermissionOption) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    DshBottomSheet(
        colors = colors,
        onClose = onClose,
        panelHeight = 240f,
    ) {
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                padding(18f)
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter(); }
                Text {
                    attr {
                        text("选择权限")
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
            }
            // 三个圆角权限卡片：横向一行等宽，svg 在卡内上方、文字在下方
            View {
                attr {
                    marginTop(12f)
                    flexDirectionRow()
                }
                vforIndex({ options() }) { option, index, _ ->
                    View {
                        attr {
                            flex(1f)
                            height(84f)
                            if (index > 0) marginLeft(12f)
                            flexDirectionColumn()
                            alignItemsCenter()
                            justifyContentCenter()
                            borderRadius(14f)
                            border(Border(
                                1f,
                                BorderStyle.SOLID,
                                if (option.selected) {
                                    if (option.value == "danger-full-access") colors().stateErrorPrimary else colors().stateBusinessPrimary
                                } else colors().borderL2,
                            ))
                            backgroundColor(
                                if (option.selected) {
                                    if (option.value == "danger-full-access") colors().interactiveBgHoverDanger else colors().stateBusinessTertiary
                                } else colors().bgBase,
                            )
                        }
                        View {
                            attr { size(22f, 22f); allCenter() }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets(dshPermissionIcon(option.value)))
                                    size(20f, 20f)
                                    tintColor(dshPermissionTint(option.value, option.selected, colors()))
                                }
                            }
                        }
                        Text {
                            attr {
                                text(option.label)
                                marginTop(6f)
                                fontSize(13f)
                                fontWeightMedium()
                                color(
                                    if (option.selected) {
                                        if (option.value == "danger-full-access") colors().stateErrorPrimary else colors().stateBusinessPrimary
                                    } else colors().labelPrimary
                                )
                            }
                        }
                        if (option.selected) {
                            View {
                                attr {
                                    absolutePosition(top = 8f, left = 8f)
                                    size(20f, 20f)
                                    allCenter()
                                    borderRadius(10f)
                                    backgroundColor(
                                        if (option.value == "danger-full-access") colors().stateErrorPrimary else colors().stateBusinessPrimary
                                    )
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("check.svg"))
                                        size(14f, 14f)
                                        tintColor(Color.WHITE)
                                    }
                                }
                            }
                        }
                        event { click { onSelect(option) } }
                    }
                }
            }
        }
    }
}

// Full access 风险确认弹窗：对齐 dsh 原版 RiskConfirmation（红色警示 icon + 风险文案 +
// 「我已了解风险」勾选 + 取消/启用双按钮），勾选前启用按钮不可点。
internal fun ViewContainer<*, *>.DshRiskConfirmationModal(
    title: String,
    description: String,
    acknowledgeLabel: String,
    cancelLabel: String,
    confirmLabel: String,
    acknowledged: () -> Boolean,
    busy: () -> Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
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
                width(pagerData.pageViewWidth - 40f)
                maxWidth(440f)
                flexDirectionColumn()
                padding(24f)
                borderRadius(18f)
                backgroundColor(colors().bgLayer1)
            }
            View {
                attr { height(32f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(title)
                        flex(1f)
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View {
                    attr { size(32f, 32f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(colors().labelSecondary) } }
                    DshHitButton { if (!busy()) onCancel() }
                }
            }
            View {
                attr {
                    marginTop(16f)
                    flexDirectionRow()
                    alignItemsFlexStart()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("warning-outline.svg"))
                        size(18f, 18f)
                        marginTop(2f)
                        tintColor(colors().stateErrorPrimary)
                    }
                }
                Text {
                    attr {
                        text(description)
                        flex(1f)
                        marginLeft(10f)
                        fontSize(14f)
                        lineHeight(22f)
                        color(colors().labelSecondary)
                    }
                }
            }
            View {
                attr {
                    marginTop(20f)
                    flexDirectionRow()
                    alignItemsFlexStart()
                }
                View {
                    attr {
                        size(18f, 18f)
                        allCenter()
                        borderRadius(4f)
                        border(Border(
                            1.5f,
                            BorderStyle.SOLID,
                            if (acknowledged()) colors().buttonPrimaryFill else colors().borderL2,
                        ))
                        backgroundColor(if (acknowledged()) colors().buttonPrimaryFill else Color(0x00000000))
                    }
                    vif({ acknowledged() }) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets("check.svg"))
                                size(12f, 12f)
                                tintColor(colors().labelPrimaryInverted)
                            }
                        }
                    }
                    DshHitButton { if (!busy()) onAcknowledgedChange(!acknowledged()) }
                }
                Text {
                    attr {
                        text(acknowledgeLabel)
                        flex(1f)
                        marginLeft(10f)
                        fontSize(14f)
                        lineHeight(22f)
                        color(colors().labelPrimary)
                    }
                }
                DshHitButton { if (!busy()) onAcknowledgedChange(!acknowledged()) }
            }
            View {
                attr {
                    marginTop(24f)
                    flexDirectionRow()
                    justifyContentFlexEnd()
                    alignItemsCenter()
                }
                View {
                    attr {
                        height(38f)
                        paddingLeft(16f)
                        paddingRight(16f)
                        allCenter()
                        borderRadius(8f)
                        border(Border(1f, BorderStyle.SOLID, colors().borderL2))
                        backgroundColor(colors().bgLayer1)
                    }
                    Text {
                        attr {
                            text(cancelLabel)
                            fontSize(14f)
                            fontWeightMedium()
                            color(colors().labelPrimary)
                        }
                    }
                    DshHitButton { if (!busy()) onCancel() }
                }
                View {
                    attr {
                        height(38f)
                        marginLeft(12f)
                        paddingLeft(16f)
                        paddingRight(16f)
                        allCenter()
                        borderRadius(8f)
                        backgroundColor(if (acknowledged()) colors().buttonPrimaryFill else colors().buttonPrimaryDimmed)
                    }
                    Text {
                        attr {
                            text(confirmLabel)
                            fontSize(14f)
                            fontWeightMedium()
                            color(if (acknowledged()) colors().labelPrimaryInverted else colors().labelSecondary)
                        }
                    }
                    DshHitButton {
                        if (!busy() && acknowledged()) onConfirm()
                    }
                }
            }
        }
    }
}

internal data class DshAgentModeOption(
    val value: String,
    val label: String,
    val description: String,
    val selected: Boolean = false,
)

// 模式选择弹窗（会话开始前，users 在 Hero 区点击模式 chip 打开）。
// 纵向 list：每项 模式名 + 多行描述，选中项右侧蓝勾（d888 样式）。
internal fun ViewContainer<*, *>.DshAgentModePicker(
    options: () -> ObservableList<DshAgentModeOption>,
    title: () -> String = { "选择模式" },
    onClose: () -> Unit,
    onSelect: (DshAgentModeOption) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    DshBottomSheet(
        colors = colors,
        onClose = onClose,
        largeHeightRatio = 0.62f,
        heightCap = 520f,
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
                        text(title())
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
            }
            Scroller {
                attr { flex(1f); marginTop(8f) }
                vfor({ options() }) { option ->
                    View {
                        attr {
                            minHeight(64f)
                            marginBottom(6f)
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(10f, 12f, 10f, 12f)
                            borderRadius(10f)
                            backgroundColor(if (option.selected) colors().stateBusinessTertiary else colors().bgBase)
                        }
                        View {
                            attr { flex(1f); flexDirectionColumn() }
                            Text {
                                attr {
                                    text(option.label)
                                    fontSize(14f)
                                    fontWeightSemiBold()
                                    color(if (option.selected) colors().stateBusinessPrimary else colors().labelPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text(option.description)
                                    marginTop(4f)
                                    fontSize(12f)
                                    lineHeight(18f)
                                    color(colors().labelTertiary)
                                }
                            }
                        }
                        if (option.selected) {
                            Image {
                                    attr {
                                        src(ImageUri.commonAssets("check.svg"))
                                        size(18f, 18f)
                                        tintColor(colors().stateBusinessPrimary)
                                    }
                                }
                        }
                        event { click { onSelect(option) } }
                    }
                }
            }
        }
    }
}
