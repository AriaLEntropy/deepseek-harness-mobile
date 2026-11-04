package com.example.dsh.ui.chat

import com.example.dsh.message.DshMessageRole
import com.example.dsh.ui.home.BusApprovalQuestion
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderRectRadius
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.RichText
import com.tencent.kuikly.core.views.Span
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 对话页消息流的构建函数（视觉基线：.artifacts/agentbus-chat-detail.html）
 *
 * 页面只管数据、轮询与命令，这里只管画：颜色全部从 [BusChatPalette] 取，
 * 尺寸优先用 [BusChatTokens]，设计稿里没有收进 token 的小值就地写数字
 *
 * 行与行之间的间距（换轮 24 / 同轮 8 / 卡片 12）由页面按角色决定，
 * 这些构建函数不自带上下外边距，避免页面再加一层时叠成双倍
 *
 * 正文内容在构建时读一次：流式文本要靠页面替换 [BusChatRow] 触发重绘，
 * 只有布尔与文案这类高频状态才走 lambda
 */

// ---------- 空状态与提示 ----------

/**
 * 空状态：`Kuik` 彩色字标 + 一行副文案，在消息区内垂直居中
 *
 * 设计稿是 `.empty{padding:0 14px 96px}`，底部 96 的留白把字标往上顶，
 * 视觉中心落在输入条上方的空白里，而不是屏幕正中
 */
internal fun ViewContainer<*, *>.BusChatEmptyState(palette: BusChatPalette) {
    View {
        attr {
            flex(1f)
            flexDirectionColumn()
            justifyContentCenter()
            alignItemsFlexStart()
            paddingLeft(BusChatTokens.pageSide)
            paddingRight(BusChatTokens.pageSide)
            paddingBottom(busChatPx(96f))
        }
        // 字标三段只有颜色与字重不同；用一个横排的三个 Text 保证同字号同基线
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text("Kuik")
                    fontSize(BusChatTokens.wordmark)
                    lineHeight(BusChatTokens.wordmark * 1.15f)
                    fontWeightSemiBold()
                    color(palette.kuik)
                    letterSpacing(busChatPx(-0.5f))
                }
            }
            Text {
                attr {
                    text("Agent")
                    fontSize(BusChatTokens.wordmark)
                    lineHeight(BusChatTokens.wordmark * 1.15f)
                    fontWeightNormal()
                    color(palette.ink)
                    letterSpacing(busChatPx(-0.5f))
                }
            }
            Text {
                attr {
                    text("Bus")
                    fontSize(BusChatTokens.wordmark)
                    lineHeight(BusChatTokens.wordmark * 1.15f)
                    fontWeightNormal()
                    color(palette.deep)
                    letterSpacing(busChatPx(-0.5f))
                }
            }
        }
        View {
            attr {
                flexDirectionRow()
                marginTop(busChatPx(10f))
                maxWidth(busChatPx(270f))
            }
            Text {
                attr {
                    text("电脑上跑着的 Agent，")
                    fontSize(BusChatTokens.emptyTag)
                    lineHeight(BusChatTokens.emptyTag * 1.6f)
                    color(palette.mut)
                }
            }
            Text {
                attr {
                    text("在手机上接着看")
                    fontSize(BusChatTokens.emptyTag)
                    lineHeight(BusChatTokens.emptyTag * 1.6f)
                    color(palette.deep)
                }
            }
        }
    }
}

/** 消息流里的居中小字（例如「更早的记录在电脑上」），不带底色与分割线 */
internal fun ViewContainer<*, *>.BusChatHint(palette: BusChatPalette, text: String) {
    Text {
        attr {
            this.text(text)
            fontSize(busChatPx(11f))
            color(palette.faint)
            alignSelfCenter()
            marginTop(busChatPx(7f))
            marginBottom(busChatPx(7f))
        }
    }
}

// ---------- 消息行 ----------

/**
 * 一行消息：用户气泡、助手正文、单条工具行、工具组折叠行
 *
 * [contentWidth] 是消息区可用宽度（已扣掉左右 14），由页面算好传进来；
 * 用户气泡的上限是它的 82%，助手正文直接铺满
 */
