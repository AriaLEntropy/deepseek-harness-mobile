package com.example.dsh.ui.home

import com.example.dsh.ui.message.DshLoadingSpinner
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 统一 Agent 审批中心的渲染层（视觉基线：first-phase-ui.md §16.9 + M2整合v86）
 *
 * 这一层只画东西、只回调，不持有任何状态：卡片列表、当前筛选段由调用方
 * 一次求值后传进来（结构变化要重建），而「已处理记录 / 勾选中的选项 / 正在
 * 发送的卡片 / 错误文案 / 加载中」一律传 `() -> T`，让 attr 闭包在响应式系统里
 * 重新求值——处理完一张卡时它只是就地变灰、标题追加状态字样，整列不重建、
 * 滚动位置不动
 *
 * 因此调用方（DshBusHomePage）必须把本函数包在 `vbind` 里，绑定值只要覆盖
 * 「卡片 key 序列 + 当前筛选段」即可；不要把 answered / selection 也放进绑定值，
 * 否则每点一次就整列重建
 *
 * 与 HTML 的两处已知差异（都受 Kuikly 能力限制，见交付说明）：
 * - `box-shadow` 两层（0 1px 2px + 0 14px 34px）只能取主层；
 * - 按钮 `:active` 的 scale(.97) + opacity .9 没有 CSS 伪类可挂，未做
 */
