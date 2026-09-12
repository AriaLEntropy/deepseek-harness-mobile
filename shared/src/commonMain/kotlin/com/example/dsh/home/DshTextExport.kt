package com.example.dsh.home

import com.example.dsh.conversation.DshExportFormat
import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal enum class DshTextExportPhase { IDLE, READING, WRITING, SHARING, READY, FAILED, CANCELLED }

internal data class DshTextExportState(
    val sessionId: String = "",
    val connectionId: String = "",
    val title: String = "",
    val phase: DshTextExportPhase = DshTextExportPhase.IDLE,
    val path: String = "",
    val error: String = "",
) {
    val busy: Boolean get() = phase == DshTextExportPhase.READING || phase == DshTextExportPhase.WRITING || phase == DshTextExportPhase.SHARING
    fun canShare(sessionId: String, connectionId: String): Boolean =
        !busy && path.isNotEmpty() &&
            (phase == DshTextExportPhase.FAILED || phase == DshTextExportPhase.CANCELLED) &&
            this.sessionId == sessionId && this.connectionId == connectionId

    val status: String get() = when (phase) {
        DshTextExportPhase.IDLE -> "准备分享"
        DshTextExportPhase.READING -> "正在读取完整会话…"
        DshTextExportPhase.WRITING -> "正在生成分享文件…"
        DshTextExportPhase.SHARING -> "正在打开系统分享…"
        DshTextExportPhase.READY -> "文本已生成，可通过系统分享保存文件。"
        DshTextExportPhase.FAILED -> if (path.isEmpty()) "分享失败" else "文本已生成，分享未完成"
        DshTextExportPhase.CANCELLED -> "已取消分享"
    }
}

/**
 * 分享多选态顶部栏：左侧全选/取消全选，右侧关闭。
 * 已选计数放进底部弹窗，避免与底部重复。
 */
internal fun ViewContainer<*, *>.DshExportSelectionTopBar(
    totalCount: () -> Int,
    allSelected: () -> Boolean,
    onToggleAll: () -> Unit,
    onClose: () -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr {
            height(58f)
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(colors().bgBase)
            borderBottom(Border(1f, BorderStyle.SOLID, colors().borderL1))
        }
        // 左侧：全选 / 取消全选
        View {
            attr { height(48f); flexDirectionRow(); alignItemsCenter(); paddingLeft(14f); paddingRight(14f) }
            event { click { onToggleAll() } }
            View {
                attr {
                    size(22f, 22f)
                    borderRadius(11f)
                    allCenter()
                    backgroundColor(
                        if (allSelected() && totalCount() > 0) colors().stateBusinessPrimary
                        else Color(0x00FFFFFF)
                    )
                    border(Border(
                        1.5f,
                        BorderStyle.SOLID,
                        if (allSelected() && totalCount() > 0) colors().stateBusinessPrimary
                        else colors().borderL2,
                    ))
                }
                vif({ allSelected() && totalCount() > 0 }) {
                    Image {
                        attr {
                            src(ImageUri.commonAssets("check.svg"))
                            size(14f, 14f)
                            tintColor(Color.WHITE)
                        }
                    }
                }
            }
            Text {
                attr {
                    text(if (allSelected() && totalCount() > 0) "取消全选" else "全选")
                    marginLeft(10f)
                    fontSize(16f)
                    color(colors().labelPrimary)
                }
            }
        }
        View { attr { flex(1f) } }
        // 右侧：关闭多选态
        View {
            attr { size(52f, 58f); allCenter() }
            event { click { onClose() } }
            Image {
                attr {
                    src(ImageUri.commonAssets("x.svg"))
                    size(22f, 22f)
                    tintColor(colors().labelSecondary)
                }
            }
        }
    }
}

/**
 * 分享多选态底部弹窗：无 mask，占满底部、覆盖主输入框位置。
 * 顶部居中展示「已选择 N 组对话」，下方是生成 PDF / 复制内容 / 更多分享三个圆形动作；
 * 点「更多分享」展开文件格式选择与分享按钮。
 */
