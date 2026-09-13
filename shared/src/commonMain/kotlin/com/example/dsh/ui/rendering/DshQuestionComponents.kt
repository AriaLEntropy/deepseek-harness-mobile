package com.example.dsh.ui.rendering

import com.example.dsh.tool.AnsweredItem
import com.example.dsh.tool.DshAskQuestionCard
import com.example.dsh.interaction.DshPendingQuestion
import com.example.dsh.interaction.DshPendingQuestionItem
import com.example.dsh.interaction.DshPendingQuestionOption
import com.example.dsh.tool.UnansweredItem
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
import com.tencent.kuikly.core.base.BoxShadow
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
import com.tencent.kuikly.core.views.TextArea

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

internal fun ViewContainer<*, *>.DshTapTarget(onClick: () -> Unit) {
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