internal fun ViewContainer<*, *>.BusApprovalBoard(
    items: List<BusApprovalItem>,
    filter: String,
    answered: () -> BusApprovalAnswered,
    selection: () -> Map<String, List<String>>,
    busyKey: () -> String,
    error: () -> String,
    loading: () -> Boolean,
    emptyTitle: () -> String,
    emptySub: () -> String,
    topFadeVisible: () -> Boolean,
    palette: BusHomePalette,
    onFilter: (String) -> Unit,
    onToggleOption: (BusApprovalItem, BusApprovalQuestion, String) -> Unit,
    onAnswer: (BusApprovalItem, Boolean, Map<String, List<String>>) -> Unit,
    onOpenTask: (BusApprovalItem) -> Unit,
    onScrollY: (Float) -> Unit,
) {
    View {
        attr {
            flex(1f)
            flexDirectionColumn()
        }

        // 筛选行：全部 / 待审批 / 等待输入（gap 6、下间距 10，计数不含已处理）
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                flexWrap(FlexWrap.WRAP)
                paddingLeft(BusHomeTokens.spacePage)
                paddingRight(BusHomeTokens.spacePage)
                marginTop(BusHomeTokens.spaceListTop)
                marginBottom(FILTER_ROW_BOTTOM)
            }
            BUS_APPROVAL_FILTER_IDS.forEach { tabId ->
                BusApprovalFilterChip(
                    tabId = tabId,
                    items = items,
                    filter = filter,
                    answered = answered,
                    palette = palette,
                    onClick = { onFilter(tabId) },
                )
            }
        }

        // 发送失败提示：快照本身不带错误通道，这里只回显页面记下的最后一次失败
        vif({ error().isNotBlank() }) {
            View {
                attr {
                    marginLeft(BusHomeTokens.spacePage)
                    marginRight(BusHomeTokens.spacePage)
                    marginBottom(FILTER_ROW_BOTTOM)
                    borderRadius(BusHomeTokens.radiusButton)
                    backgroundColor(palette.errorFill)
                    paddingLeft(ERROR_ROW_SIDE)
                    paddingRight(ERROR_ROW_SIDE)
                    paddingTop(ERROR_ROW_VERTICAL)
                    paddingBottom(ERROR_ROW_VERTICAL)
                }
                Text {
                    attr {
                        text(error())
                        fontSize(BusHomeTokens.fontSizeMeta)
                        lineHeight(busHomePx(16f))
                        color(palette.error)
                    }
                }
            }
        }

        // 卡片区 + 顶部 56px 渐隐：渐隐层是 List 的兄弟节点，自己不带事件，
        // 只在列表滚出顶部后由调用方把 topFadeVisible 置真（对齐 #task-top-fade）
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
            }
            val rows = ObservableList<BusApprovalRow>().apply {
                addAll(busApprovalRows(items, filter))
            }
            // 没有任何待处理项时，缺省页直接铺满列表区并垂直居中，对齐「暂无任务」
            vif({ !loading() && busApprovalVisibleItems(items, filter).isEmpty() }) {
                BusApprovalEmptyState(palette, emptyTitle, emptySub, fill = true)
            }
            vif({ loading() || busApprovalVisibleItems(items, filter).isNotEmpty() }) {
            List {
                attr {
                    flex(1f)
                    showScrollerIndicator(false)
                }
                event {
                    scroll { params -> onScrollY(params.offsetY) }
                    // 橡皮筋回弹时，最后一条 scroll 可能还停在 offset>1 上；落定后再判一次，
                    // 免得列表已经贴回顶部，56 的渐隐条却一直挂在上面压住第一张卡
                    scrollEnd { params -> onScrollY(params.offsetY) }
                }
                vforLazy({ rows }, maxLoadItem = MAX_RENDERED_CARDS) { row, _, _ ->
                    // 循环指令的闭包必须且只能生成一个根节点，所以空态行与卡片
                    // 共用这层壳；底部 128 的胶囊/FAB 预留也挂在壳里
                    View {
                        attr { flexDirectionColumn() }
                        when (row) {
                            is BusApprovalRow.Card -> BusApprovalCard(
                                item = row.item,
                                answered = answered,
                                selection = selection,
                                busyKey = busyKey,
                                palette = palette,
                                onToggleOption = onToggleOption,
                                onAnswer = onAnswer,
                                onOpenTask = onOpenTask,
                            )

                            is BusApprovalRow.Empty -> {
                                vif({ loading() }) {
                                    BusApprovalLoadingRow(palette)
                                }
                                vif({ !loading() && !busApprovalHasOpenItems(items, filter, answered()) }) {
                                    BusApprovalEmptyState(palette, emptyTitle, emptySub)
                                }
                                View { attr { height(BOTTOM_INSET) } }
                            }
                        }
                    }
                }
            }
            }
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f)
                    height(BusHomeTokens.topFadeHeight)
                    // 设计稿 `#task-top-fade` 带 pointer-events: none；漏掉这行会让渐隐层
                    // 吃掉列表顶部 56 的触摸，第一张卡的上半截点不动
                    touchEnable(false)
                    backgroundLinearGradient(
                        Direction.TO_BOTTOM,
                        ColorStop(palette.topFadeStart, 0f),
                        ColorStop(palette.topFadeMiddle, BusHomeTokens.topFadeStopMiddle),
                        ColorStop(palette.topFadeBody, BusHomeTokens.topFadeStopBody),
                        ColorStop(palette.topFadeClear, 1f),
                    )
                    // 设计稿是 `transition: opacity .16s ease`；直接改 opacity 会「啪」地刷出一道白条
                    opacity(if (topFadeVisible()) 1f else 0f)
                    animation(Animation.easeInOut(0.16f), topFadeVisible())
                }
            }
        }
    }
}

/** 三个筛选段的固定顺序（id 见 [BUS_APPROVAL_FILTER_ALL] 等）。 */
private val BUS_APPROVAL_FILTER_IDS = listOf(
    BUS_APPROVAL_FILTER_ALL,
    BUS_APPROVAL_FILTER_DECISION,
    BUS_APPROVAL_FILTER_REPLY,
)

