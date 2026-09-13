package com.example.dsh.rendering

import com.example.dsh.base.DshBottomSheet
import com.example.dsh.message.iconAsset
import com.example.dsh.export.DshSearchCard
import com.example.dsh.export.DshWebCard
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

private fun dshLinkHost(url: String): String {
    val withoutScheme = url.substringAfter("://", url)
    val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    return host.ifEmpty { url }
}

/** 链接的可见文案：优先标题，其次域名（对齐 WebBlock 的 linkLabel）。 */
private fun dshLinkLabel(url: String, title: String): String =
    title.ifEmpty { dshLinkHost(url) }

/**
 * 结构化 web 检索/抓取卡片（对齐电脑端 WebBlock）。
 * 检索：可选 answer + 编号来源列表（标题/域名可点、snippet、发布时间）；
 * 抓取：URL + HTTP 状态。整体左对齐。
 */
internal class DshWebResultCardAttr : ComposeAttr() {
    var card: com.example.dsh.export.DshWebCard? by observable(null)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

internal class DshWebResultCardView : ComposeView<DshWebResultCardAttr, ComposeEvent>() {
    override fun createAttr(): DshWebResultCardAttr = DshWebResultCardAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val card = ctx.attr.card ?: return { View { } }
        val linkColor = ctx.attr.colors.stateBusinessPrimary
        return {
            View {
                attr {
                    flexDirectionColumn()
                    alignItems(FlexAlign.FLEX_START)
                    marginTop(6f)
                    padding(12f, 14f, 12f, 14f)
                    borderRadius(10f)
                    backgroundColor(ctx.attr.colors.markdownCodeBlock)
                }
                if (card.isFetch) {
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("icon-globe14.svg"))
                                size(14f, 14f)
                                tintColor(linkColor)
                            }
                        }
                        Text {
                            attr {
                                text(card.url)
                                flex(1f)
                                marginLeft(6f)
                                fontSize(13f)
                                lineHeight(19f)
                                fontFamily("monospace")
                                color(linkColor)
                            }
                            event { click { ctx.dshOpenLink(card.url) } }
                        }
                    }
                    Text {
                        attr {
                            text(if (card.truncated) "HTTP ${card.statusCode} · 内容已截断" else "HTTP ${card.statusCode}")
                            marginTop(6f)
                            fontSize(12f)
                            color(ctx.attr.colors.labelSecondary)
                        }
                    }
                } else {
                    if (card.answer.isNotEmpty()) {
                        Text {
                            attr {
                                text(card.answer)
                                fontSize(14f)
                                lineHeight(21f)
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                    }
                    if (card.sources.isEmpty() && card.answer.isEmpty()) {
                        Text {
                            attr {
                                text("未找到相关结果")
                                fontSize(13f)
                                color(ctx.attr.colors.labelSecondary)
                            }
                        }
                    } else {
                        card.sources.forEachIndexed { index, source ->
                            View {
                                attr {
                                    flexDirectionColumn()
                                    alignItems(FlexAlign.FLEX_START)
                                    if (index > 0 || card.answer.isNotEmpty()) marginTop(10f)
                                }
                                View {
                                    attr {
                                        flexDirectionRow()
                                        alignItems(FlexAlign.FLEX_START)
                                    }
                                    Text {
                                        attr {
                                            text("${index + 1}.")
                                            fontSize(13f)
                                            lineHeight(20f)
                                            color(ctx.attr.colors.labelTertiary)
                                            marginRight(6f)
                                        }
                                    }
                                    Image {
                                        attr {
                                            src(ImageUri.commonAssets("icon-globe14.svg"))
                                            size(14f, 14f)
                                            marginTop(3f)
                                            marginRight(5f)
                                            tintColor(linkColor)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text(dshLinkLabel(source.url, source.title))
                                            flex(1f)
                                            fontSize(14f)
                                            lineHeight(20f)
                                            fontWeightMedium()
                                            color(linkColor)
                                        }
                                        event { click { ctx.dshOpenLink(source.url) } }
                                    }
                                }
                                if (source.snippet.isNotEmpty()) {
                                    Text {
                                        attr {
                                            text(source.snippet)
                                            marginTop(2f)
                                            fontSize(13f)
                                            lineHeight(19f)
                                            color(ctx.attr.colors.labelSecondary)
                                        }
                                    }
                                }
                                if (source.publishedAt.isNotEmpty()) {
                                    Text {
                                        attr {
                                            text(source.publishedAt)
                                            marginTop(2f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.labelTertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (card.truncated) {
                        Text {
                            attr {
                                text("结果已截断")
                                marginTop(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.labelTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshWebResultCard(init: DshWebResultCardView.() -> Unit) {
    addChild(DshWebResultCardView(), init)
}

/**
 * 结构化 grep/glob 结果卡片（对齐电脑端 SearchBlock）：
 * 顶部横幅摘要 + 复制，正文等宽展示文件分组命中 / 路径列表，整体左对齐。
 */
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
internal fun ViewContainer<*, *>.DshExpandedContentModal(
    payload: () -> DshExpandedPayload?,
    isJsonNodeExpanded: (String) -> Boolean = { false },
    onToggleJsonNode: (String) -> Unit = {},
    onClose: () -> Unit,
    colors: () -> DshColorTokens = { DshDefaultTheme.light },
) {
    DshBottomSheet(
        colors = colors,
        onClose = onClose,
        largeHeightRatio = 0.72f,
    ) {
        vif({ payload() != null }) {
            val p = payload()!!
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                }
                View {
                    attr {
                        height(52f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(16f)
                        paddingRight(8f)
                    }
                    vif({ p.iconAsset.isNotEmpty() }) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets(p.iconAsset))
                                size(16f, 16f)
                                tintColor(colors().labelTertiary)
                            }
                        }
                    }
                    Text {
                        attr {
                            text(p.title)
                            flex(1f)
                            marginLeft(8f)
                            fontSize(15f)
                            fontWeightMedium()
                            color(colors().labelPrimary)
                        }
                    }
                    View {
                        attr { size(36f, 36f); allCenter() }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("x.svg"))
                                size(18f, 18f)
                                tintColor(colors().labelSecondary)
                            }
                        }
                        event { click { onClose() } }
                    }
                }
                View {
                    attr {
                        height(1f)
                        backgroundColor(colors().borderL1)
                    }
                }
                Scroller {
                    attr { height(pagerData.pageViewHeight * 0.6f) }
                    View {
                        attr {
                            flexDirectionColumn()
                            paddingLeft(16f)
                            paddingRight(16f)
                            paddingBottom(20f)
                        }
                        // 弹层内同样保留左侧装饰线，且左右留白，避免输入/输出卡片贴屏幕边缘。
                        View {
                            attr {
                                flexDirectionColumn()
                                borderLeft(Border(2f, BorderStyle.SOLID, colors().borderL2))
                                paddingLeft(12f)
                            }
                            DshDisclosureBodyContent(
                                askCard = { p.askCard },
                                jsonContent = { p.jsonContent },
                                contextDetail = { p.contextDetail },
                                body = { p.body },
                                plainBody = { p.plainBody },
                                error = { p.error },
                                toolDetail = { p.toolDetail },
                                compact = { false },
                                isJsonNodeExpanded = isJsonNodeExpanded,
                                onToggleJsonNode = onToggleJsonNode,
                                colors = colors,
                                webCard = { p.webCard },
                                searchCard = { p.searchCard },
                            )
                        }
                    }
                }
            }
        }
    }
}
