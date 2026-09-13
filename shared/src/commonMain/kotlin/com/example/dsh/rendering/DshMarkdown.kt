package com.example.dsh.rendering

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.ReactiveObserver
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.Text
import com.tencent.kuiklybase.components.DefaultComponentsBridge
import com.tencent.kuiklybase.components.markdownComponents
import com.tencent.kuiklybase.elements.markdownCodeFence
import com.tencent.kuiklybase.KuiklyStreamingMarkdown
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.MarkdownColors
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.MarkdownDimens
import com.tencent.kuiklybase.config.MarkdownTypography
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.streaming.MarkdownBlock
import com.tencent.kuiklybase.streaming.MarkdownStreamingState

/**
 * 对话正文（问答）基准字号（pt）。
 * 行高、段间距、列表项间距等排版值全部按此字号的百分比推导，
 * 调整字号时整体节奏自动同步（对齐 Apple HIG Body 的阅读尺度）。
 */
internal const val DSH_CHAT_BODY_FONT = 17f

/** 行高相对字号的倍数（≈1.5 倍行距）。 */
private const val DSH_CHAT_LINE_RATIO = 1.5f

/** 对话正文行高（pt），供问答两侧纯文本复用，保持与 Markdown 正文一致。 */
internal const val DSH_CHAT_BODY_LINE = DSH_CHAT_BODY_FONT * DSH_CHAT_LINE_RATIO

/** 段间距相对字号的倍数。 */
private const val DSH_CHAT_BLOCK_RATIO = 0.75f

/** 列表项间距相对字号的倍数（上下各取一半）。 */
private const val DSH_CHAT_LIST_ITEM_RATIO = 0.3f

/** DSH theme wrapper around KuiklyMarkdown's streaming renderer. */
internal class DshMarkdownView : ComposeView<DshMarkdownAttr, ComposeEvent>() {
    private val streamingState = MarkdownStreamingState()
    private var blockList by observableList<MarkdownBlock>()
    private var blockCount by observable(0)
    private var treeEpoch by observable(0)
    private var liveKey by observable("")
    private var lastContent = ""
    private var lastStreaming = false
    private var pendingContent = ""
    private var pendingStreaming = false
    private var flushScheduled = false