/** 审批卡容器：v86 补丁的 surface + 高光描边 + radius 18 + 单层主投影。 */
private fun ViewContainer<*, *>.BusApprovalCard(
    item: BusApprovalItem,
    answered: () -> BusApprovalAnswered,
    selection: () -> Map<String, List<String>>,
    busyKey: () -> String,
    palette: BusHomePalette,
    onToggleOption: (BusApprovalItem, BusApprovalQuestion, String) -> Unit,
    onAnswer: (BusApprovalItem, Boolean, Map<String, List<String>>) -> Unit,
    onOpenTask: (BusApprovalItem) -> Unit,
) {
    val quickSend = busApprovalQuickSend(item)
    val commandText = busApprovalCommandText(item)
    val proseLines = busApprovalProseLines(item)
    View {
        attr {
            flexDirectionColumn()
            borderRadius(BusHomeTokens.radiusCard)
            backgroundColor(palette.surface)
            border(Border(1f, BorderStyle.SOLID, BusHomeTokens.surfaceBorder))
            boxShadow(BoxShadow(0f, CARD_SHADOW_Y, CARD_SHADOW_BLUR, CARD_SHADOW_COLOR))
            marginLeft(BusHomeTokens.spacePage)
            marginRight(BusHomeTokens.spacePage)
            paddingLeft(CARD_SIDE)
            paddingRight(CARD_SIDE)
            paddingTop(CARD_TOP)
            paddingBottom(CARD_BOTTOM)
            marginBottom(CARD_GAP)
            // 已处理：留在原位、压暗，不再响应点击（快照还会把它投回来）
            opacity(if (answered()[item.key] != null) ANSWERED_OPACITY else 1f)
        }
        event {
            click {
                if (answered()[item.key] == null && busyKey() != item.key) onOpenTask(item)
            }
        }

        // 标题 + 时间
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text(busApprovalTitle(item, answered()))
                    fontSize(BusHomeTokens.fontSizeTitle)
                    fontWeightBold()
                    color(palette.textPrimary)
                    flex(1f)
                    lines(1)
                    textOverFlowTail()
                }
            }
            Text {
                attr {
                    text(item.time)
                    fontSize(BusHomeTokens.fontSizeCaption)
                    color(BusHomeTokens.textTertiary)
                    marginLeft(HEADER_TIME_GAP)
                }
            }
        }

        // 项目标签 + 「Agent · 请求说明」
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(META_ROW_TOP)
            }
            if (item.project.isNotBlank()) {
                View {
                    attr {
                        borderRadius(BusHomeTokens.radiusChip)
                        backgroundColor(BusHomeTokens.tagFill)
                        paddingLeft(TAG_SIDE)
                        paddingRight(TAG_SIDE)
                        paddingTop(TAG_VERTICAL)
                        paddingBottom(TAG_VERTICAL)
                    }
                    Text {
                        attr {
                            text(item.project)
                            fontSize(BusHomeTokens.fontSizeCaption)
                            fontWeightBold()
                            color(palette.textPrimary)
                            lines(1)
                            textOverFlowTail()
                        }
                    }
                }
            }
            Text {
                attr {
                    text("${item.agentName} · ${busApprovalKindLabel(item.kind)}")
                    fontSize(BusHomeTokens.fontSizeCaption)
                    color(BusHomeTokens.textTertiary)
                    marginLeft(META_TEXT_GAP)
                    lines(1)
                    textOverFlowTail()
                }
            }
        }

        // 命令 / 改动块（等宽、#F5F5F7 底）
        if (commandText.isNotBlank()) {
            View {
                attr {
                    marginTop(COMMAND_BLOCK_TOP)
                    borderRadius(COMMAND_BLOCK_RADIUS)
                    backgroundColor(BusHomeTokens.field)
                    paddingLeft(COMMAND_BLOCK_SIDE)
                    paddingRight(COMMAND_BLOCK_SIDE)
                    paddingTop(COMMAND_BLOCK_VERTICAL)
                    paddingBottom(COMMAND_BLOCK_VERTICAL)
                }
                Text {
                    attr {
                        text(commandText)
                        fontSize(BusHomeTokens.fontSizeMeta)
                        lineHeight(COMMAND_LINE_HEIGHT)
                        fontFamily("monospace")
                        color(palette.textPrimary)
                    }
                }
            }
        }

        // 问题正文（14/20）
        proseLines.forEachIndexed { lineIndex, line ->
            val firstBlock = commandText.isBlank() && lineIndex == 0
            Text {
                attr {
                    text(line)
                    fontSize(QUESTION_FONT)
                    lineHeight(QUESTION_LINE_HEIGHT)
                    color(palette.textPrimary)
                    marginTop(if (firstBlock) META_ROW_TOP else PROSE_TOP)
                }
            }
        }

        // 选项：非「点一下就发送」的卡片在这里勾选
        if (!quickSend) {
            item.questions.forEach { question ->
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        flexWrap(FlexWrap.WRAP)
                        marginTop(OPTION_ROW_TOP)
                    }
                    question.options.forEach { option ->
                        BusApprovalOptionChip(
                            label = option.first,
                            description = option.second,
                            multiSelect = question.multiSelect,
                            selected = {
                                option.first in busApprovalNormalizeLabels(
                                    question,
                                    selection()[busApprovalSelectionKey(item.key, question.id)].orEmpty(),
                                )
                            },
                            palette = palette,
                            onClick = { onToggleOption(item, question, option.first) },
                        )
                    }
                }
            }
        }

        // 操作行
        when {
            !item.replyOnly -> View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(ACTION_ROW_TOP)
                }
                View { attr { flex(1f) } }
                BusApprovalButton(
                    label = "拒绝",
                    variant = BusApprovalButtonVariant.SECONDARY,
                    palette = palette,
                    enabled = { answered()[item.key] == null },
                    busy = { busyKey() == item.key },
                ) {
                    // 协议规定 answers 只和 allow 一起出现，拒绝时不带任何勾选
                    onAnswer(item, false, emptyMap())
                }
                BusApprovalButton(
                    label = busApprovalPrimaryLabel(item),
                    variant = BusApprovalButtonVariant.PRIMARY,
                    palette = palette,
                    enabled = { answered()[item.key] == null },
                    busy = { busyKey() == item.key },
                ) {
                    onAnswer(item, true, busApprovalAnswers(item, selection()))
                }
            }

            quickSend -> View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    flexWrap(FlexWrap.WRAP)
                    marginTop(ACTION_ROW_TOP)
                }
                item.questions.singleOrNull()?.options?.forEach { option ->
                    BusApprovalButton(
                        label = option.first,
                        variant = BusApprovalButtonVariant.SECONDARY,
                        horizontalPadding = OPTION_BUTTON_SIDE,
                        palette = palette,
                        enabled = { answered()[item.key] == null },
                        busy = { busyKey() == item.key },
                    ) {
                        // 单选问题的一个选项就是一条完整回答，点一下即发送（HTML 的 relay.log）
                        onAnswer(item, true, mapOf(item.questions.single().id to listOf(option.first)))
                    }
                }
                BusApprovalButton(
                    label = "自定义回复…",
                    variant = BusApprovalButtonVariant.DASHED,
                    horizontalPadding = OPTION_BUTTON_SIDE,
                    palette = palette,
                    enabled = { answered()[item.key] == null },
                    busy = { busyKey() == item.key },
                ) {
                    onOpenTask(item)
                }
            }

            item.questions.isNotEmpty() -> View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(ACTION_ROW_TOP)
                }
                View { attr { flex(1f) } }
                BusApprovalButton(
                    label = busApprovalPrimaryLabel(item),
                    variant = BusApprovalButtonVariant.PRIMARY,
                    palette = palette,
                    enabled = {
                        answered()[item.key] == null &&
                            busApprovalHasSelection(item, selection())
                    },
                    busy = { busyKey() == item.key },
                ) {
                    onAnswer(item, true, busApprovalAnswers(item, selection()))
                }
                BusApprovalButton(
                    label = "自定义回复…",
                    variant = BusApprovalButtonVariant.DASHED,
                    horizontalPadding = OPTION_BUTTON_SIDE,
                    palette = palette,
                    enabled = { answered()[item.key] == null },
                    busy = { busyKey() == item.key },
                ) {
                    onOpenTask(item)
                }
            }

            else -> View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentFlexEnd()
                    marginTop(ACTION_ROW_TOP)
                }
                BusApprovalButton(
                    label = "回复…",
                    variant = BusApprovalButtonVariant.SECONDARY,
                    palette = palette,
                    enabled = { answered()[item.key] == null },
                    busy = { busyKey() == item.key },
                ) {
                    // 没问题可点 = 要自己组织语言，交给会话页输入框
                    onOpenTask(item)
                }
            }
        }
    }
}

