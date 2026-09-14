package com.example.dsh.ui.rendering

import com.example.dsh.message.boundedContextText
import com.example.dsh.message.DshContextCatalogEntry
import com.example.dsh.message.DshContextInstruction
import com.example.dsh.message.DshContextRecall
import com.example.dsh.message.DshContextSection
import com.example.dsh.tool.DshRemoteToolKind
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** The durable context fields retain their producer-specific presentation after expansion. */
data class DshContextDetail(
    val form: String,
    val body: String,
    val catalog: List<DshContextCatalogEntry>,
    val sections: List<DshContextSection>,
    val recalls: List<DshContextRecall>,
    val instructions: List<DshContextInstruction>,
    val relaySender: String,
)

class DshContextDetailsView : ComposeView<DshContextDetailsAttr, ComposeEvent>() {
    override fun createAttr(): DshContextDetailsAttr = DshContextDetailsAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val detail = ctx.attr.detail ?: return { View { } }
        val catalog = ObservableList<DshContextCatalogEntry>().also { it.addAll(detail.catalog) }
        val sections = ObservableList<DshContextSection>().also { it.addAll(detail.sections) }
        val recalls = ObservableList<DshContextRecall>().also { it.addAll(detail.recalls) }
        val instructions = ObservableList<DshContextInstruction>().also { it.addAll(detail.instructions) }
        return {
            View {
                attr {
                    flexDirectionColumn()
                    marginLeft(22f)
                    marginRight(22f)
                    marginTop(4f)
                }
                vif({ detail.relaySender.isNotEmpty() }) {
                    Text {
                        attr {
                            text("来自 ${detail.relaySender}")
                            fontSize(12f)
                            color(ctx.attr.colors.labelTertiary)
                            marginBottom(4f)
                        }
                    }
                }
                vfor({ catalog }) { item ->
                    View {
                        attr {
                            flexDirectionColumn()
                            padding(7f, 8f, 7f, 8f)
                            marginTop(4f)
                            borderRadius(6f)
                            backgroundColor(ctx.attr.colors.bgModulePlatform)
                        }
                        Text {
                            attr {
                                text(item.name)
                                fontSize(12f)
                                fontFamily("monospace")
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(item.description)
                                fontSize(13f)
                                lineHeight(20f)
                                color(ctx.attr.colors.labelSecondary)
                                marginTop(2f)
                            }
                        }
                    }
                }
                vfor({ instructions }) { item ->
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(5f)
                        }
                        Text {
                            attr {
                                text(item.path)
                                flex(1f)
                                lines(1)
                                fontSize(12f)
                                fontFamily("monospace")
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(when (item.action) { "remove" -> "已移除"; "replace" -> "已更新"; else -> "已加载" })
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.labelTertiary)
                            }
                        }
                    }
                }
                vfor({ recalls }) { item ->
                    View {
                        attr {
                            flexDirectionColumn()
                            marginTop(5f)
                            padding(7f, 8f, 7f, 8f)
                            borderRadius(6f)
                            backgroundColor(ctx.attr.colors.bgModulePlatform)
                        }
                        Text {
                            attr {
                                text(item.label)
                                fontSize(13f)
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("保留 ${item.retainedMessages} 条，省略 ${item.omittedMessages} 条${if (item.truncated) "，已截断" else ""}")
                                fontSize(12f)
                                color(ctx.attr.colors.labelTertiary)
                                marginTop(2f)
                            }
                        }
                    }
                }
                vfor({ sections }) { item ->
                    View {
                        attr {
                            flexDirectionColumn()
                            marginTop(8f)
                        }
                        Text {
                            attr {
                                text(item.title)
                                fontSize(13f)
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(boundedContextText(item.body))
                                fontSize(13f)
                                lineHeight(21f)
                                color(ctx.attr.colors.labelSecondary)
                                marginTop(3f)
                            }
                        }
                    }
                }
                vif({ detail.body.isNotEmpty() }) {
                    Text {
                        attr {
                            text(detail.body)
                            fontSize(13f)
                            lineHeight(21f)
                            color(ctx.attr.colors.labelSecondary)
                            marginTop(8f)
                        }
                    }
                }
            }
        }
    }
}