    override fun createAttr(): DshMarkdownAttr = DshMarkdownAttr()

    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    if (ctx.attr.contentWidth > 0f) {
                        width(ctx.attr.contentWidth)
                    }
                }
                // vforLazy will not create later siblings of a nested vfor
                // after the row is already built (paragraph stays, code fence
                // never mounts). Remount when the block count changes so
                // KuiklyStreamingMarkdown can paint every block, including
                // the component's own codeFence.
                vbind({ ctx.treeEpoch }) {
                    vfor({ ctx.blockList }) { block ->
                        View {
                            attr {
                                if (ctx.attr.contentWidth > 0f) {
                                    width(ctx.attr.contentWidth)
                                }
                            }
                            vbind({
                                val live = block.blockIndex == ctx.blockCount - 1
                                if (live) ctx.liveKey else block.id
                            }) {
                                KuiklyStreamingMarkdown(
                                    state = ctx.streamingState,
                                    block = block,
                                    config = ctx.markdownConfig(),
                                    components = markdownComponents(
                                        codeFence = { model, container ->
                                            container.View {
                                                View {
                                                    attr { height(32f); flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter() }
                                                    Text {
                                                        attr { text("复制代码"); fontSize(12f); color(model.config.colors.linkColor) }
                                                        event { click {
                                                            val code = dshCodeFenceSource(model.content, model.node)
                                                            val bridge = ctx.getPager().acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                                                            bridge.copyToPasteboard(code)
                                                            bridge.toast("代码已复制")
                                                        } }
                                                    }
                                                }
                                                markdownCodeFence(model.content, model.node, model.typography.code, model.config)
                                            }
                                        },
                                        paragraph = { model, container ->
                                            val text = model.content.substring(model.node.startOffset, model.node.endOffset)
                                            if (dshMathWebViewSupported() && DshMath.containsFormula(text)) {
                                                container.DshMathParagraph {
                                                    attr {
                                                        content = text
                                                        dark = ctx.attr.darkMode
                                                        fontSize = DSH_CHAT_BODY_FONT
                                                        contentWidth = ctx.attr.contentWidth
                                                    }
                                                }
                                            } else {
                                                DefaultComponentsBridge.paragraph(model, container)
                                            }
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        ReactiveObserver.bindValueChange(this) {
            val live = attr.liveContent
            val content = live?.invoke() ?: attr.content
            val streaming = attr.streamingProvider?.invoke() ?: attr.streaming
            ReactiveObserver.addLazyTaskUtilEndCollectDependency {
                scheduleBlocksUpdate(content, streaming)
            }
        }
    }

    override fun viewWillUnload() {
        ReactiveObserver.unbindValueChange(this)
        super.viewWillUnload()
    }

    private fun scheduleBlocksUpdate(content: String, streaming: Boolean) {
        pendingContent = content
        pendingStreaming = streaming
        if (!streaming || lastContent.isEmpty() || lastContent == DshStreamingMarkdown.PLACEHOLDER) {
            flushBlocksUpdate()
            return
        }
        if (flushScheduled) return
        flushScheduled = true
        setTimeout(pagerId, DshStreamingMarkdown.FRAME_MS) {
            flushScheduled = false
            flushBlocksUpdate()
        }
    }

    private fun flushBlocksUpdate() {
        val content = pendingContent
        val streaming = pendingStreaming
        if (content == lastContent && streaming == lastStreaming) return
        val endingStream = lastStreaming && !streaming
        if (content.isEmpty() && lastContent.isNotEmpty()) {
            lastStreaming = streaming
            return
        }
        if (
            lastContent.isNotEmpty() &&
            content.length < lastContent.length &&
            lastContent.startsWith(content)
        ) {
            lastStreaming = streaming
            return
        }
        if (streaming && !lastStreaming) {
            streamingState.reset()
            blockList.clear()
            blockCount = 0
            liveKey = ""
            treeEpoch += 1
        }
        lastContent = content
        lastStreaming = streaming
        val input = if (content.isEmpty() && streaming) DshStreamingMarkdown.PLACEHOLDER else content
        val closed = if (streaming) DshStreamingMarkdown.closeOpenFence(input) else input
        val toParse = if (dshMathWebViewSupported()) closed else DshMath.transform(closed)
        val next = streamingState.update(toParse, force = !streaming) ?: return
        blockList.diffUpdate(next) { old, new -> old.id == new.id }
        val countChanged = blockCount != blockList.size
        if (countChanged) {
            blockCount = blockList.size
        }
        val newLiveKey = next.lastOrNull()?.id.orEmpty()
        if (liveKey != newLiveKey) {
            liveKey = newLiveKey
        }
        if (countChanged || endingStream) {
            treeEpoch += 1
        }
        flexNode.markDirty()
    }

    private fun markdownConfig(): MarkdownConfig {
        val dark = attr.darkMode
        val text = if (dark) 0xFFF5F6F7 else 0xFF1F1F23
        // 所有排版值均由正文基准字号按百分比推导，字号与行高/段间距同步缩放。
        val body = DSH_CHAT_BODY_FONT
        val line = body * DSH_CHAT_LINE_RATIO
        val small = body - 2f
        val smallLine = small * DSH_CHAT_LINE_RATIO
        val headingLine = 1.3f
        val h1 = body * 1.6f
        val h2 = body * 1.35f
        val h3 = body * 1.2f
        val h4 = body * 1.06f
        val block = body * DSH_CHAT_BLOCK_RATIO
        val listItemGap = body * DSH_CHAT_LIST_ITEM_RATIO
        return MarkdownConfig(
            colors = MarkdownColors(
                text = text,
                codeBackground = if (dark) 0xFF242528 else 0xFFF9FAFB,
                inlineCodeBackground = if (dark) 0xFF34363A else 0xFFEBEEF2,
                dividerColor = if (dark) 0xFF45474B else 0xFFE5E5E5,
                tableBackground = if (dark) 0xFF202124 else 0xFFFAFAFA,
                blockQuoteBar = if (dark) 0xFF858990 else 0xFFA2A4A8,
                blockQuoteBackground = if (dark) 0xFF242528 else 0xFFF5F6F7,
                linkColor = if (dark) 0xFF78A4F8 else 0xFF4176E6,
                codeText = text,
            ),
            typography = MarkdownTypography(
                text = TextStyleConfig(fontSize = body, color = text, lineHeight = line),
                paragraph = TextStyleConfig(fontSize = body, color = text, lineHeight = line),
                code = TextStyleConfig(fontSize = small, fontFamily = "monospace", lineHeight = smallLine),
                inlineCode = TextStyleConfig(fontSize = small, fontFamily = "monospace"),
                h1 = TextStyleConfig(fontSize = h1, fontWeight = FontWeight.Bold, color = text, lineHeight = h1 * headingLine),
                h2 = TextStyleConfig(fontSize = h2, fontWeight = FontWeight.Bold, color = text, lineHeight = h2 * headingLine),
                h3 = TextStyleConfig(fontSize = h3, fontWeight = FontWeight.SemiBold, color = text, lineHeight = h3 * headingLine),
                h4 = TextStyleConfig(fontSize = h4, fontWeight = FontWeight.SemiBold, color = text, lineHeight = h4 * headingLine),
                h5 = TextStyleConfig(fontSize = body, fontWeight = FontWeight.SemiBold, color = text, lineHeight = body * headingLine),
                h6 = TextStyleConfig(fontSize = body, fontWeight = FontWeight.SemiBold, color = text, lineHeight = body * headingLine),
                quote = TextStyleConfig(fontSize = body, color = if (dark) 0xFFB7BBC2 else 0xFF61666D, lineHeight = line * 0.95f),
                ordered = TextStyleConfig(fontSize = body, color = text, lineHeight = line),
                bullet = TextStyleConfig(fontSize = body, color = text, lineHeight = line),
                list = TextStyleConfig(fontSize = body, color = text, lineHeight = line),
                table = TextStyleConfig(fontSize = body - 4f, color = text, lineHeight = (body - 4f) * DSH_CHAT_LINE_RATIO),
                textLink = TextStyleConfig(fontSize = body, color = if (dark) 0xFF78A4F8 else 0xFF4176E6, lineHeight = line),
            ),
            dimens = MarkdownDimens(
                dividerThickness = 1f,
                codeBackgroundCornerSize = 8f,
                blockQuoteThickness = 3f,
                blockQuoteCornerSize = 6f,
                tableCellWidth = 136f,
                tableCellPadding = 10f,
                tableCornerSize = 8f,
            ),
            codeHighlightDarkTheme = dark,
            codeHighlightEnabled = true,
            padding = com.tencent.kuiklybase.config.MarkdownPadding(
                // 段间距、列表间距等均按正文基准字号的百分比推导
                block = block,
                list = body * 0.5f,
                listItemTop = listItemGap,
                listItemBottom = listItemGap,
                listIndent = body * 1.2f,
                codeBlock = block,
                blockQuotePaddingLeft = block,
                blockQuoteBarPaddingLeft = body * 0.25f,
                blockQuoteTextVertical = body * 0.5f,
            ),
        )
    }
}

internal class DshMarkdownAttr : ComposeAttr() {
    var contentWidth: Float by observable(0f)
    var content: String by observable("")
    var streaming: Boolean by observable(false)
    var darkMode: Boolean by observable(false)
    var liveContent: (() -> String)? = null
    var streamingProvider: (() -> Boolean)? = null
}

internal fun ViewContainer<*, *>.DshMarkdown(init: DshMarkdownView.() -> Unit) {
    addChild(DshMarkdownView(), init)
}
