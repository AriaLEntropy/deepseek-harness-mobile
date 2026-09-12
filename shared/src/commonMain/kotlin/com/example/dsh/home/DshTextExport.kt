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
 * 分享多选态顶部栏：只保留左侧全选/取消全选。
 * 已选计数与关闭动作统一放进底部弹窗，避免与底部重复。
 */
internal fun ViewContainer<*, *>.DshExportSelectionTopBar(
    totalCount: () -> Int,
    allSelected: () -> Boolean,
    onToggleAll: () -> Unit,
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
            attr { height(40f); paddingLeft(14f); paddingRight(14f); allCenter() }
            Text {
                attr {
                    text(if (allSelected() && totalCount() > 0) "取消全选" else "全选")
                    fontSize(15f)
                    color(colors().stateBusinessPrimary)
                }
            }
            event { click { onToggleAll() } }
        }
    }
}

/**
 * 分享多选态底部弹窗：无 mask，占满底部、覆盖主输入框位置。
 * 标题行居中展示已选计数、右侧关闭，下面是文件格式选择与分享动作。
 */
internal fun ViewContainer<*, *>.DshExportSelectionSheet(
    selectedCount: () -> Int,
    format: () -> DshExportFormat,
    onPickFormat: (DshExportFormat) -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    colors: () -> DshColorTokens,
) {
    val canExport = { selectedCount() > 0 }
    View {
        attr {
            flexDirectionColumn()
            paddingLeft(16f)
            paddingRight(16f)
            paddingTop(14f)
            paddingBottom(18f)
            backgroundColor(colors().bgLayer1)
            borderTop(Border(1f, BorderStyle.SOLID, colors().borderL1))
            boxShadow(BoxShadow(0f, -6f, 18f, Color(0x14000000)))
        }
        // 标题行：已选计数居中，右侧关闭
        View {
            attr { height(32f); flexDirectionRow(); alignItemsCenter() }
            View { attr { size(32f, 32f) } }
            View {
                attr { flex(1f); allCenter() }
                Text {
                    attr {
                        text(if (selectedCount() > 0) "已选 ${selectedCount()} 条" else "请选择要分享的消息")
                        fontSize(15f)
                        fontWeightMedium()
                        color(colors().labelPrimary)
                    }
                }
            }
            View {
                attr { size(32f, 32f); allCenter() }
                event { click { onClose() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("x.svg"))
                        size(20f, 20f)
                        tintColor(colors().labelSecondary)
                    }
                }
            }
        }
        Text { attr { text("选择文件格式"); marginTop(14f); fontSize(12f); color(colors().labelTertiary) } }
        View {
            attr { marginTop(8f); flexDirectionRow() }
            DshExportFormatChip(DshExportFormat.TXT, format, onPickFormat, first = true, colors = colors)
            DshExportFormatChip(DshExportFormat.MARKDOWN, format, onPickFormat, first = false, colors = colors)
            DshExportFormatChip(DshExportFormat.HTML, format, onPickFormat, first = false, colors = colors)
        }
        // 分享动作
        View {
            attr {
                height(44f)
                marginTop(16f)
                allCenter()
                borderRadius(10f)
                backgroundColor(if (canExport()) colors().stateBusinessPrimary else colors().stateBusinessTertiary)
            }
            Text {
                attr {
                    text(if (canExport()) "分享 ${selectedCount()} 条" else "请先选择消息")
                    fontSize(15f)
                    fontWeightMedium()
                    color(if (canExport()) colors().labelPrimaryInverted else colors().stateBusinessPrimary)
                }
            }
            event { click { if (canExport()) onConfirm() } }
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
