package com.example.dsh.ui.rendering

import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.Scroller

internal class DshTerminalCardAttr : ComposeAttr() {
    var command: String by observable("")
    var output: String by observable("")
    var running: Boolean by observable(false)
    var error: Boolean by observable(false)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
    var onCopy: (String) -> Unit by observable({})
}

internal class DshTerminalCardView : ComposeView<DshTerminalCardAttr, ComposeEvent>() {
    override fun createAttr(): DshTerminalCardAttr = DshTerminalCardAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val hasOutput = ctx.attr.output.isNotBlank()
        val markdownResult = buildString {
            appendLine("### Bash")
            appendLine()
            appendLine("**命令：**")
            appendLine("```bash")
            appendLine(ctx.attr.command)
            appendLine("```")
            if (hasOutput) {
                appendLine()
                appendLine("**输出：**")
                appendLine("```")
                appendLine(ctx.attr.output)
                appendLine("```")
            }
            if (ctx.attr.error) {
                appendLine()
                appendLine("**状态：** 执行失败")
            }
        }
        return {
            View {
                attr {
                    flexDirectionColumn()
                    marginTop(4f)
                    marginLeft(22f)
                    borderRadius(12f)
                    backgroundColor(ctx.attr.colors.markdownCodeBlock)
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItems(FlexAlign.FLEX_START)
                        padding(9f, 14f, 9f, 12f)
                        backgroundColor(ctx.attr.colors.markdownCodeBlockBanner)
                    }
                    View {
                        attr {
                            width(10f)
                            height(10f)
                            borderRadius(5f)
                            backgroundColor(
                                if (ctx.attr.error) {
                                    ctx.attr.colors.stateErrorPrimary
                                } else if (ctx.attr.running) {
                                    ctx.attr.colors.stateBusinessPrimary
                                } else {
                                    ctx.attr.colors.stateSuccessPrimary
                                },
                            )
                            marginTop(6f)
                            marginRight(8f)
                        }
                    }
                    Text {
                        attr {
                            text(ctx.attr.command)
                            fontSize(12f)
                            lineHeight(22f)
                            color(ctx.attr.colors.labelPrimary)
                            fontFamily("monospace")
                            flex(1f)
                        }
                    }
                    View {
                        attr {
                            marginLeft(12f)
                            marginTop(3f)
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("copy.svg"))
                                size(16f, 16f)
                                tintColor(ctx.attr.colors.labelPrimary)
                            }
                        }
                        DshTapTarget {
                            ctx.attr.onCopy(markdownResult)
                        }
                    }
                }
                vif({ hasOutput }) {
                    View {
                        attr {
                            height(1f)
                            marginLeft(12f)
                            marginRight(14f)
                            backgroundColor(ctx.attr.colors.borderL2)
                        }
                    }
                }
                vif({ hasOutput }) {
                    View {
                        attr {
                            padding(12f, 14f, 12f, 30f)
                            flexDirectionColumn()
                        }
                        Scroller {
                            attr {
                                height(150f)
                            }
                            Text {
                                attr {
                                    text(ctx.attr.output)
                                    fontSize(12f)
                                    lineHeight(22f)
                                    color(if (ctx.attr.error) ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.labelSecondary)
                                    fontFamily("monospace")
                                }
                            }
                        }
                    }
                }
                vif({ ctx.attr.running && !hasOutput }) {
                    Text {
                        attr {
                            text("等待工具输出…")
                            fontSize(12f)
                            lineHeight(22f)
                            color(ctx.attr.colors.labelTertiary)
                            marginTop(4f)
                            marginBottom(4f)
                            marginLeft(30f)
                            marginRight(14f)
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshTerminalCard(init: DshTerminalCardView.() -> Unit) {
    addChild(DshTerminalCardView(), init)
}

internal class DshReadCardAttr : ComposeAttr() {
    var filePath: String by observable("")
    var content: String by observable("")
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
    var onCopy: (String) -> Unit by observable({})
}

internal class DshReadCardView : ComposeView<DshReadCardAttr, ComposeEvent>() {
    override fun createAttr(): DshReadCardAttr = DshReadCardAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val lines = ctx.attr.content.lineSequence().toList()
        val lineCount = lines.size
        val displayPath = ctx.attr.filePath.ifEmpty { "文件" }
        return {
            View {
                attr {
                    flexDirectionColumn()
                    marginTop(4f)
                    marginLeft(22f)
                    borderRadius(12f)
                    backgroundColor(ctx.attr.colors.markdownCodeBlock)
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItems(FlexAlign.CENTER)
                        padding(9f, 14f, 9f, 14f)
                        backgroundColor(ctx.attr.colors.markdownCodeBlockBanner)
                    }
                    Text {
                        attr {
                            text(displayPath)
                            fontSize(12f)
                            lineHeight(18f)
                            color(ctx.attr.colors.labelPrimary)
                            fontFamily("monospace")
                            flex(1f)
                            lines(1)
                        }
                    }
                    Text {
                        attr {
                            text("$lineCount 行")
                            fontSize(12f)
                            lineHeight(18f)
                            color(ctx.attr.colors.labelTertiary)
                            marginLeft(12f)
                        }
                    }
                    View {
                        attr {
                            marginLeft(12f)
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("copy.svg"))
                                size(14f, 14f)
                                tintColor(ctx.attr.colors.labelPrimary)
                            }
                        }
                        DshTapTarget {
                            ctx.attr.onCopy(ctx.attr.content)
                        }
                    }
                }
                View {
                    attr {
                        padding(12f, 14f, 12f, 14f)
                    }
                    Scroller {
                        attr {
                            height((lineCount * 22f).coerceAtMost(150f))
                            flex(1f)
                        }
                        Text {
                            attr {
                                text(ctx.attr.content)
                                fontSize(12f)
                                lineHeight(22f)
                                color(ctx.attr.colors.labelPrimary)
                                fontFamily("monospace")
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshReadCard(init: DshReadCardView.() -> Unit) {
    addChild(DshReadCardView(), init)
}

/** Fixed-height scrollable text body used by expandable tool rows. */

internal class DshLongTextView : ComposeView<DshLongTextAttr, ComposeEvent>() {
    override fun createAttr(): DshLongTextAttr = DshLongTextAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionColumn()
                    borderRadius(8f)
                    backgroundColor(ctx.attr.colors.markdownCodeBlock)
                    border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                    padding(8f)
                }
                Scroller {
                    attr {
                        height(if (ctx.attr.maxHeight > 0f) ctx.attr.maxHeight.coerceAtMost(180f) else 180f)
                    }
                    Text {
                        attr {
                            text(ctx.attr.content)
                            fontSize(12f)
                            lineHeight(18f)
                            fontFamily("monospace")
                            color(if (ctx.attr.error) ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.labelPrimary)
                        }
                    }
                }
            }
        }
    }
}

internal class DshLongTextAttr : ComposeAttr() {
    var content: String by observable("")
    var maxHeight: Float by observable(0f)
    var error: Boolean by observable(false)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshLongText(init: DshLongTextView.() -> Unit) {
    addChild(DshLongTextView(), init)
}