internal fun ViewContainer<*, *>.BusChatMessageRow(
    row: BusChatRow,
    palette: BusChatPalette,
    contentWidth: Float,
    streaming: () -> Boolean,
    expanded: () -> Boolean,
    onToggleTools: () -> Unit,
    onSelectStep: (Int) -> Unit,
) {
    val width = contentWidth.coerceAtLeast(0f)
    when (row) {
        is BusChatRow.ToolGroup -> BusChatToolGroupRow(row, palette, expanded, onToggleTools, onSelectStep)
        is BusChatRow.Message -> when (row.message.role) {
            DshMessageRole.USER -> BusChatUserBubble(row.message.content, palette, width)
            DshMessageRole.TOOL -> BusChatToolRow(row, palette, expanded, onToggleTools, onSelectStep)
            else -> BusChatAssistantBody(row.message.content, palette, width, streaming)
        }
    }
}

/**
 * 用户气泡：右对齐白玻璃，右下角收成 6
 *
 * 圆角顺序按 Kuikly 的 BorderRectRadius(左上, 右上, 左下, 右下)，
 * 对应设计稿的 `border-radius:18px 18px 18px 6px`
 */
private fun ViewContainer<*, *>.BusChatUserBubble(
    content: String,
    palette: BusChatPalette,
    contentWidth: Float,
) {
    val body = content.trim()
    View {
        attr {
            alignSelfFlexEnd()
            maxWidth(contentWidth * BusChatTokens.bubbleMaxRatio)
            flexDirectionColumn()
            borderRadius(
                BorderRectRadius(
                    BusChatTokens.bubbleRadius,
                    BusChatTokens.bubbleRadius,
                    BusChatTokens.bubbleRadius,
                    BusChatTokens.bubbleTail,
                ),
            )
            backgroundColor(palette.surface)
            border(Border(1f, BorderStyle.SOLID, palette.surfaceBorder))
            boxShadow(BoxShadow(0f, 8f, 22f, BusChatTokens.cardShadow))
            paddingTop(BusChatTokens.bubblePaddingV)
            paddingBottom(BusChatTokens.bubblePaddingV)
            paddingLeft(BusChatTokens.bubblePaddingH)
            paddingRight(BusChatTokens.bubblePaddingH)
        }
        if (body.isEmpty()) {
            // 只发了图片时正文为空，气泡仍要出现，用「图片」占位顶住一行高度
            Text {
                attr {
                    text("图片")
                    fontSize(busChatPx(14f))
                    color(palette.faint)
                }
            }
        } else {
            Text {
                attr {
                    text(body)
                    fontSize(BusChatTokens.bodySize)
                    lineHeight(BusChatTokens.bodyLine)
                    color(palette.ink)
                    // 气泡只给了 maxWidth，Text 还要自己知道上限才会折行
                    maxWidth(
                        contentWidth * BusChatTokens.bubbleMaxRatio -
                            BusChatTokens.bubblePaddingH * 2f,
                    )
                    lines(Int.MAX_VALUE)
                }
            }
        }
    }
}

/** 助手正文：按 [busChatBlocks] 的分块渲染，最后一块之后挂流式光标 */
private fun ViewContainer<*, *>.BusChatAssistantBody(
    content: String,
    palette: BusChatPalette,
    contentWidth: Float,
    streaming: () -> Boolean,
) {
    val blocks = busChatBlocks(content)
    View {
        attr {
            width(contentWidth)
            flexDirectionColumn()
        }
        blocks.forEachIndexed { index, block ->
            BusChatBlockView(
                block = block,
                first = index == 0,
                last = index == blocks.lastIndex,
                palette = palette,
            )
        }
        // 光标是 2×16 的实心竖条，只能挂在最后一块之后；行内跟字太靠近会压住标点，
        // 所以留 2 的间距，看起来仍然贴着正文
        vif({ streaming() }) {
            View {
                attr {
                    width(busChatPx(2f))
                    height(busChatPx(16f))
                    backgroundColor(palette.deep)
                    opacity(0.7f)
                    marginTop(busChatPx(2f))
                }
            }
        }
    }
}