/** 筛选 chip：11 号、radius 10、padding 4/10；选中态黑底白字。 */
private fun ViewContainer<*, *>.BusApprovalFilterChip(
    tabId: String,
    items: List<BusApprovalItem>,
    filter: String,
    answered: () -> BusApprovalAnswered,
    palette: BusHomePalette,
    onClick: () -> Unit,
) {
    View {
        attr {
            borderRadius(BusHomeTokens.radiusButton)
            backgroundColor(
                if (filter == tabId) palette.selectedFill else palette.chipFill,
            )
            paddingLeft(CHIP_SIDE)
            paddingRight(CHIP_SIDE)
            paddingTop(CHIP_VERTICAL)
            paddingBottom(CHIP_VERTICAL)
            marginRight(CHIP_GAP)
        }
        event { click { onClick() } }
        Text {
            attr {
                text(
                    busApprovalFilterTabs(items, answered())
                        .firstOrNull { it.id == tabId }
                        .let { "${it?.label.orEmpty()} ${it?.count ?: 0}" }
                        .trim(),
                )
                fontSize(BusHomeTokens.fontSizeCaption)
                if (filter == tabId) {
                    fontWeightBold()
                    color(palette.onSelected)
                } else {
                    fontWeightMedium()
                    color(palette.textSecondary)
                }
            }
        }
    }
}

