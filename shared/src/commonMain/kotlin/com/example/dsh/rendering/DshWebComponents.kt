package com.example.dsh.rendering

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.example.dsh.web.*
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** The shared compact disclosure chrome used by context and tool rows. */
internal class DshDisclosureRowView : ComposeView<DshDisclosureRowAttr, ComposeEvent>() {
    override fun createAttr(): DshDisclosureRowAttr = DshDisclosureRowAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    if (ctx.attr.chrome) {
                        padding(8f, 10f, 8f, 10f)
                        borderRadius(8f)
                        backgroundColor(
                            when {
                                ctx.attr.errorSummary -> ctx.attr.colors.interactiveBgHoverDanger
                                ctx.attr.stopped -> ctx.attr.colors.stateWarnTertiary
                                ctx.attr.running -> ctx.attr.colors.stateBusinessTertiary
                                else -> ctx.attr.colors.bgLayer1
                            },
                        )
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                    }
                }
                View {
                    attr {
                        height(if (ctx.attr.compact) 24f else 28f)
                        flexDirectionRow()
                        alignItemsCenter()
                        if (ctx.attr.running) {
                            backgroundColor(ctx.attr.colors.stateBusinessTertiary)
                            borderRadius(6f)
                        } else if (ctx.attr.errorSummary) {
                            backgroundColor(ctx.attr.colors.interactiveBgHoverDanger)
                            borderRadius(6f)
                        } else if (ctx.attr.stopped) {
                            backgroundColor(ctx.attr.colors.stateWarnTertiary)
                            borderRadius(6f)
                        } else {
                            // 运行/失败/中断状态结束后必须显式复位，否则最后一帧底色会残留
                            backgroundColor(Color(0x00000000))
                            borderRadius(0f)
                        }
                    }
                    vif({ ctx.attr.errorSummary || ctx.attr.stopped }) {
                        View {
                            attr {
                                size(14f, 14f)
                                allCenter()
                            }
                            View {
                                attr {
                                    size(8f, 8f)
                                    borderRadius(4f)
                                    backgroundColor(
                                        if (ctx.attr.errorSummary) ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.stateWarnPrimary,
                                    )
                                }
                            }
                        }
                    }
                    vif({ !ctx.attr.errorSummary && !ctx.attr.stopped && ctx.attr.iconAsset.isNotEmpty() }) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets(ctx.attr.iconAsset))
                                size(14f, 14f)
                                tintColor(ctx.attr.colors.labelTertiary)
                            }
                        }
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("chevron-down.svg"))
                            size(14f, 14f)
                            marginLeft(if (ctx.attr.iconAsset.isNotEmpty()) 4f else 0f)
                            transform(Rotate(if (ctx.attr.open) 0f else -90f))
                            tintColor(ctx.attr.colors.labelTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(ctx.attr.title)
                            marginLeft(7f)
                            fontSize(if (ctx.attr.compact) 13f else 14f)
                            color(ctx.attr.colors.labelPrimary)
                        }
                    }
                    vif({ ctx.attr.summary.isNotEmpty() }) {
                        View {
                            attr {
                                width(2f)
                                height(2f)
                                borderRadius(1f)
                                marginLeft(8f)
                                marginRight(8f)
                                backgroundColor(ctx.attr.colors.labelTertiary)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.attr.summary)
                                flex(1f)
                                lines(1)
                                fontSize(if (ctx.attr.compact) 13f else 14f)
                                color(if (ctx.attr.errorSummary) ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.labelSecondary)
                            }
                        }
                    }
                    vif({ ctx.attr.summary.isEmpty() }) {
                        View { attr { flex(1f) } }
                    }
                    DshTapTarget {
                        if (ctx.attr.expandable) {
                            ctx.attr.open = !ctx.attr.open
                            ctx.attr.onToggle()
                        }
                    }
                }
                vif({ ctx.attr.open && !ctx.attr.headerOnly && ctx.attr.bodyMaxHeight > 0f }) {
                    Scroller {
                        attr {
                            height(if (ctx.attr.bodyContentHeight > 0f) minOf(ctx.attr.bodyContentHeight, ctx.attr.bodyMaxHeight) else ctx.attr.bodyMaxHeight)
                        }
                        View {
                            attr {
                                marginTop(6f)
                                marginBottom(8f)
                                flexDirectionColumn()
                                if (ctx.attr.bodyChrome) {
                                    padding(12f, 12f, 12f, 12f)
                                    borderRadius(8f)
                                    backgroundColor(ctx.attr.colors.bgModulePlatform)
                                }
                            }
                        vif({ ctx.attr.askCard != null }) {
                            DshAskQuestionCard {
                                attr {
                                    card = ctx.attr.askCard
                                    colors = ctx.attr.colors
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isNotEmpty() }) {
                            DshJsonTree {
                                attr {
                                    content = ctx.attr.jsonContent
                                    this.isExpanded = ctx.attr.isJsonNodeExpanded
                                    this.onToggle = ctx.attr.onToggleJsonNode
                                    colors = ctx.attr.colors
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isEmpty() && ctx.attr.contextDetail != null }) {
                            DshContextDetails {
                                attr {
                                    detail = ctx.attr.contextDetail
                                    colors = ctx.attr.colors
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isEmpty() && ctx.attr.contextDetail == null && ctx.attr.body.isNotEmpty() }) {
                            if (ctx.attr.plainBody) {
                                Text {
                                    attr {
                                        text(ctx.attr.body)
                            fontSize(if (ctx.attr.compact) 13f else 14f)
                                        lineHeight(24f)
                                        color(ctx.attr.colors.labelSecondary)
                                        marginTop(6f)
                                        marginLeft(22f)
                                        marginRight(22f)
                                    }
                                }
                            } else {
                                DshLongText {
                                    attr {
                                        content = ctx.attr.body
                                        error = ctx.attr.errorSummary
                                        colors = ctx.attr.colors
                                    }
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isEmpty() && ctx.attr.contextDetail == null && ctx.attr.toolDetail != null }) {
                            DshToolDetails {
                                attr {
                                    detail = ctx.attr.toolDetail
                                    colors = ctx.attr.colors
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isEmpty() && ctx.attr.contextDetail == null && ctx.attr.body.isEmpty() && ctx.attr.toolDetail == null }) {
                            Text {
                                attr {
                                    text("暂无输出")
                                    fontSize(12f)
                                    color(ctx.attr.colors.labelTertiary)
                                    margin(10f)
                                }
                            }
                        }
                        }
                    }
                }
                vif({ ctx.attr.open && !ctx.attr.headerOnly && ctx.attr.bodyMaxHeight <= 0f }) {
                    View {
                        attr {
                            marginTop(6f)
                            marginBottom(8f)
                            flexDirectionColumn()
                            if (ctx.attr.bodyChrome) {
                                padding(12f, 12f, 12f, 12f)
                                borderRadius(8f)
                                backgroundColor(ctx.attr.colors.bgModulePlatform)
                            }
                        }
                        vif({ ctx.attr.askCard != null }) {
                            DshAskQuestionCard {
                                attr {
                                    card = ctx.attr.askCard
                                    colors = ctx.attr.colors
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isNotEmpty() }) {
                            DshJsonTree {
                                attr {
                                    content = ctx.attr.jsonContent
                                    this.isExpanded = ctx.attr.isJsonNodeExpanded
                                    this.onToggle = ctx.attr.onToggleJsonNode
                                    colors = ctx.attr.colors
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isEmpty() && ctx.attr.contextDetail != null }) {
                            DshContextDetails {
                                attr {
                                    detail = ctx.attr.contextDetail
                                    colors = ctx.attr.colors
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isEmpty() && ctx.attr.contextDetail == null && ctx.attr.body.isNotEmpty() }) {
                            if (ctx.attr.plainBody) {
                                Text {
                                    attr {
                                        text(ctx.attr.body)
                            fontSize(if (ctx.attr.compact) 13f else 14f)
                                        lineHeight(24f)
                                        color(ctx.attr.colors.labelSecondary)
                                        marginTop(6f)
                                        marginLeft(22f)
                                        marginRight(22f)
                                    }
                                }
                            } else {
                                DshLongText {
                                    attr {
                                        content = ctx.attr.body
                                        error = ctx.attr.errorSummary
                                        colors = ctx.attr.colors
                                    }
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isEmpty() && ctx.attr.contextDetail == null && ctx.attr.toolDetail != null }) {
                            DshToolDetails {
                                attr {
                                    detail = ctx.attr.toolDetail
                                    colors = ctx.attr.colors
                                }
                            }
                        }
                        vif({ ctx.attr.askCard == null && ctx.attr.jsonContent.isEmpty() && ctx.attr.contextDetail == null && ctx.attr.body.isEmpty() && ctx.attr.toolDetail == null }) {
                            Text {
                                attr {
                                    text("暂无输出")
                                    fontSize(12f)
                                    color(ctx.attr.colors.labelTertiary)
                                    margin(10f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal class DshDisclosureRowAttr : ComposeAttr() {
    var title: String by observable("")
    var summary: String by observable("")
    var body: String by observable("")
    var iconAsset: String by observable("")
    var errorSummary: Boolean by observable(false)
    var open: Boolean by observable(false)
    var expandable: Boolean by observable(false)
    var onToggle: () -> Unit by observable({})
    var jsonContent: String by observable("")
    var isJsonNodeExpanded: (String) -> Boolean by observable({ false })
    var onToggleJsonNode: (String) -> Unit by observable({})
    var chrome: Boolean by observable(false)
    var running: Boolean by observable(false)
    var stopped: Boolean by observable(false)
    var plainBody: Boolean by observable(false)
    var bodyMaxHeight: Float by observable(0f)
    var bodyContentHeight: Float by observable(0f)
    var bodyChrome: Boolean by observable(false)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
    var askCard: DshAskQuestionCard? by observable(null)
    /** Compact one-line chrome used by Web ToolRow/ReasoningRow equivalents. */
    var compact: Boolean by observable(false)
    /** 只渲染可点击的表头；正文由调用方在行内另行渲染（用于回合过程分组）。 */
    var headerOnly: Boolean by observable(false)
    var toolDetail: DshToolDetail? by observable(null)
    var contextDetail: DshContextDetail? by observable(null)
    var onCopyToolCommand: (String) -> Unit by observable({})
}

/** The durable context fields retain their producer-specific presentation after expansion. */
internal data class DshContextDetail(
    val form: String,
    val body: String,
    val catalog: List<DshContextCatalogEntry>,
    val sections: List<DshContextSection>,
    val recalls: List<DshContextRecall>,
    val instructions: List<DshContextInstruction>,
    val relaySender: String,
)

internal class DshContextDetailsView : ComposeView<DshContextDetailsAttr, ComposeEvent>() {
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

internal class DshContextDetailsAttr : ComposeAttr() {
    var detail: DshContextDetail? by observable(null)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshContextDetails(init: DshContextDetailsView.() -> Unit) {
    addChild(DshContextDetailsView(), init)
}

/** Input/output kept separately so a tool detail does not collapse into one raw text block. */
internal data class DshToolDetail(
    val kind: DshRemoteToolKind,
    val input: String,
    val output: String,
    val fallback: String,
    val running: Boolean,
    val error: Boolean,
    val filePath: String? = null,
)


internal fun dshExtractBashCommand(input: String): String {
    val trimmed = input.trim()
    if (!trimmed.startsWith("{")) return trimmed
    return runCatching { JSONObject(trimmed).optString("command") }
        .getOrNull()?.takeIf { it.isNotEmpty() } ?: trimmed
}

internal class DshToolDetailsView : ComposeView<DshToolDetailsAttr, ComposeEvent>() {
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

internal class DshToolDetailsAttr : ComposeAttr() {
    var detail: DshToolDetail? by observable(null)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
    var onCopy: (String) -> Unit by observable({})
}

internal class DshToolDetailSectionView : ComposeView<DshToolDetailSectionAttr, ComposeEvent>() {
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
                    attr { height(180f) }
                    Text {
                        attr {
                            text(ctx.attr.content)
                            fontSize(if (ctx.attr.code) 12f else 13f)
                            lineHeight(if (ctx.attr.code) 18f else 21f)
                            if (ctx.attr.code) fontFamily("monospace")
                            color(if (ctx.attr.error) ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.labelSecondary)
                        }
                    }
                }
            }
        }
    }
}

internal class DshToolDetailSectionAttr : ComposeAttr() {
    var label: String by observable("")
    var content: String by observable("")
    var code: Boolean by observable(false)
    var error: Boolean by observable(false)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshToolDetails(init: DshToolDetailsView.() -> Unit) {
    addChild(DshToolDetailsView(), init)
}

internal fun ViewContainer<*, *>.DshToolDetailSection(init: DshToolDetailSectionView.() -> Unit) {
    addChild(DshToolDetailSectionView(), init)
}

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
                                tintColor(ctx.attr.colors.labelTertiary)
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
                                height(220f)
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
                                tintColor(ctx.attr.colors.labelTertiary)
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
                            height((lineCount * 22f).coerceAtMost(220f))
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
                        height(if (ctx.attr.maxHeight > 0f) ctx.attr.maxHeight.coerceAtMost(240f) else 240f)
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

internal fun ViewContainer<*, *>.DshDisclosureRow(init: DshDisclosureRowView.() -> Unit) {
    addChild(DshDisclosureRowView(), init)
}

/** Client-local disclosure state; it never writes back to the Host. */
internal data class DshDisclosureState(
    val key: String,
    val open: Boolean = false,
)

internal data class DshToolCardModel(
    val key: String,
    val title: String,
    val summary: String,
    val input: String?,
    val output: String?,
    val error: String? = null,
    val running: Boolean = false,
)

internal data class DshContextInjectionModel(
    val key: String,
    val sourceLabel: String,
    val summary: String,
    val body: String,
)

internal fun dshJsonPreview(value: String): String {
    if (value.length <= 160) return value
    return value.take(148) + "…"
}

internal fun dshParseJsonTree(raw: String): Any? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> null
        }
    }.getOrNull()
}

internal fun dshJsonObjectToMap(value: JSONObject): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    val keys = value.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = value.opt(key)
    }
    return map
}

internal fun dshBuildJsonNodes(
    value: Any?,
    key: String = "$",
    depth: Int = 0,
): List<DshJsonNode> = when (value) {
    is JSONObject -> dshBuildJsonNodes(dshJsonObjectToMap(value), key, depth)
    is JSONArray -> dshBuildJsonNodes((0 until value.length()).map { value.opt(it) }, key, depth)
    is Map<*, *> -> {
        if (value.isEmpty()) {
            listOf(DshJsonNode(key, "{}", "{}", emptyList(), depth))
        } else {
            value.entries.flatMap { (childKey, childValue) ->
                val label = childKey?.toString() ?: "null"
                val childNodes = dshBuildJsonNodes(childValue, "$key.$label", depth + 1)
                val preview = dshJsonPreview(childValue?.toString().orEmpty())
                listOf(DshJsonNode("$key.$label", label, preview, childNodes, depth)) + childNodes
            }
        }
    }
    is List<*> -> {
        if (value.isEmpty()) {
            listOf(DshJsonNode(key, "[]", "[]", emptyList(), depth))
        } else {
            value.flatMapIndexed { index, childValue ->
                val childNodes = dshBuildJsonNodes(childValue, "$key[$index]", depth + 1)
                val preview = dshJsonPreview(childValue?.toString().orEmpty())
                listOf(DshJsonNode("$key[$index]", "[$index]", preview, childNodes, depth)) + childNodes
            }
        }
    }
    else -> listOf(DshJsonNode(key, key, value?.toString() ?: "null", emptyList(), depth))
}

internal class DshJsonTreeView : ComposeView<DshJsonTreeAttr, ComposeEvent>() {
    override fun createAttr(): DshJsonTreeAttr = DshJsonTreeAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    marginTop(6f)
                    flexDirectionColumn()
                    padding(8f)
                    borderRadius(8f)
                    backgroundColor(ctx.attr.colors.markdownCodeBlock)
                    border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                }
                val parsed = dshParseJsonTree(ctx.attr.content)
                vif({ parsed != null }) {
                    val nodes = com.tencent.kuikly.core.reactive.collection.ObservableList<DshJsonNode>()
                    nodes.addAll(dshBuildJsonNodes(parsed ?: Any()).filter { it.depth == 0 })
                    vfor({ nodes }) { node ->
                        DshJsonNodeRow {
                            attr {
                                this.node = node
                                expanded = ctx.attr.isExpanded(node.key)
                                isNodeExpanded = ctx.attr.isExpanded
                                onToggle = { ctx.attr.onToggle(node.key) }
                                onToggleNode = ctx.attr.onToggle
                                colors = ctx.attr.colors
                            }
                        }
                    }
                }
                vif({ parsed == null }) {
                    Text {
                        attr {
                            text(ctx.attr.content)
                            fontSize(12f)
                            fontFamily("monospace")
                            color(ctx.attr.colors.labelPrimary)
                        }
                    }
                }
            }
        }
    }
}

internal class DshJsonTreeAttr : ComposeAttr() {
    var content: String by observable("")
    var isExpanded: (String) -> Boolean by observable({ false })
    var onToggle: (String) -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

internal class DshJsonNodeRowView : ComposeView<DshJsonNodeRowAttr, ComposeEvent>() {
    override fun createAttr(): DshJsonNodeRowAttr = DshJsonNodeRowAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionColumn()
                    marginLeft(ctx.attr.node.depth * 10f)
                }
                View {
                    attr { height(28f); flexDirectionRow(); alignItemsCenter() }
                    vif({ ctx.attr.node.children.isNotEmpty() }) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets("chevron-down.svg"))
                                size(12f, 12f)
                                tintColor(ctx.attr.colors.labelTertiary)
                                transform(Rotate(if (ctx.attr.expanded) 0f else -90f))
                            }
                        }
                    }
                    Text {
                        attr {
                            text(ctx.attr.node.label)
                            marginLeft(6f)
                            fontSize(12f)
                            fontFamily("monospace")
                            color(ctx.attr.colors.labelPrimary)
                        }
                    }
                    Text {
                        attr {
                            text(ctx.attr.node.preview)
                            marginLeft(8f)
                            flex(1f)
                            lines(1)
                            fontSize(11f)
                            fontFamily("monospace")
                            color(ctx.attr.colors.labelTertiary)
                        }
                    }
                    vif({ ctx.attr.node.children.isNotEmpty() }) {
                        DshTapTarget {
                            ctx.attr.expanded = !ctx.attr.expanded
                            ctx.attr.onToggle()
                        }
                    }
                }
                vif({ ctx.attr.expanded && ctx.attr.node.children.isNotEmpty() }) {
                    val childNodes = com.tencent.kuikly.core.reactive.collection.ObservableList<DshJsonNode>()
                    childNodes.addAll(ctx.attr.node.children.filter { it.depth == ctx.attr.node.depth + 1 })
                    vfor({ childNodes }) { child ->
                        DshJsonNodeRow {
                            attr {
                                this.node = child
                                expanded = ctx.attr.isNodeExpanded(child.key)
                                isNodeExpanded = ctx.attr.isNodeExpanded
                                onToggle = { ctx.attr.onToggleNode(child.key) }
                                onToggleNode = ctx.attr.onToggleNode
                                colors = ctx.attr.colors
                            }
                        }
                    }
                }
            }
        }
    }
}