/** 单个正文块：小标题、段落、方点列表、有序步骤、代码井 */
private fun ViewContainer<*, *>.BusChatBlockView(
    block: BusChatBlock,
    first: Boolean,
    last: Boolean,
    palette: BusChatPalette,
) {
    when (block) {
        is BusChatBlock.Heading -> Text {
            attr {
                text(block.text)
                fontSize(BusChatTokens.bodySize)
                fontWeightSemiBold()
                lineHeight(BusChatTokens.bodyLine)
                color(palette.ink)
                lines(Int.MAX_VALUE)
                // 小标题只靠字重与灰度区分，不放大字号
                marginTop(if (first) 0f else busChatPx(14f))
                marginBottom(if (last) 0f else busChatPx(6f))
            }
        }

        is BusChatBlock.Paragraph -> Text {
            attr {
                text(block.text)
                fontSize(BusChatTokens.bodySize)
                lineHeight(BusChatTokens.bodyLine)
                color(palette.ink)
                lines(Int.MAX_VALUE)
                marginBottom(if (last) 0f else BusChatTokens.paragraphGap)
            }
        }

        is BusChatBlock.Bullets -> View {
            attr {
                flexDirectionColumn()
                marginBottom(if (last) 0f else busChatPx(10f))
            }
            block.items.forEachIndexed { index, item ->
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsFlexStart()
                        if (index > 0) marginTop(busChatPx(5f))
                    }
                    View {
                        attr {
                            size(BusChatTokens.bulletDot, BusChatTokens.bulletDot)
                            borderRadius(busChatPx(1f))
                            backgroundColor(palette.faint)
                            // 方点落在首行 x 高度上，而不是贴着行框顶部
                            marginTop(busChatPx(10f))
                        }
                    }
                    Text {
                        attr {
                            text(item)
                            fontSize(BusChatTokens.bodySize)
                            lineHeight(BusChatTokens.bodySize * 1.6f)
                            color(palette.ink)
                            lines(Int.MAX_VALUE)
                            marginLeft(busChatPx(8f))
                            flex(1f)
                        }
                    }
                }
            }
        }

        is BusChatBlock.Numbered -> View {
            attr {
                flexDirectionColumn()
                marginBottom(if (last) 0f else BusChatTokens.paragraphGap)
            }
            block.items.forEachIndexed { index, item ->
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsFlexStart()
                        if (index > 0) marginTop(busChatPx(5f))
                    }
                    Text {
                        attr {
                            text("${index + 1}.")
                            fontSize(BusChatTokens.toolTitle)
                            fontWeightSemiBold()
                            lineHeight(BusChatTokens.bodySize * 1.6f)
                            color(palette.mut)
                        }
                    }
                    Text {
                        attr {
                            text(item)
                            fontSize(BusChatTokens.bodySize)
                            lineHeight(BusChatTokens.bodySize * 1.6f)
                            color(palette.ink)
                            lines(Int.MAX_VALUE)
                            marginLeft(busChatPx(8f))
                            flex(1f)
                        }
                    }
                }
            }
        }

        is BusChatBlock.Code -> View {
            attr {
                backgroundColor(palette.field)
                borderRadius(BusChatTokens.codeRadius)
                paddingTop(busChatPx(8f))
                paddingBottom(busChatPx(8f))
                paddingLeft(busChatPx(10f))
                paddingRight(busChatPx(10f))
                marginBottom(if (last) 0f else BusChatTokens.paragraphGap)
            }
            Text {
                attr {
                    text(block.text)
                    fontSize(BusChatTokens.codeSize)
                    lineHeight(BusChatTokens.codeSize * 1.6f)
                    color(palette.deep)
                    // 超过 8 行截断，剩下的交给电脑上的完整记录
                    lines(8)
                    textOverFlowTail()
                }
            }
        }
    }
}

/**
 * 单条工具消息：一行灰字，没有卡片底也没有分割线
 *
 * 左边 13/600 的标签，中间 12.5/400 的单行摘要，右边 14 的下箭头；
 * 与工具组同一套三层结构：点这一行就地展开出那一条步骤，再点进步详情浮层
 */