/** 卡片上的可勾选选项（单选互斥、多选可叠加），沿用标签观感。 */
private fun ViewContainer<*, *>.BusApprovalOptionChip(
    label: String,
    description: String,
    multiSelect: Boolean,
    selected: () -> Boolean,
    palette: BusHomePalette,
    onClick: () -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            borderRadius(OPTION_RADIUS)
            backgroundColor(if (selected()) palette.selectedFill else Color(0xFFFFFFFF))
            border(
                Border(
                    1f,
                    BorderStyle.SOLID,
                    if (selected()) palette.selectedFill else OPTION_BORDER,
                ),
            )
            paddingLeft(OPTION_SIDE)
            paddingRight(OPTION_SIDE)
            paddingTop(OPTION_VERTICAL)
            paddingBottom(OPTION_VERTICAL)
            marginRight(OPTION_GAP)
            marginBottom(OPTION_ROW_GAP)
        }
        event { click { onClick() } }
        if (multiSelect) {
            View {
                attr {
                    size(OPTION_MARK_SIZE, OPTION_MARK_SIZE)
                    borderRadius(OPTION_MARK_SIZE / 2f)
                    border(
                        Border(
                            1.5f,
                            BorderStyle.SOLID,
                            if (selected()) palette.onSelected else BusHomeTokens.lineDashed,
                        ),
                    )
                    marginRight(OPTION_MARK_GAP)
                }
            }
        }
        Text {
            attr {
                text(label)
                fontSize(BusHomeTokens.fontSizeMeta)
                fontWeightBold()
                color(if (selected()) palette.onSelected else palette.textSecondary)
                lines(1)
                textOverFlowTail()
            }
        }
        if (description.isNotBlank()) {
            Text {
                attr {
                    text(description)
                    fontSize(BusHomeTokens.fontSizeCaption)
                    color(if (selected()) palette.onSelected else BusHomeTokens.textTertiary)
                    marginLeft(OPTION_MARK_GAP)
                    lines(1)
                    textOverFlowTail()
                }
            }
        }
    }
}

/** 审批卡按钮的三种观感（§16.9：主操作黑底白字、次操作实描边、自定义回复虚线）。 */
private enum class BusApprovalButtonVariant {
    PRIMARY,
    SECONDARY,
    DASHED,
}