internal class DshJsonNodeRowAttr : ComposeAttr() {
    var node: DshJsonNode by observable(DshJsonNode("$", "$", "null"))
    var expanded: Boolean by observable(false)
    var onToggle: () -> Unit by observable({})
    var isNodeExpanded: (String) -> Boolean by observable({ false })
    var onToggleNode: (String) -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshJsonTree(init: DshJsonTreeView.() -> Unit) {
    addChild(DshJsonTreeView(), init)
}

internal fun ViewContainer<*, *>.DshJsonNodeRow(init: DshJsonNodeRowView.() -> Unit) {
    addChild(DshJsonNodeRowView(), init)
}

internal class DshQueueDockView : ComposeView<DshQueueDockAttr, ComposeEvent>() {
    override fun createAttr(): DshQueueDockAttr = DshQueueDockAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val items = ctx.attr.items
        val expanded = ctx.attr.expanded || items.size <= 1 || ctx.attr.editingId.isNotEmpty() || ctx.attr.actionBusy
        return {
            vif({ items.isNotEmpty() }) {
                View {
                    attr {
                        marginBottom(8f)
                        flexDirectionColumn()
                        padding(8f)
                        borderRadius(10f)
                        backgroundColor(ctx.attr.colors.bgBase)
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                    }
                    vif({ items.size > 1 }) {
                        View {
                            attr {
                                height(30f)
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            event { click { if (!ctx.attr.actionBusy && ctx.attr.editingId.isEmpty()) ctx.attr.onToggle() } }
                            Text {
                                attr {
                                    text("队列 · ${items.size}")
                                    flex(1f)
                                    fontSize(13f)
                                    color(ctx.attr.colors.labelPrimary)
                                }
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("chevron-down.svg"))
                                    size(14f, 14f)
                                    tintColor(ctx.attr.colors.labelTertiary)
                                    transform(Rotate(if (expanded) 0f else -90f))
                                }
                            }
                        }
                    }
                    vif({ expanded }) {
                        vfor({ ctx.attr.items }) { item ->
                            View {
                                attr {
                                    minHeight(40f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                }
                                vif({ ctx.attr.editingId != item.id }) {
                                    Text {
                                        attr {
                                            text(item.preview)
                                            flex(1f)
                                            lines(1)
                                            fontSize(13f)
                                            color(ctx.attr.colors.labelPrimary)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text("编辑")
                                            marginLeft(8f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.stateBusinessPrimary)
                                        }
                                        event { click { if (!ctx.attr.actionBusy) ctx.attr.onEdit(item.id) } }
                                    }
                                    Text {
                                        attr {
                                            text("删除")
                                            marginLeft(10f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.stateErrorPrimary)
                                        }
                                        event { click { if (!ctx.attr.actionBusy) ctx.attr.onRemove(item.id) } }
                                    }
                                    Text {
                                        attr {
                                            text("转向")
                                            marginLeft(10f)
                                            fontSize(12f)
                                            color(if (ctx.attr.running) ctx.attr.colors.stateBusinessPrimary else ctx.attr.colors.labelCaption)
                                        }
                                        event { click { if (ctx.attr.running && !ctx.attr.actionBusy) ctx.attr.onSteer(item.id) } }
                                    }
                                }
                                vif({ ctx.attr.editingId == item.id }) {
                                    Input {
                                        ref { it.view?.setText(ctx.attr.editingText) }
                                        attr {
                                            flex(1f)
                                            height(32f)
                                            fontSize(13f)
                                            placeholder("编辑队列消息")
                                            placeholderColor(ctx.attr.colors.labelTertiary)
                                        }
                                        event { textDidChange { ctx.attr.onEditingTextChange(it.text) } }
                                    }
                                    Text {
                                        attr {
                                            text("保存")
                                            marginLeft(8f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.stateSuccessPrimary)
                                        }
                                        event { click { if (!ctx.attr.actionBusy) ctx.attr.onSaveEdit(item.id) } }
                                    }
                                    Text {
                                        attr {
                                            text("取消")
                                            marginLeft(10f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.labelTertiary)
                                        }
                                        event { click { if (!ctx.attr.actionBusy) ctx.attr.onCancelEdit() } }
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

internal class DshQueueDockAttr : ComposeAttr() {
    var items: com.tencent.kuikly.core.reactive.collection.ObservableList<DshQueueItem> by observable(
        com.tencent.kuikly.core.reactive.collection.ObservableList(),
    )
    var expanded: Boolean by observable(false)
    var running: Boolean by observable(false)
    var editingId: String by observable("")
    var editingText: String by observable("")
    var actionBusy: Boolean by observable(false)
    var onToggle: () -> Unit by observable({})
    var onEdit: (String) -> Unit by observable({})
    var onEditingTextChange: (String) -> Unit by observable({})
    var onSaveEdit: (String) -> Unit by observable({})
    var onCancelEdit: () -> Unit by observable({})
    var onRemove: (String) -> Unit by observable({})
    var onSteer: (String) -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshQueueDock(init: DshQueueDockView.() -> Unit) {
    addChild(DshQueueDockView(), init)
}

internal class DshJobsPanelView : ComposeView<DshJobsPanelAttr, ComposeEvent>() {
    override fun createAttr(): DshJobsPanelAttr = DshJobsPanelAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val snapshot = ctx.attr.jobs.toList()
        val ordered = dshOrderedJobs(snapshot)
        val liveCount = ordered.count { it.status == "running" || it.status == "stopping" }
        return {
            vif({ ctx.attr.jobs.isNotEmpty() }) {
                View {
                    attr {
                        marginBottom(8f)
                        flexDirectionColumn()
                        padding(8f)
                        borderRadius(10f)
                        backgroundColor(ctx.attr.colors.bgBase)
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                    }
                    View {
                        attr { height(30f); flexDirectionRow(); alignItemsCenter() }
                        event { click { ctx.attr.onToggle() } }
                        Text {
                            attr {
                                text(if (liveCount > 0) "后台任务 · $liveCount 运行中" else "后台任务 · ${snapshot.size}")
                                flex(1f)
                                fontSize(13f)
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("chevron-down.svg"))
                                size(14f, 14f)
                                tintColor(ctx.attr.colors.labelTertiary)
                                transform(Rotate(if (ctx.attr.expanded) 0f else -90f))
                            }
                        }
                    }
                    vif({ ctx.attr.expanded }) {
                    vfor({ ctx.attr.jobs }) { job ->
                        View {
                            attr {
                                minHeight(46f)
                                marginTop(6f)
                                flexDirectionColumn()
                                padding(6f)
                                borderRadius(8f)
                                backgroundColor(ctx.attr.colors.bgBase)
                            }
                            View {
                                attr { flexDirectionRow(); alignItemsCenter() }
                                View {
                                    attr {
                                        size(7f, 7f)
                                        borderRadius(4f)
                                        backgroundColor(
                                            when (job.status) {
                                                "running" -> ctx.attr.colors.stateSuccessPrimary
                                                "stopping" -> ctx.attr.colors.stateWarnPrimary
                                                "completed" -> ctx.attr.colors.labelTertiary
                                                "failed" -> ctx.attr.colors.stateErrorPrimary
                                                else -> ctx.attr.colors.labelTertiary
                                            },
                                        )
                                    }
                                }
                                Text {
                                    attr {
                                        text(job.kind)
                                        marginLeft(7f)
                                        fontSize(12f)
                                        fontWeightMedium()
                                        color(ctx.attr.colors.labelPrimary)
                                    }
                                }
                                Text {
                                    attr {
                                        text(job.detail.ifEmpty { dshJobStatusLabel(job.status) })
                                        marginLeft(8f)
                                        fontSize(11f)
                                        color(ctx.attr.colors.labelSecondary)
                                    }
                                }
                                Text {
                                    attr {
                                        text(dshJobDuration(job, ctx.attr.now))
                                        marginLeft(8f)
                                        fontSize(11f)
                                        color(ctx.attr.colors.labelTertiary)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text(job.label)
                                    marginTop(3f)
                                    lines(1)
                                    fontSize(12f)
                                    color(ctx.attr.colors.labelPrimary)
                                }
                            }
                            vif({ job.detail.isNotEmpty() }) {
                                Text {
                                    attr {
                                        text(job.detail)
                                        marginTop(2f)
                                        lines(1)
                                        fontSize(11f)
                                        color(ctx.attr.colors.labelTertiary)
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

internal fun dshOrderedJobs(jobs: List<DshJobItem>): List<DshJobItem> {
    return jobs.sortedWith(
        compareByDescending<DshJobItem> { it.status == "running" || it.status == "stopping" }
            .thenBy { if (it.status == "running" || it.status == "stopping") it.startedAt else Long.MAX_VALUE }
            .thenByDescending { it.finishedAt ?: it.startedAt },
    )
}

/** Bottom jobs UI only represents work that can still change. */
internal fun dshLiveJobs(jobs: List<DshJobItem>): List<DshJobItem> =
    dshOrderedJobs(jobs.filter { it.status == "running" || it.status == "stopping" })

private fun dshJobStatusLabel(status: String): String = when (status) {
    "running" -> "运行中"
    "stopping" -> "正在停止"
    "completed" -> "已完成"
    "killed" -> "已取消"
    "failed" -> "已失败"
    else -> status
}

internal fun dshJobDuration(job: DshJobItem, now: Long): String {
    val end = job.finishedAt ?: now.takeIf { it > 0L } ?: return "进行中"
    val seconds = ((end - job.startedAt).coerceAtLeast(0L) / 1000L).toInt()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分${rest}秒"
        else -> "${rest}秒"
    }
}

internal class DshJobsPanelAttr : ComposeAttr() {
    var jobs: com.tencent.kuikly.core.reactive.collection.ObservableList<DshJobItem> by observable(
        com.tencent.kuikly.core.reactive.collection.ObservableList(),
    )
    var expanded: Boolean by observable(false)
    var now: Long by observable(0L)
    var onToggle: () -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshJobsPanel(init: DshJobsPanelView.() -> Unit) {
    addChild(DshJobsPanelView(), init)
}

internal class DshGoalBarView : ComposeView<DshGoalBarAttr, ComposeEvent>() {
    private var editing by observable(false)
    private var draft by observable("")

    override fun createAttr(): DshGoalBarAttr = DshGoalBarAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            vif({ ctx.attr.snapshot != null }) {
                val goal = ctx.attr.snapshot ?: return@vif
                View {
                    attr {
                        marginBottom(8f)
                        minHeight(38f)
                        flexDirectionRow()
                        alignItemsCenter()
                        padding(8f, 10f, 8f, 10f)
                        borderRadius(8f)
                        backgroundColor(ctx.attr.colors.bgBase)
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                    }
                    Image {
                        attr { src(ImageUri.commonAssets("goal.svg")); size(14f, 14f); tintColor(ctx.attr.colors.labelTertiary) }
                    }
                    Text {
                        attr {
                            text(when (goal.phase) {
                                "active" -> "目标进行中"
                                "paused" -> "目标已暂停"
                                "blocked" -> "目标受阻"
                                else -> goal.phase
                            })
                            marginLeft(6f)
                            fontSize(12f)
                            fontWeightMedium()
                            color(if (goal.phase == "blocked") ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.labelSecondary)
                        }
                    }
                    vif({ !ctx.editing }) {
                        Text {
                            attr {
                                text(goal.objective)
                                flex(1f)
                                marginLeft(8f)
                                lines(2)
                                fontSize(12f)
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                    }
                    vif({ ctx.editing }) {
                        Input {
                            ref { it.view?.setText(ctx.draft) }
                            attr {
                                flex(1f)
                                height(30f)
                                marginLeft(8f)
                                fontSize(12f)
                                text(ctx.draft)
                            }
                            event { textDidChange { ctx.draft = it.text } }
                        }
                        Text {
                            attr {
                                text(if (ctx.attr.busy) "处理中" else "保存")
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.stateSuccessPrimary)
                            }
                            event {
                                click {
                                    if (!ctx.attr.busy && ctx.draft.trim().isNotEmpty()) {
                                        ctx.attr.onEdit(ctx.draft.trim()) { success -> if (success) ctx.editing = false }
                                    }
                                }
                            }
                        }
                        Text {
                            attr {
                                text("取消")
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.labelTertiary)
                            }
                            event { click { if (!ctx.attr.busy) ctx.editing = false } }
                        }
                    }
                    vif({ goal.blockedReason.isNotEmpty() }) {
                        Text {
                            attr {
                                text(goal.blockedReason)
                                marginLeft(6f)
                                lines(1)
                                fontSize(11f)
                                color(ctx.attr.colors.stateErrorPrimary)
                            }
                        }
                    }
                    vif({ ctx.attr.error.isNotEmpty() }) {
                        Text {
                            attr {
                                text(ctx.attr.error)
                                marginLeft(6f)
                                fontSize(11f)
                                color(ctx.attr.colors.stateErrorPrimary)
                            }
                        }
                    }
                    vif({ !ctx.editing && goal.phase == "active" }) {
                        Text {
                            attr {
                                text(if (ctx.attr.busy) "处理中" else "暂停")
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.labelSecondary)
                            }
                            event { click { if (!ctx.attr.busy) ctx.attr.onPause() } }
                        }
                    }
                    vif({ !ctx.editing && goal.phase == "paused" }) {
                        Text {
                            attr {
                                text(if (ctx.attr.busy) "处理中" else "恢复")
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.stateSuccessPrimary)
                            }
                            event { click { if (!ctx.attr.busy) ctx.attr.onResume() } }
                        }
                    }
                    vif({ !ctx.editing }) {
                    Text {
                        attr {
                            text("清除")
                            marginLeft(8f)
                            fontSize(12f)
                            color(ctx.attr.colors.stateErrorPrimary)
                        }
                        event { click { if (!ctx.attr.busy) ctx.attr.onClear() } }
                    }
                    }
                    vif({ !ctx.editing }) {
                    Text {
                        attr {
                            text("编辑")
                            marginLeft(8f)
                            fontSize(12f)
                            color(ctx.attr.colors.stateBusinessPrimary)
                        }
                        event { click { if (!ctx.attr.busy) { ctx.draft = goal.objective; ctx.editing = true } } }
                    }
                    }
                }
            }
        }
    }
}

internal class DshGoalBarAttr : ComposeAttr() {
    var snapshot: DshGoalSnapshot? by observable(null)
    var busy: Boolean by observable(false)
    var error: String by observable("")
    var onEdit: (String, (Boolean) -> Unit) -> Unit by observable({ _, _ -> })
    var onPause: () -> Unit by observable({})
    var onResume: () -> Unit by observable({})
    var onClear: () -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshGoalBar(init: DshGoalBarView.() -> Unit) {
    addChild(DshGoalBarView(), init)
}

internal class DshApprovalPanelView : ComposeView<DshApprovalPanelAttr, ComposeEvent>() {
    override fun createAttr(): DshApprovalPanelAttr = DshApprovalPanelAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            vif({ ctx.attr.approval != null }) {
                val approval = ctx.attr.approval ?: return@vif
                View {
                    attr {
                        marginLeft(4f)
                        marginRight(4f)
                        flexDirectionColumn()
                        padding(14f, 14f, 14f, 14f)
                        borderRadius(16f)
                        backgroundColor(ctx.attr.colors.bgLayer1)
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.stateWarnSecondary))
                    }
                    View {
                        attr {
                            alignSelfFlexStart()
                            padding(3f, 8f, 3f, 8f)
                            borderRadius(6f)
                            backgroundColor(ctx.attr.colors.stateWarnTertiary)
                        }
                        Text {
                            attr {
                                text("等待审批")
                                fontSize(11f)
                                fontWeightMedium()
                                color(ctx.attr.colors.stateWarnPrimary)
                            }
                        }
                    }
                    Text {
                        attr {
                            text(approval.reason ?: "需要使用 ${approval.toolName}")
                            marginTop(10f)
                            fontSize(16f)
                            fontWeightMedium()
                            lineHeight(23f)
                            color(ctx.attr.colors.labelPrimary)
                        }
                    }
                    vif({ approval.command != null }) {
                        View {
                            attr {
                                marginTop(8f)
                                padding(10f, 12f, 10f, 12f)
                                borderRadius(10f)
                                backgroundColor(ctx.attr.colors.bgBase)
                            }
                            Text {
                                attr {
                                    text(approval.command ?: "")
                                    fontSize(12f)
                                    lineHeight(18f)
                                    fontFamily("monospace")
                                    color(ctx.attr.colors.labelSecondary)
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            height(40f)
                            marginTop(12f)
                            flexDirectionRow()
                            justifyContentFlexEnd()
                            alignItemsCenter()
                        }
                        View {
                            attr {
                                height(32f)
                                paddingLeft(14f)
                                paddingRight(14f)
                                borderRadius(8f)
                                justifyContentCenter()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    text(if (ctx.attr.busy) "处理中" else "拒绝")
                                    fontSize(13f)
                                    color(ctx.attr.colors.stateErrorPrimary)
                                }
                            }
                            DshTapTarget { if (!ctx.attr.busy) ctx.attr.onAnswer("rejected") }
                        }
                        View {
                            attr {
                                height(32f)
                                marginLeft(8f)
                                paddingLeft(14f)
                                paddingRight(14f)
                                borderRadius(8f)
                                backgroundColor(if (ctx.attr.busy) ctx.attr.colors.stateSuccessTertiary else ctx.attr.colors.stateSuccessPrimary)
                                justifyContentCenter()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    text(if (ctx.attr.busy) "处理中" else "允许一次")
                                    fontSize(13f)
                                    fontWeightMedium()
                                    color(Color.WHITE)
                                }
                            }
                            DshTapTarget { if (!ctx.attr.busy) ctx.attr.onAnswer("allowed-once") }
                        }
                    }
                }
            }
        }
    }
}

internal class DshApprovalPanelAttr : ComposeAttr() {
    var approval: DshPendingApproval? by observable(null)
    var busy: Boolean by observable(false)
    var onAnswer: (String) -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

internal class DshQuestionFlowView : ComposeView<DshQuestionFlowAttr, ComposeEvent>() {
    override fun createAttr(): DshQuestionFlowAttr = DshQuestionFlowAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    // 内部 UI 状态：卡片是否收起（只显示标题栏）
    private var collapsed by observable(false)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // 切换题目依赖 attr.index / attr.question，必须在响应式闭包内取值，
            // 否则 body() 只在 didInit 执行一次，item 会永远停在初始题目。
            // 局部函数只在 attr {} / vif {} 调用时才读取 attr，确保依赖被收集。
            fun currentItem(): DshPendingQuestionItem? =
                ctx.attr.question?.questions?.getOrNull(ctx.attr.index)

            fun totalCount(): Int = ctx.attr.question?.questions?.size ?: 1

            vif({ currentItem() != null }) {
                View {
                    attr {
                        marginLeft(4f)
                        marginRight(4f)
                        flexDirectionColumn()
                        padding(14f, 16f, 12f, 16f)
                        borderRadius(20f)
                        backgroundColor(ctx.attr.colors.bgLayer1)
                        boxShadow(BoxShadow(0f, 8f, 30f, Color(0x26000000)))
                        // 选项少时卡片 wrap content（maxHeight 上限，无空白）；选项多时 flex(1f) 占满覆盖层，
                        // 键盘弹出覆盖层收缩时卡片自动收缩，防止顶部顶到 topbar。
                        // 用 flex 值切换而非 if，切换问题时必须复位，避免上一题占满高度残留。
                        flex(if (ctx.attr.options.size > 4) 1f else 0f)
                        maxHeight(560f)
                    }
                    // ===== 标题栏：左侧标签+标题，右侧收起+关闭 =====
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsFlexStart()
                            justifyContentSpaceBetween()
                        }
                        // 左侧：标签 + 标题
                        View {
                            attr {
                                flex(1f)
                                flexDirectionColumn()
                                marginRight(12f)
                            }
                            Text {
                                attr {
                                    text(currentItem()?.header?.ifEmpty { "确认意图" } ?: "确认意图")
                                    fontSize(12f)
                                    color(ctx.attr.colors.labelTertiary)
                                }
                            }
                            Text {
                                attr {
                                    text(currentItem()?.question ?: "")
                                    marginTop(6f)
                                    fontSize(17f)
                                    fontWeightMedium()
                                    lineHeight(24f)
                                    color(ctx.attr.colors.labelPrimary)
                                }
                            }
                        }
                        // 右侧：收起按钮 + 关闭按钮
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            // 收起/展开按钮
                            View {
                                attr {
                                    size(32f, 32f)
                                    borderRadius(16f)
                                    justifyContentCenter()
                                    alignItemsCenter()
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("chevron-down.svg"))
                                        size(18f, 18f)
                                        tintColor(ctx.attr.colors.labelTertiary)
                                        transform(Rotate(if (ctx.collapsed) 0f else 180f))
                                    }
                                }
                                DshTapTarget { ctx.collapsed = !ctx.collapsed }
                            }
                            // 关闭按钮（取消提问）
                            View {
                                attr {
                                    size(32f, 32f)
                                    marginLeft(4f)
                                    borderRadius(16f)
                                    justifyContentCenter()
                                    alignItemsCenter()
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("x.svg"))
                                        size(16f, 16f)
                                        tintColor(ctx.attr.colors.labelTertiary)
                                    }
                                }
                                DshTapTarget { ctx.attr.onDismiss() }
                            }
                        }
                    }
                    // ===== 展开内容 =====
                    vif({ !ctx.collapsed }) {
                        // 问题描述
                        vif({ !currentItem()?.detail.isNullOrEmpty() }) {
                            Text {
                                attr {
                                    text(currentItem()?.detail ?: "")
                                    marginTop(10f)
                                    fontSize(13f)
                                    lineHeight(19f)
                                    color(ctx.attr.colors.labelSecondary)
                                }
                            }
                        }
                        // 选项列表：≤4个直接渲染（卡片自然撑开无空白），>4个用 Scroller 固定高度滚动
                        vif({ ctx.attr.options.size <= 4 }) {
                        vfor({ ctx.attr.options }) { option ->
                            val selected = ctx.attr.selected.contains(option.label)
                            val optionIndex = ctx.attr.options.indexOf(option) + 1
                            val isRecommended = option.label.contains("（推荐）") || option.label.contains("(Recommended)", ignoreCase = true)
                            val displayLabel = option.label.replace("（推荐）", "").replace(Regex("\\(Recommended\\)", RegexOption.IGNORE_CASE), "").trim()
                            View {
                                attr {
                                    marginTop(10f)
                                    flexDirectionRow()
                                    alignItemsFlexStart()
                                    padding(12f, 14f, 12f, 14f)
                                    borderRadius(12f)
                                    backgroundColor(if (selected) ctx.attr.colors.stateBusinessTertiary else ctx.attr.colors.bgBase)
                                    border(Border(
                                        1f,
                                        BorderStyle.SOLID,
                                        if (selected) ctx.attr.colors.stateBusinessPrimary else ctx.attr.colors.borderL2,
                                    ))
                                }
                                // 编号方块
                                View {
                                    attr {
                                        size(22f, 22f)
                                        marginTop(1f)
                                        borderRadius(6f)
                                        backgroundColor(if (selected) ctx.attr.colors.stateBusinessPrimary else ctx.attr.colors.specificSelector)
                                        justifyContentCenter()
                                        alignItemsCenter()
                                    }
                                    Text {
                                        attr {
                                            text("$optionIndex")
                                            fontSize(12f)
                                            fontWeightMedium()
                                            color(if (selected) Color.WHITE else ctx.attr.colors.labelTertiary)
                                        }
                                    }
                                }
                                // 标题 + 描述
                                View {
                                    attr {
                                        flex(1f)
                                        marginLeft(10f)
                                        flexDirectionColumn()
                                    }
                                    // 标题 + 推荐徽章
                                    View {
                                        attr {
                                            flexDirectionRow()
                                            alignItemsCenter()
                                        }
                                        Text {
                                            attr {
                                                text(displayLabel)
                                                fontSize(14f)
                                                fontWeightMedium()
                                                color(ctx.attr.colors.labelPrimary)
                                            }
                                        }
                                        vif({ isRecommended }) {
                                            View {
                                                attr {
                                                    marginLeft(6f)
                                                    padding(2f, 6f, 2f, 6f)
                                                    borderRadius(4f)
                                                    backgroundColor(ctx.attr.colors.stateBusinessPrimary)
                                                }
                                                Text {
                                                    attr {
                                                        text("推荐")
                                                        fontSize(10f)
                                                        fontWeightMedium()
                                                        color(Color.WHITE)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    vif({ option.description.isNotEmpty() }) {
                                        Text {
                                            attr {
                                                text(option.description)
                                                marginTop(3f)
                                                fontSize(12f)
                                                lineHeight(17f)
                                                color(ctx.attr.colors.labelSecondary)
                                            }
                                        }
                                    }
                                }
                                DshTapTarget { ctx.attr.onToggleOption(option.label) }
                            }
                        }
                        }
                        vif({ ctx.attr.options.size > 4 }) {
                        Scroller {
                        attr { flex(1f) }
                        vfor({ ctx.attr.options }) { option ->
                            val selected = ctx.attr.selected.contains(option.label)
                            val optionIndex = ctx.attr.options.indexOf(option) + 1
                            val isRecommended = option.label.contains("（推荐）") || option.label.contains("(Recommended)", ignoreCase = true)
                            val displayLabel = option.label.replace("（推荐）", "").replace(Regex("\\(Recommended\\)", RegexOption.IGNORE_CASE), "").trim()
                            View {
                                attr {
                                    marginTop(10f)
                                    flexDirectionRow()
                                    alignItemsFlexStart()
                                    padding(12f, 14f, 12f, 14f)
                                    borderRadius(12f)
                                    backgroundColor(if (selected) ctx.attr.colors.stateBusinessTertiary else ctx.attr.colors.bgBase)
                                    border(Border(
                                        1f,
                                        BorderStyle.SOLID,
                                        if (selected) ctx.attr.colors.stateBusinessPrimary else ctx.attr.colors.borderL2,
                                    ))
                                }
                                // 编号方块
                                View {
                                    attr {
                                        size(22f, 22f)
                                        marginTop(1f)
                                        borderRadius(6f)
                                        backgroundColor(if (selected) ctx.attr.colors.stateBusinessPrimary else ctx.attr.colors.specificSelector)
                                        justifyContentCenter()
                                        alignItemsCenter()
                                    }
                                    Text {
                                        attr {
                                            text("$optionIndex")
                                            fontSize(12f)
                                            fontWeightMedium()
                                            color(if (selected) Color.WHITE else ctx.attr.colors.labelTertiary)
                                        }
                                    }
                                }
                                // 标题 + 描述
                                View {
                                    attr {
                                        flex(1f)
                                        marginLeft(10f)
                                        flexDirectionColumn()
                                    }
                                    // 标题 + 推荐徽章
                                    View {
                                        attr {
                                            flexDirectionRow()
                                            alignItemsCenter()
                                        }
                                        Text {
                                            attr {
                                                text(displayLabel)
                                                fontSize(14f)
                                                fontWeightMedium()
                                                color(ctx.attr.colors.labelPrimary)
                                            }
                                        }
                                        vif({ isRecommended }) {
                                            View {
                                                attr {
                                                    marginLeft(6f)
                                                    padding(2f, 6f, 2f, 6f)
                                                    borderRadius(4f)
                                                    backgroundColor(ctx.attr.colors.stateBusinessPrimary)
                                                }
                                                Text {
                                                    attr {
                                                        text("推荐")
                                                        fontSize(10f)
                                                        fontWeightMedium()
                                                        color(Color.WHITE)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    vif({ option.description.isNotEmpty() }) {
                                        Text {
                                            attr {
                                                text(option.description)
                                                marginTop(3f)
                                                fontSize(12f)
                                                lineHeight(17f)
                                                color(ctx.attr.colors.labelSecondary)
                                            }
                                        }
                                    }
                                }
                                DshTapTarget { ctx.attr.onToggleOption(option.label) }
                            }
                        }
                        }
                        }
                        // 自定义答案输入框：铅笔图标 + TextArea（支持换行，minHeight 起 maxHeight 后内部滚动）
                        View {
                            attr {
                                minHeight(42f)
                                marginTop(10f)
                                paddingLeft(12f)
                                paddingRight(12f)
                                paddingTop(6f)
                                paddingBottom(6f)
                                borderRadius(12f)
                                backgroundColor(ctx.attr.colors.bgBase)
                                border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("tool-ask.svg"))
                                    size(18f, 18f)
                                    tintColor(ctx.attr.colors.labelTertiary)
                                }
                            }
                            TextArea {
                                ref { it.view?.setText(ctx.attr.custom) }
                                attr {
                                    flex(1f)
                                    minHeight(20f)
                                    maxHeight(90f)
                                    marginLeft(8f)
                                    placeholder("输入你的答案")
                                    placeholderColor(ctx.attr.colors.labelTertiary)
                                    fontSize(13f)
                                    color(ctx.attr.colors.labelPrimary)
                                    backgroundColor(Color(0x00000000))
                                }
                                event {
                                    textDidChange { ctx.attr.onCustomChange(it.text) }
                                    keyboardHeightChange { ctx.attr.onKeyboardHeightChange(it) }
                                }
                            }
                        }
                        // 错误提示
                        vif({ ctx.attr.error.isNotEmpty() }) {
                            Text {
                                attr {
                                    text(ctx.attr.error)
                                    marginTop(8f)
                                    fontSize(12f)
                                    color(ctx.attr.colors.stateErrorPrimary)
                                }
                            }
                        }
                        // ===== 底部栏：分页 + 跳过 + 提交 =====
                        View {
                            attr {
                                marginTop(16f)
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            // 分页：左箭头 + 1/1 + 右箭头
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                }
                                // 左箭头
                                View {
                                    attr {
                                        size(28f, 28f)
                                        borderRadius(14f)
                                        justifyContentCenter()
                                        alignItemsCenter()
                                        opacity(if (ctx.attr.index > 0) 1f else 0.3f)
                                    }
                                    Image {
                                        attr {
                                            src(ImageUri.commonAssets("chevron-left.svg"))
                                            size(16f, 16f)
                                            tintColor(ctx.attr.colors.labelSecondary)
                                        }
                                    }
                                    vif({ ctx.attr.index > 0 }) {
                                        DshTapTarget { ctx.attr.onNavigate(-1) }
                                    }
                                }
                                Text {
                                    attr {
                                        text("${ctx.attr.index + 1} / ${totalCount()}")
                                        marginLeft(6f)
                                        marginRight(6f)
                                        fontSize(13f)
                                        color(ctx.attr.colors.labelTertiary)
                                    }
                                }
                                // 右箭头
                                View {
                                    attr {
                                        size(28f, 28f)
                                        borderRadius(14f)
                                        justifyContentCenter()
                                        alignItemsCenter()
                                        opacity(if (ctx.attr.index < totalCount() - 1) 1f else 0.3f)
                                    }
                                    Image {
                                        attr {
                                            src(ImageUri.commonAssets("chevron-right.svg"))
                                            size(16f, 16f)
                                            tintColor(ctx.attr.colors.labelSecondary)
                                        }
                                    }
                                    vif({ ctx.attr.index < totalCount() - 1 }) {
                                        DshTapTarget { ctx.attr.onNavigate(1) }
                                    }
                                }
                            }
                            // 占位撑开
                            View { attr { flex(1f) } }
                            // 跳过本题按钮：白底 + 边框
                            View {
                                attr {
                                    height(36f)
                                    paddingLeft(16f)
                                    paddingRight(16f)
                                    marginRight(10f)
                                    borderRadius(18f)
                                    backgroundColor(ctx.attr.colors.bgLayer1)
                                    border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                                    justifyContentCenter()
                                    alignItemsCenter()
                                }
                                Text {
                                    attr {
                                        text("跳过本题")
                                        fontSize(13f)
                                        color(ctx.attr.colors.labelSecondary)
                                    }
                                }
                                DshTapTarget { if (!ctx.attr.busy) ctx.attr.onSkip() }
                            }
                            // 提交按钮：品牌蓝（有选择时）/ 灰色（无选择时）
                            View {
                                attr {
                                    height(36f)
                                    paddingLeft(20f)
                                    paddingRight(20f)
                                    borderRadius(18f)
                                    backgroundColor(
                                        if (ctx.attr.busy) ctx.attr.colors.stateBusinessTertiary
                                        else if (ctx.attr.hasSelection) ctx.attr.colors.stateBusinessPrimary
                                        else ctx.attr.colors.borderL2
                                    )
                                    justifyContentCenter()
                                    alignItemsCenter()
                                }
                                Text {
                                    attr {
                                        text(if (ctx.attr.busy) "提交中" else "提交")
                                        fontSize(13f)
                                        fontWeightMedium()
                                        color(Color.WHITE)
                                    }
                                }
                                DshTapTarget { if (!ctx.attr.busy && ctx.attr.hasSelection) ctx.attr.onSubmit() }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal class DshQuestionFlowAttr : ComposeAttr() {
    var question: DshPendingQuestion? by observable(null)
    var index: Int by observable(0)
    var options: com.tencent.kuikly.core.reactive.collection.ObservableList<DshPendingQuestionOption> by observable(
        com.tencent.kuikly.core.reactive.collection.ObservableList(),
    )
    var selected: com.tencent.kuikly.core.reactive.collection.ObservableList<String> by observable(
        com.tencent.kuikly.core.reactive.collection.ObservableList(),
    )
    var custom: String by observable("")
    var hasSelection: Boolean by observable(false)
    var error: String by observable("")
    var busy: Boolean by observable(false)
    var onToggleOption: (String) -> Unit by observable({})
    var onCustomChange: (String) -> Unit by observable({})
    var onNavigate: (Int) -> Unit by observable({})
    var onSkip: () -> Unit by observable({})
    var onSubmit: () -> Unit by observable({})
    var onDismiss: () -> Unit by observable({})
    var onKeyboardHeightChange: (com.tencent.kuikly.core.views.KeyboardParams) -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshQuestionFlow(init: DshQuestionFlowView.() -> Unit) {
    addChild(DshQuestionFlowView(), init)
}

internal fun ViewContainer<*, *>.DshApprovalPanel(init: DshApprovalPanelView.() -> Unit) {
    addChild(DshApprovalPanelView(), init)
}

internal class DshQuestionPanelView : ComposeView<DshQuestionPanelAttr, ComposeEvent>() {
    override fun createAttr(): DshQuestionPanelAttr = DshQuestionPanelAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            vif({ ctx.attr.question != null }) {
                val question = ctx.attr.question ?: return@vif
                val item = question.questions.firstOrNull()
                vif({ item != null }) {
                    val current = item ?: return@vif
                    View {
                        attr {
                            marginBottom(8f)
                            flexDirectionColumn()
                            padding(10f)
                            borderRadius(12f)
                            backgroundColor(Color(0xFFF6F9FF))
                            border(Border(1f, BorderStyle.SOLID, Color(0xFFC7D9F2)))
                        }
                        Text {
                            attr {
                                text(current.header.ifEmpty { "需要你的回答" })
                                fontSize(12f)
                                color(Color(0xFF5D6F86))
                            }
                        }
                        Text {
                            attr {
                                text(current.question)
                                marginTop(4f)
                                fontSize(15f)
                                fontWeightMedium()
                                color(Color(0xFF2A384A))
                            }
                        }
                        vif({ current.detail.isNotEmpty() }) {
                            Text {
                                attr {
                                    text(current.detail)
                                    marginTop(4f)
                                    fontSize(12f)
                                    lineHeight(18f)
                                    color(Color(0xFF667687))
                                }
                            }
                        }
                        vfor({ ctx.attr.options }) { option ->
                            View {
                                attr {
                                    height(34f)
                                    marginTop(6f)
                                    paddingLeft(8f)
                                    paddingRight(8f)
                                    borderRadius(8f)
                                    backgroundColor(Color(0xFFEDF3FB))
                                    justifyContentCenter()
                                }
                                Text {
                                    attr {
                                        text(option.label)
                                        fontSize(13f)
                                        color(Color(0xFF324A66))
                                    }
                                }
                                event { click { ctx.attr.onToggleOption(option.label) } }
                            }
                        }
                        Text {
                            attr {
                                text(if (ctx.attr.busy) "提交中" else "提交")
                                height(34f)
                                marginTop(8f)
                                textAlignCenter()
                                fontSize(14f)
                                color(Color(0xFF2F6F4F))
                            }
                            event { click { if (!ctx.attr.busy) ctx.attr.onSubmit() } }
                        }
                    }
                }
            }
        }
    }
}

internal class DshQuestionPanelAttr : ComposeAttr() {
    var question: DshPendingQuestion? by observable(null)
    var options: com.tencent.kuikly.core.reactive.collection.ObservableList<DshPendingQuestionOption> by observable(
        com.tencent.kuikly.core.reactive.collection.ObservableList(),
    )
    var selected: com.tencent.kuikly.core.reactive.collection.ObservableList<String> by observable(
        com.tencent.kuikly.core.reactive.collection.ObservableList(),
    )
    var custom: String by observable("")
    var busy: Boolean by observable(false)
    var onToggleOption: (String) -> Unit by observable({})
    var onSubmit: () -> Unit by observable({})
}

internal fun ViewContainer<*, *>.DshQuestionPanel(init: DshQuestionPanelView.() -> Unit) {
    addChild(DshQuestionPanelView(), init)
}

private fun ViewContainer<*, *>.DshTapTarget(onClick: () -> Unit) {
    View {
        attr {
            absolutePositionAllZero()
            zIndex(2)
            backgroundColor(Color(0x00000000))
        }
        event { click { onClick() } }
    }
}

internal class DshAskQuestionCardAttr : ComposeAttr() {
    var card: DshAskQuestionCard? by observable(null)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

internal class DshAskQuestionCardView : ComposeView<DshAskQuestionCardAttr, ComposeEvent>() {
    override fun createAttr(): DshAskQuestionCardAttr = DshAskQuestionCardAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val card = ctx.attr.card ?: return { View { } }
        return when (card) {
            is DshAskQuestionCard.Answered -> {
                val questions = ObservableList<AnsweredItem>().also { it.addAll(card.questions) }
                val skippedLabel = card.skippedLabel
                {
                    View {
                        attr {
                            flexDirectionColumn()
                            marginTop(4f)
                            padding(0f, 4f, 4f, 4f)
                        }
                        vfor({ questions }) { item ->
                            val answers = ObservableList<String>().also { it.addAll(item.answers) }
                            View {
                                attr {
                                    flexDirectionColumn()
                                    marginTop(12f)
                                }
                                Text {
                                    attr {
                                        text(item.question)
                                        fontSize(14f)
                                        lineHeight(22f)
                                        color(ctx.attr.colors.labelTertiary)
                                    }
                                }
                                vif({ item.answers.isEmpty() }) {
                                    Text {
                                        attr {
                                            text(skippedLabel)
                                            fontSize(14f)
                                            lineHeight(22f)
                                            color(ctx.attr.colors.labelTertiary)
                                            marginTop(2f)
                                        }
                                    }
                                }
                                vif({ item.answers.isNotEmpty() }) {
                                    vfor({ answers }) { answer ->
                                        Text {
                                            attr {
                                                text(answer)
                                                fontSize(14f)
                                                lineHeight(22f)
                                                color(ctx.attr.colors.labelPrimary)
                                                marginTop(2f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is DshAskQuestionCard.Unanswered -> {
                val questions = ObservableList<UnansweredItem>().also { it.addAll(card.questions) }
                val verdict = card.verdict
                {
                    View {
                        attr {
                            flexDirectionColumn()
                            marginTop(4f)
                            padding(0f, 4f, 4f, 4f)
                        }
                        Text {
                            attr {
                                text(verdict)
                                fontSize(14f)
                                lineHeight(22f)
                                color(ctx.attr.colors.labelPrimary)
                                marginTop(12f)
                                marginBottom(8f)
                            }
                        }
                        vfor({ questions }) { item ->
                            Text {
                                attr {
                                    text(item.question)
                                    fontSize(14f)
                                    lineHeight(22f)
                                    color(ctx.attr.colors.labelTertiary)
                                    marginTop(6f)
                                    marginLeft(16f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshAskQuestionCard(init: DshAskQuestionCardView.() -> Unit) {
    addChild(DshAskQuestionCardView(), init)
}