private fun ViewContainer<*, *>.BusChatToolRow(
    row: BusChatRow.Message,
    palette: BusChatPalette,
    expanded: () -> Boolean,
    onToggleTools: () -> Unit,
    onSelectStep: (Int) -> Unit,
) {
    val message = row.message
    View {
        attr { flexDirectionColumn(); alignSelfStretch() }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                alignSelfStretch()
            }
            event { click { onToggleTools() } }
        Text {
            attr {
                text(busChatToolLabel(message))
                fontSize(BusChatTokens.toolTitle)
                fontWeightSemiBold()
                color(palette.ink2)
            }
        }
        Text {
            attr {
                text(busChatToolLine(message))
                fontSize(BusChatTokens.toolSummary)
                fontWeightNormal()
                color(palette.mut)
                // 摘要吃掉剩余宽度，箭头才被顶到最右
                flex(1f)
                lines(1)
                textOverFlowTail()
                marginLeft(busChatPx(8f))
            }
        }
        Image {
            attr {
                // 与工具组同一套：收起指右、展开指下
                src(ImageUri.commonAssets(if (expanded()) "chevron-down.svg" else "chevron-right.svg"))
                size(busChatPx(14f), busChatPx(14f))
                tintColor(palette.faint)
                marginLeft(busChatPx(8f))
            }
        }
        }
        vif({ expanded() }) {
            BusChatInlineSteps(busChatSteps(listOf(message)), palette, onSelectStep)
        }
    }
}

/**
 * 工具组折叠行：文案来自 [busChatToolSummary]，同样一行灰字
 *
 * 点这一行在对话流里就地展开步骤列表（稿子③），再点其中某一行才进单步详情浮层；
 * [expanded] 读在 attr 与 vif 里，页面换展开 key 时箭头方向与列表一起跟上。
 * 箭头按通用约定：收起指右、展开指下（稿子③注的「翻上去」与此冲突，以本实现为准）
 */
private fun ViewContainer<*, *>.BusChatToolGroupRow(
    row: BusChatRow.ToolGroup,
    palette: BusChatPalette,
    expanded: () -> Boolean,
    onToggleTools: () -> Unit,
    onSelectStep: (Int) -> Unit,
) {
    View {
        attr { flexDirectionColumn(); alignSelfStretch() }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                alignSelfStretch()
            }
            event { click { onToggleTools() } }
        val parts = busChatToolSummaryParts(row.tools)
        Text {
            attr {
                text(busChatToolSummaryHead(parts.head, expanded()))
                fontSize(BusChatTokens.toolTitle)
                fontWeightSemiBold()
                color(palette.ink2)
                lines(1)
            }
        }
        if (parts.tail.isNotBlank()) {
            Text {
                attr {
                    text(parts.tail)
                    fontSize(BusChatTokens.toolSummary)
                    fontWeightNormal()
                    color(palette.mut)
                    lines(1)
                    textOverFlowTail()
                }
            }
        }
        View { attr { flex(1f) } }
        Image {
            attr {
                // 收起态指右、展开态指下；不再靠 down 翻 180 变成朝上
                src(ImageUri.commonAssets(if (expanded()) "chevron-down.svg" else "chevron-right.svg"))
                size(busChatPx(14f), busChatPx(14f))
                tintColor(palette.faint)
                marginLeft(busChatPx(8f))
            }
        }
        }
        vif({ expanded() }) {
            BusChatInlineSteps(busChatSteps(row.tools), palette, onSelectStep)
        }
    }
}

/**
 * 内联步骤列表：稿子③「折叠一行与单步浮层之间的中间层」
 *
 * 不弹层、不遮内容，就是把折叠那一行就地撑开，正文继续排在下面、滚动位置不动；
 * 行样式与底部浮层里那套 .step 同源（标签 13/600 + 摘要 12.5），失败行只在右侧挂一枚红叹号
 * 不写「失败」二字，行尾右箭头表示还能点进单步详情
 */
