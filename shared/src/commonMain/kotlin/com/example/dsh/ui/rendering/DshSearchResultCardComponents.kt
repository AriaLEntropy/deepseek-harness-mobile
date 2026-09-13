package com.example.dsh.ui.rendering

import com.example.dsh.export.DshSearchCard
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.Scroller

internal class DshSearchResultCardAttr : ComposeAttr() {
    var card: com.example.dsh.export.DshSearchCard? by observable(null)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
    var onCopy: (String) -> Unit by observable({})
}

internal class DshSearchResultCardView : ComposeView<DshSearchResultCardAttr, ComposeEvent>() {
    override fun createAttr(): DshSearchResultCardAttr = DshSearchResultCardAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val card = ctx.attr.card ?: return { View { } }
        val shown = if (card.pathsOnly) card.paths.size else card.files.sumOf { it.matches.size }
        val summary = if (card.pathsOnly) {
            if (card.truncated) "显示 $shown / 共 ${card.total} 个路径" else "共 $shown 个路径"
        } else {
            if (card.truncated) "显示 $shown / 共 ${card.total} 处匹配 · ${card.files.size} 个文件"
            else "$shown 处匹配 · ${card.files.size} 个文件"
        }
        val lineHeight = 22f
        val bodyHeight = (card.rowCount * 22f).coerceIn(22f, 200f)
        return {
            View {
                attr {
                    flexDirectionColumn()
                    alignItems(FlexAlign.FLEX_START)
                    marginTop(6f)
                    borderRadius(10f)
                    backgroundColor(ctx.attr.colors.markdownCodeBlock)
                }
                // 横幅：摘要 + 复制（对齐 SearchBlock.header）
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        padding(9f, 14f, 9f, 14f)
                        backgroundColor(ctx.attr.colors.markdownCodeBlockBanner)
                    }
                    Text {
                        attr {
                            text(summary)
                            flex(1f)
                            fontSize(12f)
                            color(ctx.attr.colors.labelSecondary)
                        }
                    }
                    Text {
                        attr {
                            text("复制")
                            fontSize(12f)
                            color(ctx.attr.colors.labelSecondary)
                        }
                        event { click { ctx.attr.onCopy(dshSearchCardCopyText(card)) } }
                    }
                }
                Scroller {
                    attr { height(bodyHeight) }
                    View {
                        attr {
                            flexDirectionColumn()
                            alignItems(FlexAlign.FLEX_START)
                            paddingLeft(14f)
                            paddingRight(14f)
                            paddingTop(8f)
                            paddingBottom(10f)
                        }
                        if (card.pathsOnly) {
                            card.paths.forEach { path ->
                                Text {
                                    attr {
                                        text(path)
                                        fontSize(12f)
                                        lineHeight(lineHeight)
                                        fontFamily("monospace")
                                        color(ctx.attr.colors.labelPrimary)
                                    }
                                }
                            }
                        } else {
                            card.files.forEach { file ->
                                View {
                                    attr {
                                        flexDirectionRow()
                                        alignItems(FlexAlign.FLEX_START)
                                    }
                                    Text {
                                        attr {
                                            text(file.path)
                                            flex(1f)
                                            fontSize(12f)
                                            lineHeight(lineHeight)
                                            fontFamily("monospace")
                                            fontWeightBold()
                                            color(ctx.attr.colors.labelPrimary)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text("${file.matches.size}")
                                            marginLeft(8f)
                                            fontSize(12f)
                                            lineHeight(lineHeight)
                                            color(ctx.attr.colors.labelTertiary)
                                        }
                                    }
                                }
                                file.matches.forEach { match ->
                                    View {
                                        attr {
                                            flexDirectionRow()
                                            alignItems(FlexAlign.FLEX_START)
                                        }
                                        Text {
                                            attr {
                                                text("${match.lineNumber}: ")
                                                fontSize(12f)
                                                lineHeight(lineHeight)
                                                fontFamily("monospace")
                                                color(ctx.attr.colors.labelTertiary)
                                            }
                                        }
                                        Text {
                                            attr {
                                                text(match.line)
                                                flex(1f)
                                                fontSize(12f)
                                                lineHeight(lineHeight)
                                                fontFamily("monospace")
                                                color(ctx.attr.colors.labelPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSearchResultCard(init: DshSearchResultCardView.() -> Unit) {
    addChild(DshSearchResultCardView(), init)
}

/** SearchBlock 的纯文本复制形式（完整结果，与卡片当前展示无关）。 */

private fun dshSearchCardCopyText(card: com.example.dsh.export.DshSearchCard): String =
    if (card.pathsOnly) {
        card.paths.joinToString("\n")
    } else {
        card.files.joinToString("\n\n") { file ->
            (listOf(file.path) + file.matches.map { "${it.lineNumber}: ${it.line}" }).joinToString("\n")
        }
    }

/**
 * 「弹窗查看」底部大弹层：平铺明细、去掉行内灰色容器，内容直接滚动。
 * 与行内展开共用 [DshDisclosureBodyContent]，保证两种方式看到的内容一致。
 */