private fun ViewContainer<*, *>.BusApprovalButton(
    label: String,
    variant: BusApprovalButtonVariant,
    palette: BusHomePalette,
    enabled: () -> Boolean = { true },
    busy: () -> Boolean = { false },
    horizontalPadding: Float = BUTTON_SIDE,
    onClick: () -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            justifyContentCenter()
            minHeight(BUTTON_HEIGHT)
            borderRadius(BusHomeTokens.radiusButton)
            when (variant) {
                BusApprovalButtonVariant.PRIMARY -> {
                    backgroundColor(palette.selectedFill)
                    border(Border(1f, BorderStyle.SOLID, palette.selectedFill))
                }

                BusApprovalButtonVariant.SECONDARY -> {
                    backgroundColor(BusHomeTokens.cardFill)
                    border(Border(1f, BorderStyle.SOLID, BusHomeTokens.lineCard))
                }

                BusApprovalButtonVariant.DASHED -> {
                    backgroundColor(BusHomeTokens.cardFill)
                    border(Border(1f, BorderStyle.DASHED, BusHomeTokens.lineDashed))
                }
            }
            paddingLeft(horizontalPadding)
            paddingRight(horizontalPadding)
            paddingTop(BUTTON_VERTICAL)
            paddingBottom(BUTTON_VERTICAL)
            marginLeft(BUTTON_GAP)
            opacity(
                when {
                    !enabled() -> BUTTON_DISABLED_OPACITY
                    else -> 1f
                },
            )
        }
        event {
            click {
                if (enabled() && !busy()) onClick()
            }
        }
        vif({ busy() }) {
            DshLoadingSpinner(
                diameter = SPINNER_SIZE,
                tint = if (variant == BusApprovalButtonVariant.PRIMARY) {
                    palette.onSelected
                } else {
                    palette.textSecondary
                },
            )
        }
        vif({ !busy() }) {
            Text {
                attr {
                    text(label)
                    fontSize(BusHomeTokens.fontSizeMeta)
                    fontWeightBold()
                    color(
                        when (variant) {
                            BusApprovalButtonVariant.PRIMARY -> palette.onSelected
                            else -> palette.textSecondary
                        },
                    )
                }
            }
        }
    }
}

/**
 * 空态：缺省页家族的「一叠还不存在的批准卡片」插画 + 两行文案
 *
 * [fill] = true 时铺满列表区并垂直居中，和「暂无任务」的缺省页同一套排布
 * 文案由页面给：电脑在的时候才是「暂无待处理」，电脑没连上/没响应时说「暂无」是假话
 */
private fun ViewContainer<*, *>.BusApprovalEmptyState(
    palette: BusHomePalette,
    title: () -> String,
    sub: () -> String,
    fill: Boolean = false,
) {
    View {
        attr {
            flexDirectionColumn()
            alignItemsCenter()
            if (fill) {
                flex(1f)
                justifyContentCenter()
                // 居中容器里多加 2 倍底部留白，块就整体上移 1 倍；这里要上移的就是 approvalEmptyLift，
                // 让审批的缺省页和列表那几个缺省页的插画落在同一条线上
                paddingBottom(BusHomeSectionTokens.emptyBottomInset + BusHomeSectionTokens.approvalEmptyLift * 2f)
            } else {
                paddingTop(EMPTY_VERTICAL)
                paddingBottom(EMPTY_VERTICAL)
            }
            paddingLeft(BusHomeTokens.spacePage)
            paddingRight(BusHomeTokens.spacePage)
        }
        Image {
            attr {
                src(
                    ImageUri.commonAssets(
                        if (palette.isDark) "empty-no-approval-dark.svg" else "empty-no-approval.svg",
                    ),
                )
                size(BusHomeSectionTokens.emptyIconWidth, BusHomeSectionTokens.emptyIconHeight)
            }
        }
        Text {
            attr {
                text(title())
                fontSize(BusHomeSectionTokens.emptyTitleFont)
                fontWeightBold()
                color(palette.textSecondary)
                marginTop(BusHomeSectionTokens.emptyTitleTop)
                textAlignCenter()
            }
        }
        Text {
            attr {
                text(sub())
                fontSize(BusHomeTokens.fontSizeBody)
                lineHeight(BusHomeSectionTokens.emptySubLineHeight)
                color(palette.textTertiary)
                marginTop(BusHomeTokens.gapControl)
                textAlignCenter()
            }
        }
    }
}