private fun ViewContainer<*, *>.BusChatInlineSteps(
    steps: List<BusChatStep>,
    palette: BusChatPalette,
    onSelectStep: (Int) -> Unit,
) {
    steps.forEachIndexed { index, step ->
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                alignSelfStretch()
                paddingTop(busChatPx(7f))
                paddingBottom(busChatPx(7f))
            }
            event { click { onSelectStep(index) } }
            Text {
                attr {
                    text(step.label)
                    fontSize(busChatPx(13f))
                    fontWeightSemiBold()
                    color(palette.ink2)
                }
            }
            Text {
                attr {
                    text(step.summary)
                    fontSize(BusChatTokens.toolSummary)
                    color(palette.deep)
                    marginLeft(busChatPx(8f))
                    flex(1f)
                    lines(1)
                    textOverFlowTail()
                }
            }
            if (step.failed) {
                Image {
                    attr {
                        src(ImageUri.commonAssets("warning-outline.svg"))
                        size(busChatPx(16f), busChatPx(16f))
                        tintColor(palette.bad)
                        marginLeft(busChatPx(6f))
                    }
                }
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("chevron-right.svg"))
                    size(busChatPx(16f), busChatPx(16f))
                    tintColor(palette.mut)
                    marginLeft(busChatPx(6f))
                }
            }
        }
    }
}

// ---------- 流式行 ----------

/**
 * 运行中的一行：7 圆点 + 13 灰字
 *
 * 设计稿的圆点是呼吸动画，Kuikly 这边先用固定 .7 透明度，
 * 右侧的「15/18」计数协议里没有，不编一个数字出来
 */
internal fun ViewContainer<*, *>.BusChatStreamingLine(palette: BusChatPalette, label: String) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
        }
        View {
            attr {
                size(busChatPx(7f), busChatPx(7f))
                borderRadius(busChatPx(3.5f))
                backgroundColor(palette.deep)
                opacity(0.7f)
            }
        }
        Text {
            attr {
                text(label)
                fontSize(BusChatTokens.streamLabel)
                color(palette.mut)
                marginLeft(busChatPx(8f))
                lines(1)
                textOverFlowTail()
            }
        }
    }
}

// ---------- 审批卡 ----------

/**
 * 审批卡：白玻璃 r16，头部图标井 + 琥珀点标题，中间是 diff/命令井，底部两个按钮
 *
 * [busy] 为真时整卡压到 .6 并停止响应点击，避免一脚油门连发两次批准；
 * 失败信息放在卡片最下方，用 11 号红字，不弹窗也不换行成块
 */