internal fun ViewContainer<*, *>.DshExportSelectionSheet(
    selectedCount: () -> Int,
    moreExpanded: () -> Boolean,
    format: () -> DshExportFormat,
    pdfBusy: () -> Boolean,
    onPickFormat: (DshExportFormat) -> Unit,
    onGeneratePdf: () -> Unit,
    onCopyContent: () -> Unit,
    onMoreShare: () -> Unit,
    onConfirm: () -> Unit,
    colors: () -> DshColorTokens,
) {
    val canExport = { selectedCount() > 0 }
    View {
        attr {
            flexDirectionColumn()
            paddingLeft(16f)
            paddingRight(16f)
            paddingTop(16f)
            paddingBottom(18f)
            backgroundColor(colors().bgLayer1)
            borderTop(Border(1f, BorderStyle.SOLID, colors().borderL1))
            boxShadow(BoxShadow(0f, -6f, 18f, Color(0x14000000)))
        }
        // 标题：已选组数居中
        View {
            attr { height(26f); flexDirectionRow(); alignItemsCenter() }
            View {
                attr { flex(1f); allCenter() }
                Text {
                    attr {
                        text(if (canExport()) "已选择 ${selectedCount()} 组对话" else "请选择要分享的对话")
                        fontSize(16f)
                        fontWeightMedium()
                        color(colors().labelPrimary)
                    }
                }
            }
        }
        // 三个圆形操作：生成 PDF / 复制内容 / 更多分享
        View {
            attr { marginTop(18f); flexDirectionRow(); justifyContentSpaceBetween(); paddingLeft(18f); paddingRight(18f) }
            DshExportRoundAction(
                label = { if (pdfBusy()) "生成中…" else "生成PDF" },
                iconAsset = "file.svg",
                primary = true,
                enabled = { canExport() && !pdfBusy() },
                onClick = onGeneratePdf,
                colors = colors,
            )
            DshExportRoundAction(
                label = { "复制内容" },
                iconAsset = "copy.svg",
                primary = true,
                enabled = { canExport() },
                onClick = onCopyContent,
                colors = colors,
            )
            DshExportRoundAction(
                label = { "更多分享" },
                iconAsset = "more.svg",
                primary = false,
                enabled = { canExport() },
                onClick = onMoreShare,
                colors = colors,
            )
        }
        // 更多分享：格式选择与分享按钮
        vif({ moreExpanded() }) {
            View {
                attr { flexDirectionColumn(); marginTop(18f) }
                Text { attr { text("选择导出格式"); fontSize(12f); color(colors().labelTertiary) } }
                View {
                    attr { marginTop(8f); flexDirectionRow() }
                    DshExportFormatChip(DshExportFormat.HTML, format, onPickFormat, first = true, colors = colors)
                    DshExportFormatChip(DshExportFormat.MARKDOWN, format, onPickFormat, first = false, colors = colors)
                    DshExportFormatChip(DshExportFormat.TXT, format, onPickFormat, first = false, colors = colors)
                }
                View {
                    attr {
                        height(44f)
                        marginTop(14f)
                        allCenter()
                        borderRadius(10f)
                        backgroundColor(if (canExport()) colors().stateBusinessPrimary else colors().stateBusinessTertiary)
                    }
                    event { click { if (canExport()) onConfirm() } }
                    Text {
                        attr {
                            text("分享文件")
                            fontSize(15f)
                            fontWeightMedium()
                            color(if (canExport()) colors().labelPrimaryInverted else colors().stateBusinessPrimary)
                        }
                    }
                }
                Text {
                    attr {
                        text("分享为 ${format().label}（.${format().extension}），可多选或全选后分享。")
                        marginTop(10f)
                        fontSize(11f)
                        color(colors().labelTertiary)
                    }
                }
            }
        }
    }
}