/** 首次快照还没到：只给一个转圈 + 一行说明，不铺骨架屏。 */
private fun ViewContainer<*, *>.BusApprovalLoadingRow(palette: BusHomePalette) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            justifyContentCenter()
            paddingTop(EMPTY_VERTICAL)
            paddingBottom(EMPTY_VERTICAL)
        }
        DshLoadingSpinner(diameter = SPINNER_SIZE, tint = palette.textTertiary)
        Text {
            attr {
                text("正在同步待处理请求")
                fontSize(BusHomeTokens.fontSizeMeta)
                color(palette.textTertiary)
                marginLeft(BUTTON_GAP)
            }
        }
    }
}

// ---------- 审批视图专用尺寸（§16.2 / §16.9 里只出现在这块的数值） ----------

/** 审批卡内边距 14 / 14 / 12，卡间距 10 */
private const val CARD_TOP = 14f
private const val CARD_BOTTOM = 12f
private const val CARD_SIDE = 14f
private const val CARD_GAP = 10f

/** 阴影定稿值 `0 14px 34px rgba(40,40,55,.075)`（Kuikly 只能挂一层） */
private const val CARD_SHADOW_Y = 14f
private const val CARD_SHADOW_BLUR = 34f
private val CARD_SHADOW_COLOR = Color(0x13282837)

/** 已处理卡片压暗到 .45 */
private const val ANSWERED_OPACITY = 0.45f

/** 筛选行：chip 间距 6、行下间距 10 */
private const val CHIP_GAP = 6f
private const val FILTER_ROW_BOTTOM = 10f
private const val CHIP_SIDE = 10f
private const val CHIP_VERTICAL = 4f

/** 项目标签 / 选项标签的 padding 2 x 8 */
private const val TAG_SIDE = 8f
private const val TAG_VERTICAL = 2f
private const val META_ROW_TOP = 6f
private const val META_TEXT_GAP = 6f
private const val HEADER_TIME_GAP = 10f

/** 命令块：radius 8、padding 9 x 12、等宽 12/18 */
private const val COMMAND_BLOCK_TOP = 8f
private const val COMMAND_BLOCK_RADIUS = 8f
private const val COMMAND_BLOCK_SIDE = 12f
private const val COMMAND_BLOCK_VERTICAL = 9f
private const val COMMAND_LINE_HEIGHT = 18f

/** 问题正文 14/20 */
private const val QUESTION_FONT = 14f
private const val QUESTION_LINE_HEIGHT = 20f
private const val PROSE_TOP = 8f

/** 选项行与操作行 */
private const val OPTION_ROW_TOP = 10f
private const val OPTION_ROW_GAP = 6f
private const val OPTION_GAP = 8f
private const val OPTION_MARK_SIZE = 12f
private const val OPTION_MARK_GAP = 6f

/** 选项胶囊：白底实描边 radius 10，padding 6 x 14（HTML approve-btn） */
private const val OPTION_SIDE = 14f
private const val OPTION_VERTICAL = 6f
private const val OPTION_RADIUS = 10f
private val OPTION_BORDER = Color(0xFFE4E4E7)
private const val ACTION_ROW_TOP = 8f

/** 按钮：12/600、radius 10、padding 6 x 16（快捷选项 6 x 14） */
private const val BUTTON_SIDE = 16f
private const val OPTION_BUTTON_SIDE = 14f
private const val BUTTON_VERTICAL = 6f
private const val BUTTON_GAP = 8f
private const val BUTTON_HEIGHT = 29f
private const val BUTTON_DISABLED_OPACITY = 0.55f
private const val SPINNER_SIZE = 12f

/** 错误提示行 padding 8 x 12 */
private const val ERROR_ROW_SIDE = 12f
private const val ERROR_ROW_VERTICAL = 8f

/** 空态：上下 56、左右 20；短横 8 x 1.5 居中在 16 x 16 里 */
private const val EMPTY_VERTICAL = 56f
private const val EMPTY_SUB_TOP = 6f

/** 列表底部给胶囊 + FAB 预留 128 */
private const val BOTTOM_INSET = 128f

/** 一屏最多两张半审批卡，懒加载窗口给到 4 屏。 */
private const val MAX_RENDERED_CARDS = 10