internal fun ViewContainer<*, *>.BusChatApprovalCard(
    palette: BusChatPalette,
    request: BusChatPendingRequest,
    busy: () -> Boolean,
    error: () -> String,
    questions: List<BusApprovalQuestion> = emptyList(),
    selection: Map<String, List<String>> = emptyMap(),
    onToggleOption: (String, String) -> Unit = { _, _ -> },
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val codeLines = busChatApprovalCodeLines(request.detail)
    View {
        attr {
            alignSelfStretch()
            flexDirectionColumn()
            backgroundColor(palette.surface)
            border(Border(1f, BorderStyle.SOLID, palette.surfaceBorder))
            borderRadius(BusChatTokens.approvalRadius)
            paddingTop(BusChatTokens.approvalPadding)
            paddingLeft(BusChatTokens.approvalPadding)
            paddingRight(BusChatTokens.approvalPadding)
            // 设计稿 `padding:12px 12px 10px`：底部比上面少 2，按钮行不显得空
            paddingBottom(busChatPx(10f))
            boxShadow(BoxShadow(0f, 14f, 34f, BusChatTokens.cardShadow))
            opacity(if (busy()) 0.6f else 1f)
        }
        // 头部：28 圆图标井 + 琥珀点 + 单行标题
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    size(BusChatTokens.well, BusChatTokens.well)
                    borderRadius(BusChatTokens.well / 2f)
                    backgroundColor(palette.field)
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets(busChatApprovalAsset(request.kind)))
                        size(busChatPx(18f), busChatPx(18f))
                        tintColor(palette.deep)
                    }
                }
            }
            View {
                attr {
                    flex(1f)
                    flexDirectionRow()
                    alignItemsCenter()
                    marginLeft(busChatPx(9f))
                }
                View {
                    attr {
                        size(busChatPx(6f), busChatPx(6f))
                        borderRadius(busChatPx(3f))
                        backgroundColor(palette.warn)
                        marginRight(busChatPx(6f))
                    }
                }
                Text {
                    attr {
                        text(busChatApprovalTitle(request.kind, request.summary))
                        fontSize(busChatPx(13.5f))
                        fontWeightSemiBold()
                        lineHeight(busChatPx(18f))
                        color(palette.ink)
                        flex(1f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
            }
        }
        // 每一行细节一个灰井，最多两行（busChatApprovalCodeLines 已截断），单行省略
        codeLines.forEach { line ->
            View {
                attr {
                    backgroundColor(palette.field)
                    borderRadius(BusChatTokens.codeRadius)
                    paddingTop(busChatPx(8f))
                    paddingBottom(busChatPx(8f))
                    paddingLeft(busChatPx(10f))
                    paddingRight(busChatPx(10f))
                    marginTop(busChatPx(9f))
                }
                Text {
                    attr {
                        text(line)
                        fontSize(BusChatTokens.codeSize)
                        lineHeight(BusChatTokens.codeSize * 1.6f)
                        color(palette.deep)
                        lines(1)
                        textOverFlowTail()
                    }
                }
            }
        }
        if (questions.isNotEmpty()) {
            questions.forEach { question ->
                Text {
                    attr {
                        text(question.question)
                        fontSize(busChatPx(12.5f))
                        fontWeightSemiBold()
                        color(palette.ink)
                        marginTop(busChatPx(10f))
                        lines(Int.MAX_VALUE)
                    }
                }
                View {
                    attr {
                        flexDirectionRow()
                        flexWrap(com.tencent.kuikly.core.layout.FlexWrap.WRAP)
                        marginTop(busChatPx(6f))
                    }
                    val selected = selection[question.id].orEmpty()
                    question.options.forEach { (label, _) ->
                        val on = label in selected
                        View {
                            attr {
                                padding(busChatPx(5f), busChatPx(10f), busChatPx(5f), busChatPx(10f))
                                marginRight(busChatPx(6f))
                                marginBottom(busChatPx(6f))
                                borderRadius(busChatPx(999f))
                                backgroundColor(if (on) palette.solid else palette.surface)
                                border(
                                    Border(
                                        1f,
                                        BorderStyle.SOLID,
                                        if (on) palette.solid else palette.line,
                                    ),
                                )
                            }
                            event { click { if (!busy()) onToggleOption(question.id, label) } }
                            Text {
                                attr {
                                    text(label)
                                    fontSize(busChatPx(12f))
                                    fontWeightSemiBold()
                                    color(if (on) palette.onSolid else palette.ink2)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text {
            attr {
                text("这一轮会停在这里，直到你在手机上或电脑上处理")
                fontSize(busChatPx(12f))
                lineHeight(busChatPx(12f) * 1.55f)
                color(palette.mut)
                marginTop(busChatPx(7f))
                lines(Int.MAX_VALUE)
            }
        }
        // 底部一行：左边「本会话内都允许」，右边拒绝 / 批准
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(busChatPx(11f))
            }
            View {
                attr {
                    flex(1f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("check.svg"))
                        size(busChatPx(13f), busChatPx(13f))
                        tintColor(palette.mut)
                    }
                }
                Text {
                    attr {
                        text("本会话内都允许")
                        fontSize(busChatPx(11.5f))
                        color(palette.mut)
                        marginLeft(busChatPx(5f))
                        lines(1)
                        textOverFlowTail()
                    }
                }
            }
            // 拒绝：白底描边幽灵钮；白色取自 palette.surface，深色下跟着卡片一起翻
            View {
                attr {
                    height(busChatPx(30f))
                    borderRadius(busChatPx(999f))
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                    backgroundColor(palette.surface)
                    border(Border(1f, BorderStyle.SOLID, palette.line))
                    paddingLeft(busChatPx(14f))
                    paddingRight(busChatPx(14f))
                }
                // 事件始终挂着，靠闭包里的 busy() 拦住重复提交
                event { click { if (!busy()) onDeny() } }
                Text {
                    attr {
                        text("拒绝")
                        fontSize(busChatPx(12.5f))
                        fontWeightSemiBold()
                        color(palette.ink2)
                    }
                }
            }
            // 批准：墨底白字实心钮
            View {
                attr {
                    height(busChatPx(30f))
                    borderRadius(busChatPx(999f))
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                    backgroundColor(palette.solid)
                    paddingLeft(busChatPx(14f))
                    paddingRight(busChatPx(14f))
                    marginLeft(busChatPx(8f))
                }
                event { click { if (!busy()) onApprove() } }
                Text {
                    attr {
                        text("批准")
                        fontSize(busChatPx(12.5f))
                        fontWeightSemiBold()
                        color(palette.onSolid)
                    }
                }
            }
        }
        vif({ error().isNotBlank() }) {
            Text {
                attr {
                    text(error())
                    fontSize(busChatPx(11f))
                    color(palette.bad)
                    marginTop(busChatPx(6f))
                    lines(Int.MAX_VALUE)
                }
            }
        }
    }
}

// ---------- 产物卡 ----------

/**
 * 产物卡：一行文件条，r12、1px 发丝线
 *
 * 中间两行是标题与「大小 · 产物」，右侧 chevron-right 只做可点暗示
 */
internal fun ViewContainer<*, *>.BusChatArtifactCard(
    palette: BusChatPalette,
    artifact: BusChatArtifact,
    onClick: () -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(palette.surface)
            border(Border(1f, BorderStyle.SOLID, palette.line))
            borderRadius(busChatPx(12f))
            paddingTop(busChatPx(8f))
            paddingBottom(busChatPx(8f))
            paddingLeft(busChatPx(10f))
            paddingRight(busChatPx(10f))
        }
        event { click { onClick() } }
        Image {
            attr {
                src(ImageUri.commonAssets("file-generic.svg"))
                size(busChatPx(18f), busChatPx(18f))
                tintColor(palette.deep)
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                marginLeft(busChatPx(9f))
            }
            Text {
                attr {
                    text(artifact.title.ifBlank { "产物" })
                    fontSize(BusChatTokens.toolTitle)
                    fontWeightSemiBold()
                    color(palette.ink)
                    lines(1)
                    textOverFlowTail()
                }
            }
            Text {
                attr {
                    text(busChatArtifactMeta(artifact.size))
                    fontSize(busChatPx(11f))
                    color(palette.mut)
                    lines(1)
                    textOverFlowTail()
                    marginTop(busChatPx(1f))
                }
            }
        }
        Image {
            attr {
                src(ImageUri.commonAssets("chevron-right.svg"))
                size(busChatPx(14f), busChatPx(14f))
                tintColor(palette.faint)
                marginLeft(busChatPx(8f))
            }
        }
    }
}

/** 审批卡左侧图标：命令、改动、权限、提问各用一枚线性图标 */
private fun busChatApprovalAsset(kind: String): String = when (kind) {
    "command" -> "tool-terminal.svg"
    "permission" -> "shield.svg"
    "input" -> "tool-ask.svg"
    "fileChange" -> "file-code.svg"
    else -> "file-generic.svg"
}

/** 产物副行：有体积时「102 KB · 产物」，没有体积只留「产物」 */
private fun busChatArtifactMeta(size: Long): String {
    val label = busChatFileSize(size)
    return if (label.isEmpty()) "产物" else "$label · 产物"
}

/** 字节 → 人类可读：B 取整，KB/MB 保留一位小数并去掉多余的 `.0` */
private fun busChatFileSize(size: Long): String {
    if (size <= 0L) return ""
    if (size < 1024L) return "$size B"
    val kb = size.toDouble() / 1024.0
    if (kb < 1024.0) return "${busChatSizeNumber(kb)} KB"
    return "${busChatSizeNumber(kb / 1024.0)} MB"
}

/** 一位小数，整数时不带小数点；common 里没有 String.format，只能自己拼 */
private fun busChatSizeNumber(value: Double): String {
    val scaled = (value * 10.0).toLong()
    return if (scaled % 10L == 0L) "${scaled / 10L}" else "${scaled / 10L}.${scaled % 10L}"
}