/** 底部弹窗里的单个圆形动作：主色实心圆 + 白图标，或浅色圆 + 主色图标。 */
internal fun ViewContainer<*, *>.DshExportRoundAction(
    label: () -> String,
    iconAsset: String,
    primary: Boolean,
    enabled: () -> Boolean,
    onClick: () -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr { width(88f); flexDirectionColumn(); alignItemsCenter() }
        event { click { onClick() } }
        View {
            attr {
                size(56f, 56f)
                borderRadius(28f)
                allCenter()
                backgroundColor(
                    if (primary) {
                        if (enabled()) colors().stateBusinessPrimary else colors().stateBusinessTertiary
                    } else {
                        colors().stateBusinessTertiary
                    }
                )
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(iconAsset))
                    size(24f, 24f)
                    tintColor(if (primary && enabled()) Color.WHITE else colors().stateBusinessPrimary)
                }
            }
        }
        Text {
            attr {
                text(label())
                marginTop(8f)
                fontSize(13f)
                color(if (enabled()) colors().labelPrimary else colors().labelTertiary)
            }
        }
    }
}

/** 单个文件格式选项卡片：选中态蓝底描边。 */
internal fun ViewContainer<*, *>.DshExportFormatChip(
    value: DshExportFormat,
    current: () -> DshExportFormat,
    onPick: (DshExportFormat) -> Unit,
    first: Boolean,
    colors: () -> DshColorTokens,
) {
    View {
        attr {
            flex(1f)
            height(50f)
            flexDirectionColumn()
            justifyContentCenter()
            paddingLeft(12f)
            paddingRight(12f)
            if (!first) marginLeft(10f)
            borderRadius(10f)
            border(Border(
                1.5f,
                BorderStyle.SOLID,
                if (current() == value) colors().stateBusinessPrimary else colors().borderL2,
            ))
            backgroundColor(if (current() == value) colors().stateBusinessTertiary else colors().bgBase)
        }
        Text {
            attr {
                text(value.label)
                fontSize(14f)
                fontWeightMedium()
                color(if (current() == value) colors().stateBusinessPrimary else colors().labelPrimary)
            }
        }
        Text {
            attr {
                text(value.description)
                marginTop(3f)
                lines(1)
                fontSize(11f)
                color(colors().labelTertiary)
            }
        }
        event { click { onPick(value) } }
    }
}

internal fun ViewContainer<*, *>.DshTextExportDialog(
    state: () -> DshTextExportState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    colors: () -> DshColorTokens,
) {
    Modal(inWindow = true) {
        attr { absolutePositionAllZero(); allCenter(); padding(20f); backgroundColor(Color(0x66000000)) }
        View {
            attr { width(pagerData.pageViewWidth - 40f); maxWidth(420f); padding(20f); borderRadius(16f); backgroundColor(colors().bgLayer1) }
            Text { attr { text("分享可读文本"); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
            Text { attr { text(state().title); marginTop(10f); lines(2); fontSize(14f); color(colors().labelSecondary) } }
            Text { attr { text(state().status); marginTop(14f); fontSize(14f); color(colors().labelPrimary) } }
            vif({ state().path.isNotEmpty() }) {
                Text { attr { text(state().path.substringAfterLast('/').substringAfterLast('\\')); marginTop(8f); fontSize(12f); color(colors().labelSecondary) } }
            }
            vif({ state().error.isNotEmpty() }) {
                Text { attr { text(state().error); marginTop(8f); fontSize(12f); color(colors().stateErrorPrimary) } }
            }
            View {
                attr { marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                View {
                    attr { height(40f); paddingLeft(12f); paddingRight(12f); allCenter() }
                    Text { attr { text(if (state().busy) "取消分享" else "关闭"); fontSize(14f); color(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
                vif({ !state().busy && state().path.isEmpty() }) {
                    View {
                        attr { height(40f); paddingLeft(12f); paddingRight(12f); allCenter() }
                        Text { attr { text("重试分享"); fontSize(14f); color(colors().stateBusinessPrimary) } }
                        event { click { onRetry() } }
                    }
                }
                vif({ !state().busy && state().path.isNotEmpty() }) {
                    View {
                        attr { height(40f); paddingLeft(12f); paddingRight(12f); allCenter() }
                        Text { attr { text("分享 / 保存"); fontSize(14f); color(colors().stateBusinessPrimary) } }
                        event { click { onShare() } }
                    }
                }
            }
        }
    }
}
