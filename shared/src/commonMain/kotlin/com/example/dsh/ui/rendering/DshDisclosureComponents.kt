package com.example.dsh.ui.rendering

import com.example.dsh.ui.chat.DSH_CONNECTOR_GUTTER
import com.example.dsh.ui.chat.DSH_CONNECTOR_LINE_LEFT
import com.example.dsh.ui.chat.DSH_CONNECTOR_LINE_WIDTH
import com.example.dsh.message.boundedContextText
import com.example.dsh.message.iconAsset
import com.example.dsh.tool.DshAskQuestionCard
import com.example.dsh.export.DshSearchCard
import com.example.dsh.export.DshWebCard
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** The shared compact disclosure chrome used by context and tool rows. */
internal class DshDisclosureRowView : ComposeView<DshDisclosureRowAttr, ComposeEvent>() {
    /** 展开态长内容是否已铺开全文（仅展开且限高时有效）。 */
    private var bodyExpanded by observable(false)

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
                            if (ctx.attr.expandInModal) {
                                // 弹窗模式：行内不展开，交由页面底部弹层平铺明细。
                                ctx.attr.onRequestModal(ctx.attr.dshExpandedPayload())
                            } else {
                                ctx.attr.open = !ctx.attr.open
                                ctx.attr.onToggle()
                            }
                        }
                    }
                }
                // 标题之下：左侧留出沟槽放竖线，右侧放正文。竖线用绝对定位直接撑满
                // 整行高度，避免嵌套列在 Kuikly 下高度塌陷导致线不可见。
                View {
                    attr {
                        if (ctx.attr.connector) {
                            flexDirectionRow()
                            paddingLeft(DSH_CONNECTOR_GUTTER)
                        } else {
                            flexDirectionColumn()
                        }
                    }
                    // 竖线：从表头下方起笔，随正文高度拉伸。
                    if (ctx.attr.connector) {
                        View {
                            attr {
                                positionAbsolute()
                                left(DSH_CONNECTOR_LINE_LEFT)
                                top(0f)
                                bottom(0f)
                                width(DSH_CONNECTOR_LINE_WIDTH)
                                borderRadius(DSH_CONNECTOR_LINE_WIDTH / 2f)
                                // 竖线固定使用浅灰 #dcdcdc。
                                backgroundColor(Color(0xFFDCDCDC))
                            }
                        }
                    }
                    // 右列：正文内容。
                    vif({ ctx.attr.open && !ctx.attr.headerOnly && ctx.attr.bodyMaxHeight > 0f }) {
                        View {
                            attr {
                                if (ctx.attr.connector) flex(1f)
                                marginTop(6f)
                                marginBottom(8f)
                                flexDirectionColumn()
                                if (ctx.attr.bodyChrome) {
                                    padding(12f, 12f, 12f, 12f)
                                    borderRadius(8f)
                                    backgroundColor(ctx.attr.colors.bgModulePlatform)
                                }
                            }
                            val renderBody: ViewContainer<*, *>.(Boolean) -> Unit = { expanded ->
                                DshDisclosureBodyContent(
                                    askCard = { ctx.attr.askCard },
                                    jsonContent = { ctx.attr.jsonContent },
                                    contextDetail = { ctx.attr.contextDetail },
                                    body = { ctx.attr.body },
                                    plainBody = { ctx.attr.plainBody },
                                    error = { ctx.attr.errorSummary },
                                    toolDetail = { ctx.attr.toolDetail },
                                    compact = { ctx.attr.compact },
                                    isJsonNodeExpanded = ctx.attr.isJsonNodeExpanded,
                                    onToggleJsonNode = ctx.attr.onToggleJsonNode,
                                    colors = { ctx.attr.colors },
                                    webCard = { ctx.attr.webCard },
                                    searchCard = { ctx.attr.searchCard },
                                    onCopy = ctx.attr.onCopyToolCommand,
                                    bodyExpanded = expanded,
                                )
                            }
                            // 展开后的超长正文：放进限高滚动区，箭头固定在滚动区外。
                            vif({ ctx.bodyExpanded && dshDisclosureScrollable(ctx.attr) }) {
                                Scroller {
                                    attr { height(ctx.attr.bodyMaxHeight) }
                                    renderBody(true)
                                }
                            }
                            // 其余情况：短内容直接铺开，或收起态显示预览（不滚动）。
                            vif({ !(ctx.bodyExpanded && dshDisclosureScrollable(ctx.attr)) }) {
                                renderBody(ctx.bodyExpanded || !dshDisclosureCollapsible(ctx.attr))
                            }
                            // 底部展开/收起箭头：仅在需要折叠时出现，位于滚动区之外，
                            // 不随正文滚动。
                            vif({ dshDisclosureCollapsible(ctx.attr) && (ctx.attr.plainBody || ctx.attr.contextDetail != null) }) {
                                View {
                                    attr {
                                        height(30f)
                                        allCenter()
                                    }
                                    vbind({ ctx.bodyExpanded }) {
                                        Image {
                                            attr {
                                                src(ImageUri.commonAssets("chevron-down.svg"))
                                                size(16f, 16f)
                                                tintColor(ctx.attr.colors.labelTertiary)
                                                transform(Rotate(if (ctx.bodyExpanded) 180f else 0f))
                                            }
                                        }
                                    }
                                    DshTapTarget {
                                        ctx.bodyExpanded = !ctx.bodyExpanded
                                    }
                                }
                            }
                        }
                    }
                    vif({ ctx.attr.open && !ctx.attr.headerOnly && ctx.attr.bodyMaxHeight <= 0f }) {
                        View {
                            attr {
                                if (ctx.attr.connector) flex(1f)
                                marginTop(6f)
                                marginBottom(8f)
                                flexDirectionColumn()
                                if (ctx.attr.bodyChrome) {
                                    padding(12f, 12f, 12f, 12f)
                                    borderRadius(8f)
                                    backgroundColor(ctx.attr.colors.bgModulePlatform)
                                }
                            }
                            DshDisclosureBodyContent(
                                askCard = { ctx.attr.askCard },
                                jsonContent = { ctx.attr.jsonContent },
                                contextDetail = { ctx.attr.contextDetail },
                                body = { ctx.attr.body },
                                plainBody = { ctx.attr.plainBody },
                                error = { ctx.attr.errorSummary },
                                toolDetail = { ctx.attr.toolDetail },
                                compact = { ctx.attr.compact },
                                isJsonNodeExpanded = ctx.attr.isJsonNodeExpanded,
                                onToggleJsonNode = ctx.attr.onToggleJsonNode,
                                colors = { ctx.attr.colors },
                                webCard = { ctx.attr.webCard },
                                searchCard = { ctx.attr.searchCard },
                                onCopy = ctx.attr.onCopyToolCommand,
                            )
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
    /** 结构化 web 检索/抓取卡片（优先于通用文本）。 */
    var webCard: com.example.dsh.export.DshWebCard? by observable(null)
    /** 结构化 grep/glob 结果卡片（优先于通用文本）。 */
    var searchCard: com.example.dsh.export.DshSearchCard? by observable(null)
    /** 弹窗模式：展开时不铺在行内，而是把明细交给页面用底部弹层平铺展示。 */
    var expandInModal: Boolean by observable(false)
    var onRequestModal: (DshExpandedPayload) -> Unit by observable({})
    /** 工具/思考行左侧竖向装饰线，用于把连续的过程行视觉连接起来。 */
    var connector: Boolean by observable(false)
}

/** 把当前行内明细打包给弹窗渲染，字段与 attr 的展开 body 保持一致。 */
internal fun DshDisclosureRowAttr.dshExpandedPayload(): DshExpandedPayload = DshExpandedPayload(
    title = title,
    iconAsset = iconAsset,
    body = body,
    plainBody = plainBody,
    error = errorSummary,
    jsonContent = jsonContent,
    contextDetail = contextDetail,
    toolDetail = toolDetail,
    askCard = askCard,
    webCard = webCard,
    searchCard = searchCard,
)

/**
 * 展开明细的统一渲染：行内展开与弹窗平铺共用同一套内容，两处保持一致。
 * 各字段用 lambda 传入，保持对 attr 的响应式依赖（不要提前求值成局部变量）。
 */
internal fun ViewContainer<*, *>.DshDisclosureBodyContent(
    askCard: () -> DshAskQuestionCard?,
    jsonContent: () -> String,
    contextDetail: () -> DshContextDetail?,
    body: () -> String,
    plainBody: () -> Boolean,
    error: () -> Boolean,
    toolDetail: () -> DshToolDetail?,
    compact: () -> Boolean,
    isJsonNodeExpanded: (String) -> Boolean,
    onToggleJsonNode: (String) -> Unit,
    colors: () -> DshColorTokens,
    webCard: () -> com.example.dsh.export.DshWebCard? = { null },
    searchCard: () -> com.example.dsh.export.DshSearchCard? = { null },
    onCopy: (String) -> Unit = {},
    /** 收起态只展示前几行，展开态显示全文（仅纯文本 body 生效）。 */
    bodyExpanded: Boolean = true,
) {
    val hasStructured = { askCard() != null || webCard() != null || searchCard() != null }
    vif({ askCard() != null }) {
        DshAskQuestionCard {
            attr {
                card = askCard()
                this.colors = colors()
            }
        }
    }
    vif({ askCard() == null && webCard() != null }) {
        DshWebResultCard {
            attr {
                card = webCard()
                this.colors = colors()
            }
        }
    }
    vif({ askCard() == null && webCard() == null && searchCard() != null }) {
        DshSearchResultCard {
            attr {
                card = searchCard()
                this.colors = colors()
                this.onCopy = onCopy
            }
        }
    }
    vif({ !hasStructured() && jsonContent().isNotEmpty() }) {
        DshJsonTree {
            attr {
                content = jsonContent()
                this.isExpanded = isJsonNodeExpanded
                this.onToggle = onToggleJsonNode
                this.colors = colors()
            }
        }
    }
    vif({ !hasStructured() && jsonContent().isEmpty() && contextDetail() != null }) {
        // 与 Think 一致：收起态先给纯文本预览（限行 + 省略号），点按钮才铺开结构化明细。
        if (bodyExpanded) {
            DshContextDetails {
                attr {
                    detail = contextDetail()
                    this.colors = colors()
                }
            }
        } else {
            DshLinkText {
                attr {
                    content = dshContextDetailPreview(contextDetail())
                    fontSize = if (compact()) 13f else 14f
                    lineHeight = 24f
                    textColor = colors().labelSecondary
                    this.colors = colors()
                }
            }
        }
    }
    vif({ !hasStructured() && jsonContent().isEmpty() && contextDetail() == null && body().isNotEmpty() }) {
        if (plainBody()) {
            DshLinkText {
                attr {
                    content = if (bodyExpanded) body() else dshPreviewText(body())
                    fontSize = if (compact()) 13f else 14f
                    lineHeight = 24f
                    textColor = colors().labelSecondary
                    this.colors = colors()
                }
            }
        } else {
            DshLongText {
                attr {
                    content = body()
                    this.error = error()
                    this.colors = colors()
                }
            }
        }
    }
    vif({ !hasStructured() && jsonContent().isEmpty() && contextDetail() == null && toolDetail() != null }) {
        DshToolDetails {
            attr {
                detail = toolDetail()
                this.colors = colors()
            }
        }
    }
    vif({ !hasStructured() && jsonContent().isEmpty() && contextDetail() == null && body().isEmpty() && toolDetail() == null }) {
        Text {
            attr {
                text("暂无输出")
                fontSize(12f)
                color(colors().labelTertiary)
                margin(10f)
            }
        }
    }
}

/** 网页链接正文：逐行渲染，含 URL 的行整行可点击并在内置 WebView 打开。 */
internal class DshLinkTextAttr : ComposeAttr() {
    var content: String by observable("")
    var fontSize: Float by observable(13f)
    var lineHeight: Float by observable(21f)
    var textColor: Color by observable(Color(0xFF1F1F23))
    var monospace: Boolean by observable(false)
    var indent: Boolean by observable(true)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

internal class DshLinkTextView : ComposeView<DshLinkTextAttr, ComposeEvent>() {
    override fun createAttr(): DshLinkTextAttr = DshLinkTextAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionColumn()
                    if (ctx.attr.indent) {
                        marginTop(6f)
                        marginLeft(22f)
                        marginRight(22f)
                    }
                }
                vfor({ ObservableList<String>().also { it.addAll(ctx.attr.content.split("\n")) } }) { line ->
                    val url = dshFirstUrl(line)
                    if (url != null) {
                        Text {
                            attr {
                                text(line.trim())
                                fontSize(ctx.attr.fontSize)
                                lineHeight(ctx.attr.lineHeight)
                                color(ctx.attr.colors.stateBusinessPrimary)
                                if (ctx.attr.monospace) fontFamily("monospace")
                            }
                            event { click { ctx.dshOpenLink(url) } }
                        }
                    } else {
                        Text {
                            attr {
                                text(line)
                                fontSize(ctx.attr.fontSize)
                                lineHeight(ctx.attr.lineHeight)
                                color(ctx.attr.textColor)
                                if (ctx.attr.monospace) fontFamily("monospace")
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshLinkText(init: DshLinkTextView.() -> Unit) {
    addChild(DshLinkTextView(), init)
}

private val dshUrlRegex = Regex("https?://[^\\s)\\]}>\"']+")

internal fun dshFirstUrl(text: String): String? = dshUrlRegex.find(text)?.value

/** 收起态的纯文本预览：保留前几行并加省略号，避免内部滚动。 */
internal fun dshPreviewText(text: String): String {
    val lines = text.split("\n")
    if (lines.size <= DSH_BODY_PREVIEW_LINES) return text
    return lines.take(DSH_BODY_PREVIEW_LINES).joinToString("\n") + "\n…"
}

private const val DSH_BODY_PREVIEW_LINES = 4

/**
 * 上下文明细的完整纯文本：把结构化字段拼成文本，供收起预览与是否需要滚动判断使用。
 */
internal fun dshContextDetailText(detail: DshContextDetail?): String {
    if (detail == null) return ""
    val parts = buildList {
        if (detail.relaySender.isNotEmpty()) add("来自 ${detail.relaySender}")
        detail.catalog.forEach { item ->
            add(listOf(item.name, item.description).filter { it.isNotEmpty() }.joinToString("："))
        }
        detail.instructions.forEach { item ->
            val action = when (item.action) {
                "remove" -> "已移除"
                "replace" -> "已更新"
                else -> "已加载"
            }
            add("${item.path} $action")
        }
        detail.recalls.forEach { item ->
            add(
                "${item.label}：保留 ${item.retainedMessages} 条，省略 ${item.omittedMessages} 条" +
                    if (item.truncated) "，已截断" else "",
            )
        }
        detail.sections.forEach { item ->
            add(listOf(item.title, boundedContextText(item.body)).filter { it.isNotEmpty() }.joinToString("\n"))
        }
        if (detail.body.isNotEmpty()) add(detail.body)
    }
    return parts.joinToString("\n\n")
}

/**
 * 上下文明细的收起态预览文本：拼成纯文本后按行截断，
 * 让「上下文注入」与 Think 一样先折叠、点按钮才铺开完整明细。
 */
internal fun dshContextDetailPreview(detail: DshContextDetail?): String =
    dshPreviewText(dshContextDetailText(detail))

/** 单行的估算高度，用于判断正文展开后是否需要限高滚动。 */
private const val DSH_BODY_LINE_HEIGHT = 24f

/** 明细正文（含上下文明细）的完整可读文本。 */
private fun dshDisclosureFullText(attr: DshDisclosureRowAttr): String =
    if (attr.contextDetail != null) dshContextDetailText(attr.contextDetail) else attr.body

/** 收起预览是否会截断正文（决定底部展开箭头是否出现）。 */
internal fun dshDisclosureCollapsible(attr: DshDisclosureRowAttr): Boolean {
    val text = dshDisclosureFullText(attr)
    return text.isNotBlank() && dshPreviewText(text) != text
}

/** 展开后正文是否超过限高、需要内部滚动（估算视觉行数，含换行折行）。 */
internal fun dshDisclosureScrollable(attr: DshDisclosureRowAttr): Boolean {
    val text = dshDisclosureFullText(attr)
    if (text.isBlank() || attr.bodyMaxHeight <= 0f) return false
    val sourceLines = text.count { it == '\n' } + 1
    val estimatedLines = maxOf(sourceLines, (text.length + DSH_BODY_CHARS_PER_LINE - 1) / DSH_BODY_CHARS_PER_LINE)
    return estimatedLines * DSH_BODY_LINE_HEIGHT > attr.bodyMaxHeight
}

private const val DSH_BODY_CHARS_PER_LINE = 24

/** 在内置 WebView 页打开链接（与 Markdown 链接、导出等共用 link_view 页面）。 */
internal fun ComposeView<*, *>.dshOpenLink(url: String) {
    val target = url.trim()
    if (target.isEmpty()) return
    runCatching {
        getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "link_view",
            JSONObject().apply {
                put("pageName", "link_view")
                put("url", target)
            },
        )
    }
}