class DshContextDetailsAttr : ComposeAttr() {
    var detail: DshContextDetail? by observable(null)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

fun ViewContainer<*, *>.DshContextDetails(init: DshContextDetailsView.() -> Unit) {
    addChild(DshContextDetailsView(), init)
}

/** Input/output kept separately so a tool detail does not collapse into one raw text block. */
data class DshToolDetail(
    val kind: DshRemoteToolKind,
    val input: String,
    val output: String,
    val fallback: String,
    val running: Boolean,
    val error: Boolean,
    val filePath: String? = null,
)

fun dshExtractBashCommand(input: String): String {
    val trimmed = input.trim()
    if (!trimmed.startsWith("{")) return trimmed
    return runCatching { JSONObject(trimmed).optString("command") }
        .getOrNull()?.takeIf { it.isNotEmpty() } ?: trimmed
}

class DshToolDetailsView : ComposeView<DshToolDetailsAttr, ComposeEvent>() {
    override fun createAttr(): DshToolDetailsAttr = DshToolDetailsAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val detail = ctx.attr.detail ?: return { View { } }
        val outputText = detail.output.ifEmpty { if (detail.running) "" else detail.fallback }
        val codeStyle = detail.kind == DshRemoteToolKind.BASH ||
            detail.kind == DshRemoteToolKind.READ ||
            detail.kind == DshRemoteToolKind.FILE_MUTATION
        val inputLabel = when (detail.kind) {
            DshRemoteToolKind.BASH -> "命令"
            DshRemoteToolKind.READ -> "文件"
            DshRemoteToolKind.FILE_MUTATION -> "变更"
            DshRemoteToolKind.SEARCH, DshRemoteToolKind.WEB -> "查询"
            else -> "输入"
        }
        val outputLabel = when (detail.kind) {
            DshRemoteToolKind.BASH -> "输出"
            DshRemoteToolKind.READ -> "内容"
            DshRemoteToolKind.FILE_MUTATION -> "结果"
            DshRemoteToolKind.SEARCH, DshRemoteToolKind.WEB -> "结果"
            else -> "输出"
        }
        return if (detail.kind == DshRemoteToolKind.BASH) {
            {
                DshTerminalCard {
                    attr {
                        command = dshExtractBashCommand(detail.input)
                        output = outputText
                        running = detail.running
                        error = detail.error
                        colors = ctx.attr.colors
                        onCopy = { ctx.attr.onCopy(it) }
                    }
                }
            }
        } else if (detail.kind == DshRemoteToolKind.READ) {
            {
                DshReadCard {
                    attr {
                        filePath = detail.filePath ?: ""
                        content = outputText
                        colors = ctx.attr.colors
                        onCopy = { ctx.attr.onCopy(it) }
                    }
                }
            }
        } else {
            {
                View {
                    attr {
                        flexDirectionColumn()
                        marginLeft(22f)
                        marginTop(2f)
                    }
                    vif({ detail.input.isNotBlank() }) {
                        DshToolDetailSection {
                            attr {
                                label = inputLabel
                                content = detail.input
                                code = codeStyle
                                colors = ctx.attr.colors
                            }
                        }
                    }
                    vif({ outputText.isNotBlank() }) {
                        DshToolDetailSection {
                            attr {
                                label = outputLabel
                                content = outputText
                                code = codeStyle
                                error = detail.error
                                colors = ctx.attr.colors
                            }
                        }
                    }
                    vif({ detail.running && outputText.isEmpty() }) {
                        Text {
                            attr {
                                text("等待工具输出")
                                fontSize(13f)
                                lineHeight(20f)
                                color(ctx.attr.colors.labelTertiary)
                                marginTop(4f)
                            }
                        }
                    }
                }
            }
        }
    }
}

class DshToolDetailsAttr : ComposeAttr() {
    var detail: DshToolDetail? by observable(null)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
    var onCopy: (String) -> Unit by observable({})
}

class DshToolDetailSectionView : ComposeView<DshToolDetailSectionAttr, ComposeEvent>() {
    override fun createAttr(): DshToolDetailSectionAttr = DshToolDetailSectionAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionColumn()
                    marginTop(6f)
                    padding(8f)
                    borderRadius(8f)
                    backgroundColor(if (ctx.attr.code) ctx.attr.colors.markdownCodeBlock else ctx.attr.colors.bgModulePlatform)
                    border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                }
                Text {
                    attr {
                        text(ctx.attr.label)
                        fontSize(11f)
                        color(ctx.attr.colors.labelTertiary)
                        marginBottom(4f)
                    }
                }
                Scroller {
                    attr { height(120f) }
                    if (ctx.attr.code) {
                        Text {
                            attr {
                                text(ctx.attr.content)
                                fontSize(12f)
                                lineHeight(18f)
                                fontFamily("monospace")
                                color(if (ctx.attr.error) ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.labelSecondary)
                            }
                        }
                    } else {
                        DshLinkText {
                            attr {
                                content = ctx.attr.content
                                fontSize = 13f
                                lineHeight = 21f
                                textColor = if (ctx.attr.error) ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.labelSecondary
                                indent = false
                                this.colors = ctx.attr.colors
                            }
                        }
                    }
                }
            }
        }
    }
}

class DshToolDetailSectionAttr : ComposeAttr() {
    var label: String by observable("")
    var content: String by observable("")
    var code: Boolean by observable(false)
    var error: Boolean by observable(false)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

fun ViewContainer<*, *>.DshToolDetails(init: DshToolDetailsView.() -> Unit) {
    addChild(DshToolDetailsView(), init)
}

fun ViewContainer<*, *>.DshToolDetailSection(init: DshToolDetailSectionView.() -> Unit) {
    addChild(DshToolDetailSectionView(), init)
}

fun ViewContainer<*, *>.DshDisclosureRow(init: DshDisclosureRowView.() -> Unit) {
    addChild(DshDisclosureRowView(), init)
}
