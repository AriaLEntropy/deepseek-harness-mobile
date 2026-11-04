package com.example.dsh.ui.home

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.BorderRectRadius
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 首页各分区的构建函数（视觉基线：first-phase-ui.md §16 + M2整合v86 对照稿）
 *
 * 页面（DshBusHomePage）只管状态、轮询与命令，这里只管画和回调：
 * 结构会变的入参直接传值，由页面 vbind 决定何时重建；
 * 计数、文案、滚动这类高频状态一律走 lambda，让 attr 闭包就地更新，
 * 免得 2 秒一次的快照把整页重建、把滚动位置与渐隐状态弄丢
 *
 * v86 的任务区是「外层容器卡 + 卡内若干行」，而 List 只能对直接子节点做懒加载，
 * 所以卡片不是真的父容器：每一行按它所属卡的位置自己长出圆角、卡内边距与投影，
 * 相邻片段底色一致、没有间距，拼起来就是一张完整的卡
 * 单层投影、没有 :active、噪点缺失等复刻限制逐条写在对应构建函数上
 */

// ---------- 视图模式 ----------

/** 任务列表按时间平铺 */
internal const val BUS_VIEW_RECENT = "recent"

/** 任务列表按项目分组 */
internal const val BUS_VIEW_PROJECTS = "projects"

/** 审批视图：底部胶囊的终端分段进入，共用顶部导航与底部栏 */
internal const val BUS_VIEW_APPROVAL = "approval"

/** 「最近 / 按项目」属于列表视图；审批视图下分段控件换成标题 */
internal fun busIsListViewMode(mode: String): Boolean = mode == BUS_VIEW_RECENT || mode == BUS_VIEW_PROJECTS

// ---------- 首页专用数值与颜色 ----------

/**
 * 只出现在首页、[BusHomeTokens] 没有收录的数值
 *
 * 面板那套（行卡、拖拽条、浮层）留在 BusSheetTokens 里，这里不重复定义；
 * [BusHomeTokens] 已有的语义值一律直接用 token，不在这儿再起一个名字
 */
internal object BusHomeSectionTokens {
    /**
     * 头部淡紫层高度
     *
     * 设计稿是 420px 纵向半径的径向渐变，但圆心在 -120px，
     * 实际落到画布上的可见范围只有约 200px；线性近似取 220，避免紫色铺得太深
     */
    val headerGlowHeight: Float get() = busHomePx(220f)

    /** 头部淡紫渐变 42% 处收到 soft */
    const val headerGlowMiddle: Float = 0.42f

    /** 头部淡紫在 85% 处完全消失，留一段纯透明余量 */
    const val headerGlowEnd: Float = 0.85f

    /** 顶部区（`top-panel`）内边距 6 20 12 */
    val topPanelPaddingTop: Float get() = busHomePx(6f)
    val topPanelPaddingBottom: Float get() = busHomePx(12f)

    /** 设备入口 / 设置入口的 padding：纵向 7、横向 13 */
    val entryPaddingVertical: Float get() = busHomePx(7f)
    val entryPaddingHorizontal: Float get() = busHomePx(13f)

    /** 设备入口里的平台图标 23 */
    val entryIconSize: Float get() = busHomePx(23f)

    /** 状态点与在线点 6 */
    val onlineDot: Float get() = busHomePx(6f)

    /** 点与文字、图标与文字之间的小间距 5 */
    val dotTextGap: Float get() = busHomePx(5f)

    /** 设备入口副行上间距 2 */
    val entrySubtitleTop: Float get() = busHomePx(2f)

    /** 入口禁用态透明度 .85（沿用旧首页，不给可点错觉） */
    const val entryDisabledOpacity: Float = 0.85f

    /** 入口/设置按钮阴影：单层主投影 0 10 24 */
    val entryShadowY: Float get() = busHomePx(10f)
    val entryShadowBlur: Float get() = busHomePx(24f)

    /** 设置入口 40×40、radius 12、图标 17 */
    val settingsSize: Float get() = busHomePx(40f)
    val settingsRadius: Float get() = busHomePx(12f)
    val settingsIconSize: Float get() = busHomePx(17f)

    /** 头像栈左右留白 8（叠放的首格靠它顶出来） */
    val avatarStackPaddingLeft: Float get() = busHomePx(8f)

    /** 头像投影 0 1 3 */
    val avatarShadowY: Float get() = busHomePx(1f)
    val avatarShadowBlur: Float get() = busHomePx(3f)

    /** 省略号位 14/600 */
    val avatarEllipsisFont: Float get() = busHomePx(14f)

    /** 线性类官方图标在 26 圆里的可见尺寸 */
    val avatarLineIcon: Float get() = busHomePx(20f)

    /** 容器卡内边距：上 5、左右 16、时间线卡下 3 */
    val cardPaddingTop: Float get() = busHomePx(5f)
    val cardPaddingHorizontal: Float get() = busHomePx(16f)
    val cardPaddingBottom: Float get() = busHomePx(3f)

    /** 分组卡内边距下侧 10（设计稿 `5px 16px 10px`） */
    val groupCardPaddingBottom: Float get() = busHomePx(10f)

    /** 卡片主投影 0 14 34 */
    val cardShadowY: Float get() = busHomePx(14f)
    val cardShadowBlur: Float get() = busHomePx(34f)

    /** 列表底部给胶囊 + FAB 预留 128 */
    val listBottomInset: Float get() = busHomePx(128f)

    /** 任务行上下 padding：待处理 9、其余 8 */
    val rowPaddingVertical: Float get() = busHomePx(9f)
    val rowPaddingVerticalDense: Float get() = busHomePx(8f)

    /** 行内第二行（状态 + 摘要）的上间距：最近 4、按项目 2 */
    val metaRowTop: Float get() = busHomePx(4f)
    val metaRowTopDense: Float get() = busHomePx(2f)

    /** 同行元素间距 6（对应 CSS gap:6） */
    val metaGap: Float get() = busHomePx(6f)

    /** 标题行高 21、状态与摘要行高 17 */
    val titleLineHeight: Float get() = busHomePx(21f)
    val captionLineHeight: Float get() = busHomePx(17f)

    /** 行内线性类图标的可见尺寸（36 底盘内约 6 内缩） */
    val rowIconLineSize: Float get() = busHomePx(24f)

    /** 分组头：高 28、标题 14/600、箭头 16 */
    val groupHeadHeight: Float get() = busHomePx(28f)
    val groupHeadFont: Float get() = busHomePx(14f)
    val groupHeadIcon: Float get() = busHomePx(16f)

    /** 组间发丝线 1 */
    const val hairlineHeight: Float = 1f

    /** 底部渐隐 45% 处 */
    const val bottomFadeMiddle: Float = 0.45f

    /** 胶囊阴影 0 10 28 */
    val capsuleShadowY: Float get() = busHomePx(10f)
    val capsuleShadowBlur: Float get() = busHomePx(28f)

    /** 未选中分段的透明度 .72 */
    const val capsuleIdleOpacity: Float = 0.72f

    /** FAB 阴影 0 6 18、图标 26（HTML: 0 6px 18px rgba(17,17,26,.14)） */
    val fabShadowY: Float get() = busHomePx(6f)
    val fabShadowBlur: Float get() = busHomePx(18f)
    val fabIconSize: Float get() = busHomePx(26f)

    /** 空态：插画 280×175、标题 16、上下留白 */
    val emptyIconWidth: Float get() = busHomePx(280f)
    val emptyIconHeight: Float get() = busHomePx(175f)
    val emptyTitleFont: Float get() = busHomePx(16f)
    val emptyTitleTop: Float get() = busHomePx(26f)
    val emptySubLineHeight: Float get() = busHomePx(21f)
    val emptyBottomInset: Float get() = busHomePx(70f)

    /**
     * 审批缺省页相对列表缺省页要上移的量（用 paddingBottom 的 2 倍来补偿居中）
     *
     * 两个缺省页都是「在自己的区域里垂直居中」，但审批那块：
     * - 区域顶比列表低一个分段筛选行（16 上间距 + 20 行高 + 10 下间距 = 46），居中后整体低 46/2 = 23；
     * - 自己的内容比「还没有连接电脑」矮一截（少了查看引导 + 两条链接，约 94），矮的块居中后起点更低 94/2 = 47；
     * 合计 70，实测两个插画上沿差 198px = 69 设计 px，正好是这两项。
     * 居中时把底部留白加大 2×70，块就整体上移 70，两个缺省页的插画上沿对齐
     */
    val approvalEmptyLift: Float get() = busHomePx(70f)

    /** 无设备空态：虚线引导胶囊与两条连接入口 */
    val noDeviceGuideTop: Float get() = busHomePx(14f)
    val noDeviceGuideHorizontal: Float get() = busHomePx(18f)
    val noDeviceGuideVertical: Float get() = busHomePx(7f)
    val noDeviceLinksTop: Float get() = busHomePx(12f)
    val noDeviceLinkGap: Float get() = busHomePx(22f)

    /** 无设备时空头像位换成菜单图标，12 */
    val agentEmptyIcon: Float get() = busHomePx(12f)
}

/** 首页专用色（§16.1 里 [BusHomeTokens] 没收录的那几个值） */
internal object BusHomeSectionColors {
    /** 设备/设置入口底 rgba(255,255,255,.86) */
    val entryFill = Color(0xDBFFFFFF)

    /** 入口主投影 rgba(40,40,55,.08) */
    val entryShadow = Color(0x14282837)

    /** 胶囊底 rgba(255,255,255,.72) */
    val capsuleFill = Color(0xB8FFFFFF)

    /** 胶囊描边 rgba(255,255,255,.75) */
    val capsuleBorder = Color(0xBFFFFFFF)

    /** 胶囊主投影 rgba(40,40,55,.08) */
    val capsuleShadow = Color(0x14282837)

    /** 容器卡主投影 rgba(40,40,55,.075) */
    val cardShadow = Color(0x13282837)

    /** 等待输入的状态点 #B89A52 */
    val waitingDot = Color(0xFFB89A52)

    /** 等待输入的状态文字 #B54708 */
    val waitingText = Color(0xFFB54708)

    /** 已完成 / 其余状态的状态点 #C9C4BC */
    val doneDot = Color(0xFFC9C4BC)

    /** 空头像位底 #F0F0F2（§16.1 line/border） */
    val avatarEmpty = Color(0xFFF0F0F2)

    /** 组间发丝线 #F0F0F3（§16.1 line/hairline） */
    val hairline = Color(0xFFF0F0F3)

    /** 无设备空态虚线描边 #C4C0D8（empty-no-device.svg 外框同色） */
    val noDeviceDashed = Color(0xFFC4C0D8)
}

// ---------- 任务区行模型与纯函数 ----------

/**
 * 任务区的一行
 *
 * 外层容器卡由这些片段拼出来，所以每行要带上自己在这张卡里的位置；
 * 列表首尾也一样要烘进行数据里 —— `vforLazy` 复用的是已建好的节点，
 * 只在渲染闭包里读 index/count 的话，首行上方 16、末行下方 128 会留在旧节点上
 */
internal sealed class BusTaskAreaRow {
    abstract val key: String
    abstract val cardFirst: Boolean
    abstract val cardLast: Boolean
    abstract val listFirst: Boolean
    abstract val listLast: Boolean

    /** 一条任务；[dense] = 按项目视图的紧凑排布（行内边距收为 8、不显示 Agent 名） */
    data class Task(
        val task: BusHomeTask,
        override val cardFirst: Boolean = false,
        override val cardLast: Boolean = false,
        override val listFirst: Boolean = false,
        override val listLast: Boolean = false,
        val dense: Boolean = false,
    ) : BusTaskAreaRow() {
        override val key: String = "task-${task.id}"
    }

    /** 按项目视图的分组头，组间发丝线长在它自己上边 */
    data class GroupHeader(
        val group: BusHomeProjectGroup,
        val expanded: Boolean,
        val showMenu: Boolean,
        val divider: Boolean,
        override val cardFirst: Boolean = false,
        override val cardLast: Boolean = false,
        override val listFirst: Boolean = false,
        override val listLast: Boolean = false,
    ) : BusTaskAreaRow() {
        override val key: String = "project-${group.key}"
    }
}

private fun BusTaskAreaRow.withEdges(
    cardFirst: Boolean,
    cardLast: Boolean,
    listFirst: Boolean,
    listLast: Boolean,
): BusTaskAreaRow = when (this) {
    is BusTaskAreaRow.Task -> copy(
        cardFirst = cardFirst,
        cardLast = cardLast,
        listFirst = listFirst,
        listLast = listLast,
    )

    is BusTaskAreaRow.GroupHeader -> copy(
        cardFirst = cardFirst,
        cardLast = cardLast,
        listFirst = listFirst,
        listLast = listLast,
    )
}

/**
 * 统一给行打上「卡内首尾」与「列表首尾」标记
 *
 * [sameCard] 决定相邻两行是否落在同一张外层容器卡里：
 * 最近视图按待处理与否切两张卡，按项目视图所有行共用一张分组卡
 */
private fun busTaskAreaEdges(
    rows: List<BusTaskAreaRow>,
    sameCard: (BusTaskAreaRow, BusTaskAreaRow) -> Boolean,
): List<BusTaskAreaRow> = rows.mapIndexed { index, row ->
    val previous = rows.getOrNull(index - 1)
    val next = rows.getOrNull(index + 1)
    row.withEdges(
        cardFirst = previous == null || !sameCard(previous, row),
        cardLast = next == null || !sameCard(row, next),
        listFirst = index == 0,
        listLast = index == rows.lastIndex,
    )
}

/** 最近视图的行：待处理收进上卡、其余收进下卡，两卡间距 10 */
internal fun busTaskAreaRecentRows(tasks: List<BusHomeTask>): List<BusTaskAreaRow> {
    val rows = tasks.map { task -> BusTaskAreaRow.Task(task = task) }
    return busTaskAreaEdges(rows) { left, right ->
        left is BusTaskAreaRow.Task && right is BusTaskAreaRow.Task && left.task.pending == right.task.pending
    }
}

/** 按项目视图的行：所有分组共用一张分组卡，组与组之间一条发丝线 */
internal fun busTaskAreaProjectRows(
    groups: List<BusHomeProjectGroup>,
    collapsed: Set<String>,
): List<BusTaskAreaRow> {
    val rows = ArrayList<BusTaskAreaRow>(groups.size * 2)
    groups.forEach { group ->
        val expanded = group.key !in collapsed
        rows += BusTaskAreaRow.GroupHeader(
            group = group,
            expanded = expanded,
            showMenu = group.path.isNotBlank(),
            divider = rows.isNotEmpty(),
        )
        if (expanded) {
            group.tasks.forEach { task ->
                rows += BusTaskAreaRow.Task(task = task, dense = true)
            }
        }
    }
    // 分组头与它下面的任务同属一张卡
    return busTaskAreaEdges(rows) { _, _ -> true }
}

/** 任务行左侧的状态点 + 文字（取色见 §16.1 状态表，§16.10 第 6 条：不用大色块） */
internal data class BusTaskRowStatus(
    val label: String,
    val dot: Color,
    val text: Color,
    val emphasized: Boolean,
)

internal fun busTaskRowStatus(status: String, statusLabel: String): BusTaskRowStatus = when (status) {
    // 待审批：点与文字都是 #9A6A1F
    "waitingApproval" -> BusTaskRowStatus(statusLabel, BusHomeTokens.pendingText, BusHomeTokens.pendingText, true)
    // 等待输入：点 #B89A52、文字 #B54708
    "waitingInput" -> BusTaskRowStatus(statusLabel, BusHomeSectionColors.waitingDot, BusHomeSectionColors.waitingText, true)
    // 运行中：黑点黑字 600
    "running" -> BusTaskRowStatus(statusLabel, BusHomeTokens.textPrimary, BusHomeTokens.textPrimary, true)
    "failed" -> BusTaskRowStatus(statusLabel, BusHomeTokens.error, BusHomeTokens.error, true)
    // 已完成与其余状态：灰点灰字 500
    else -> BusTaskRowStatus(statusLabel, BusHomeSectionColors.doneDot, BusHomeTokens.textTertiary, false)
}

/** 任务行的摘要：挂起请求优先，其次最后一条消息；空串表示这一截不渲染 */
internal fun busTaskRowSummaryText(task: BusHomeTask): String =
    task.summary.ifBlank { task.lastMessage }

/** 任务行的 Agent 图标（一律官方图标，未知连接器回退占位图） */
internal fun busTaskRowAsset(task: BusHomeTask): String =
    busAgentAsset(task.source, task.connectorId, task.agentKey, task.agent)

/** 头像栈里的一格 */
internal data class BusAvatarSlot(
    val asset: String,
    val isPhoto: Boolean,
    /** 固定的省略号位（最后一格） */
    val ellipsis: Boolean,
    /** false = Agent 不足 3 个时留出的空位 */
    val filled: Boolean,
)

/** 省略号位之外可放的头像格数（§16.6：前 3 位显示已选 Agent） */
internal const val BUS_AVATAR_AGENT_SLOTS = 3

/**
 * 头像栈的格子：已选 Agent 优先；没勾选等价于「全部」，取电脑上的前几个，
 * 不足补空位，最后一格固定是省略号
 */
internal fun busHomeAvatarSlots(agents: List<BusHomeAgent>, selectedKeys: Set<String>): List<BusAvatarSlot> {
    val shown = if (selectedKeys.isEmpty()) agents else agents.filter { it.key in selectedKeys }
    val slots = ArrayList<BusAvatarSlot>(BUS_AVATAR_AGENT_SLOTS + 1)
    for (index in 0 until BUS_AVATAR_AGENT_SLOTS) {
        val agent = shown.getOrNull(index)
        if (agent == null) {
            slots += BusAvatarSlot(asset = "", isPhoto = false, ellipsis = false, filled = false)
        } else {
            val asset = BusAgentVisuals.assetFor(agent.source, agent.connectorId, agent.key, agent.name)
            slots += BusAvatarSlot(asset = asset, isPhoto = busAgentIconIsPhoto(asset), ellipsis = false, filled = true)
        }
    }
    slots += BusAvatarSlot(asset = "", isPhoto = false, ellipsis = true, filled = true)
    return slots
}

/** 审批队列里还没处理的条数：胶囊角标、「待处理 · N」与「全部 N」同一口径 */
internal fun busHomeOpenApprovalCount(items: List<BusApprovalItem>, answered: BusApprovalAnswered): Int =
    items.count { it.key !in answered }

/** 胶囊角标文案：超过 99 收敛成 99+，免得撑破 16 高的圆角条 */
internal fun busHomeBadgeLabel(count: Int): String = if (count > 99) "99+" else count.toString()

/** 外层容器卡宽 = 页宽 - 左右安全边距（375 - 40 = 335） */
internal fun busHomeCardWidth(pageWidth: Float): Float =
    (pageWidth - BusHomeTokens.spacePage * 2f).coerceAtLeast(0f)

/** 卡内行宽 = 卡宽 - 卡内左右 padding（335 - 32 = 303） */
internal fun busHomeRowWidth(pageWidth: Float): Float =
    (busHomeCardWidth(pageWidth) - BusHomeSectionTokens.cardPaddingHorizontal * 2f).coerceAtLeast(0f)

// ---------- 顶部区 ----------

/**
 * 头部淡紫层（§16.3 背景第 1 层）
 *
 * 设计稿是 `radial-gradient(130% 420px at 50% -120px, ...)`，Kuikly 只有线性渐变，
 * 这里用同色同高的纵向渐变近似：只在头部露出、向下渐隐到页面底色，不带硬边
 * 纯装饰层，[com.tencent.kuikly.core.base.Attr.touchEnable] 关掉，不挡点击
 * 深色主题没有这套淡紫（§16 基线只有浅色），直接不画
 */
internal fun ViewContainer<*, *>.BusHomeHeaderGlow(pageWidth: Float, isDark: Boolean) {
    if (isDark) return
    View {
        attr {
            absolutePosition(top = 0f, left = 0f)
            width(pageWidth)
            height(BusHomeSectionTokens.headerGlowHeight)
            touchEnable(false)
            backgroundLinearGradient(
                Direction.TO_BOTTOM,
                ColorStop(BusHomeTokens.headerLavender, 0f),
                ColorStop(BusHomeTokens.headerLavenderSoft, BusHomeSectionTokens.headerGlowMiddle),
                ColorStop(BusHomeTokens.headerLavenderClear, BusHomeSectionTokens.headerGlowEnd),
                ColorStop(BusHomeTokens.headerLavenderClear, 1f),
            )
        }
    }
}

/**
 * 设备入口（`device-entry`）
 *
 * 文案与计数走 lambda：快照 2 秒一轮，只让这几处 attr 重算，整块不重建
 * [disabled] = 还没有配对电脑，此时不响应点击、文字转灰
 * 设计稿的 `backdrop-filter: blur(10)` 没有对应能力，只保留半透明白底 + 高光描边 + 单层投影
 */
internal fun ViewContainer<*, *>.BusHomeDeviceEntry(
    palette: BusHomePalette,
    platformAsset: String,
    online: Boolean,
    disabled: Boolean,
    maxWidth: Float,
    title: () -> String,
    pendingCount: () -> Int,
    runningCount: () -> Int,
    onClick: () -> Unit,
) {
    View {
        attr {
            // 设计稿里这颗胶囊是「贴着内容」的，不用 flex 撑开；
            // 只给一个上限，名字太长时靠标题的单行省略收住
            maxWidth(maxWidth)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(BusHomeSectionTokens.entryPaddingHorizontal)
            paddingRight(BusHomeSectionTokens.entryPaddingHorizontal)
            paddingTop(BusHomeSectionTokens.entryPaddingVertical)
            paddingBottom(BusHomeSectionTokens.entryPaddingVertical)
            borderRadius(BusHomeTokens.radiusNavEntry)
            backgroundColor(palette.surfaceOnLight(BusHomeSectionColors.entryFill))
            border(Border(1f, BorderStyle.SOLID, palette.borderOnLight(BusHomeTokens.surfaceBorder)))
            boxShadow(
                BoxShadow(
                    0f,
                    BusHomeSectionTokens.entryShadowY,
                    BusHomeSectionTokens.entryShadowBlur,
                    BusHomeSectionColors.entryShadow,
                ),
            )
            if (disabled) opacity(BusHomeSectionTokens.entryDisabledOpacity)
        }
        if (!disabled) event { click { onClick() } }
        Image {
            attr {
                src(ImageUri.commonAssets(platformAsset))
                size(BusHomeSectionTokens.entryIconSize, BusHomeSectionTokens.entryIconSize)
                resizeContain()
                tintColor(if (disabled) palette.textTertiary else palette.textSecondary)
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                marginLeft(BusHomeTokens.gapControl)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(BusHomeSectionTokens.onlineDot, BusHomeSectionTokens.onlineDot)
                        borderRadius(BusHomeSectionTokens.onlineDot / 2f)
                        backgroundColor(
                            when {
                                disabled -> palette.textTertiary
                                online -> BusHomeTokens.success
                                else -> palette.textTertiary
                            },
                        )
                    }
                }
                Text {
                    attr {
                        text(title())
                        fontSize(BusHomeTokens.fontSizeBody)
                        fontWeightBold()
                        color(if (disabled) palette.textTertiary else palette.textPrimary)
                        marginLeft(BusHomeSectionTokens.dotTextGap)
                        flex(1f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
            }
            // 副行「待处理 N · 运行中 N」：数字要 600 转 #57534E，所以拆成四段文字
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(BusHomeSectionTokens.entrySubtitleTop)
                }
                if (disabled) {
                    // 无设备空态沿用旧稿：不报 0 计数，直接提示连上就能看任务
                    Text {
                        attr {
                            text("连接电脑后查看任务")
                            fontSize(BusHomeTokens.fontSizeCaption)
                            color(palette.textTertiary)
                        }
                    }
                } else {
                    Text {
                        attr {
                            text("待处理 ")
                            fontSize(BusHomeTokens.fontSizeCaption)
                            color(BusHomeTokens.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(pendingCount().toString())
                            fontSize(BusHomeTokens.fontSizeCaption)
                            fontWeightBold()
                            color(palette.textSecondary)
                        }
                    }
                    Text {
                        attr {
                            text(" · 运行中 ")
                            fontSize(BusHomeTokens.fontSizeCaption)
                            color(palette.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(runningCount().toString())
                            fontSize(BusHomeTokens.fontSizeCaption)
                            fontWeightBold()
                            color(palette.textSecondary)
                        }
                    }
                }
            }
        }
        if (!disabled) {
            Image {
                attr {
                    src(ImageUri.commonAssets("chevron-down.svg"))
                    size(BusHomeTokens.avatarOverlap, BusHomeTokens.avatarOverlap)
                    tintColor(palette.textTertiary)
                    marginLeft(BusHomeSectionTokens.dotTextGap)
                }
            }
        }
    }
}

/** 设置入口（`settings-entry`）40×40、radius 12，与设备入口同一套补丁质感 */
internal fun ViewContainer<*, *>.BusHomeSettingsEntry(
    palette: BusHomePalette,
    onClick: () -> Unit,
) {
    View {
        attr {
            size(BusHomeSectionTokens.settingsSize, BusHomeSectionTokens.settingsSize)
            borderRadius(BusHomeSectionTokens.settingsRadius)
            backgroundColor(palette.surfaceOnLight(BusHomeSectionColors.entryFill))
            border(Border(1f, BorderStyle.SOLID, palette.borderOnLight(BusHomeTokens.surfaceBorder)))
            boxShadow(
                BoxShadow(
                    0f,
                    BusHomeSectionTokens.entryShadowY,
                    BusHomeSectionTokens.entryShadowBlur,
                    BusHomeSectionColors.entryShadow,
                ),
            )
            alignItemsCenter()
            justifyContentCenter()
            marginLeft(BusHomeTokens.gapControl)
        }
        event { click { onClick() } }
        Image {
            attr {
                src(ImageUri.commonAssets("settings-cube.svg"))
                size(BusHomeSectionTokens.settingsIconSize, BusHomeSectionTokens.settingsIconSize)
                tintColor(palette.textSecondary)
            }
        }
    }
}

/**
 * `list-controls`：左边「最近 / 按项目」分段（审批视图换成 `approve-title`），
 * 右边 Agent 头像栈（审批视图隐藏，§16.11 以 HTML 为准）
 *
 * 高度固定 [BusHomeTokens.listControlsHeight]，切换视图时控件不得位移（§16.10 第 1 条）
 */
internal fun ViewContainer<*, *>.BusHomeListControls(
    controlsWidth: Float,
    viewMode: String,
    palette: BusHomePalette,
    agents: List<BusHomeAgent>,
    selectedKeys: Set<String>,
    approveCount: () -> Int,
    agentsEmptyLabel: () -> String,
    onSelectView: (String) -> Unit,
    onOpenAgentSheet: () -> Unit,
) {
    View {
        attr {
            width(controlsWidth)
            height(BusHomeTokens.listControlsHeight)
            flexDirectionRow()
            alignItemsCenter()
            justifyContentSpaceBetween()
            marginTop(BusHomeTokens.gapControl)
        }
        if (viewMode == BUS_VIEW_APPROVAL) {
            // 审批视图：分段换成标题，右侧头像栈保留（对照 approval-v84）
            Text {
                attr {
                    text("待处理 · ${approveCount()}")
                    fontSize(BusHomeTokens.fontSizeTitle)
                    fontWeightBold()
                    color(palette.textPrimary)
                }
            }
        } else {
            BusHomeViewSegment(viewMode = viewMode, palette = palette, onSelect = onSelectView)
        }
        if (agents.isEmpty()) {
            // 没有 Agent 数据时不露出一排空圈，也不挂点击（面板里同样没有可选项）。
            // 文案说清是哪一种空：没配对 / 正在连接 / 电脑未响应 / 电脑离线 / 没有连接器，
            // 一律「全部 Agent」会被读成「这台电脑没装 Agent」
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("menu.svg"))
                        size(BusHomeSectionTokens.agentEmptyIcon, BusHomeSectionTokens.agentEmptyIcon)
                        tintColor(BusHomeTokens.textQuiet)
                    }
                }
                Text {
                    attr {
                        text(agentsEmptyLabel())
                        fontSize(BusHomeTokens.fontSizeMeta)
                        fontWeightMedium()
                        color(BusHomeTokens.textQuiet)
                        marginLeft(BusHomeSectionTokens.dotTextGap)
                    }
                }
            }
        } else {
            BusHomeAvatarStack(agents = agents, selectedKeys = selectedKeys, onClick = onOpenAgentSheet)
        }
    }
}

/** 深色下把静态浅色面/边换成主题语义色；浅色保持原稿 */
private fun BusHomePalette.surfaceOnLight(light: Color): Color = if (isDark) surface else light
private fun BusHomePalette.borderOnLight(light: Color): Color = if (isDark) chipFill else light

/** 分段控件（`view-segment`）：白底 radius 12、padding 3，选中块 #1D1B20 + radius 9 */
private fun ViewContainer<*, *>.BusHomeViewSegment(
    viewMode: String,
    palette: BusHomePalette,
    onSelect: (String) -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(palette.surfaceOnLight(BusHomeTokens.cardFill))
            border(Border(1f, BorderStyle.SOLID, palette.chipFill))
            borderRadius(BusHomeTokens.radiusSegmented)
            padding(BusHomeTokens.segmentedPadding)
        }
        BusHomeSegmentItem(
            label = "最近",
            active = viewMode != BUS_VIEW_PROJECTS,
            palette = palette,
            onClick = { onSelect(BUS_VIEW_RECENT) },
        )
        BusHomeSegmentItem(
            label = "按项目",
            active = viewMode == BUS_VIEW_PROJECTS,
            palette = palette,
            onClick = { onSelect(BUS_VIEW_PROJECTS) },
        )
    }
}

private fun ViewContainer<*, *>.BusHomeSegmentItem(
    label: String,
    active: Boolean,
    palette: BusHomePalette,
    onClick: () -> Unit,
) {
    View {
        attr {
            borderRadius(BusHomeTokens.radiusChip)
            backgroundColor(if (active) palette.selectedFill else Color(0x00000000))
            paddingLeft(BusHomeTokens.segmentedItemHorizontal)
            paddingRight(BusHomeTokens.segmentedItemHorizontal)
            paddingTop(BusHomeTokens.segmentedItemVertical)
            paddingBottom(BusHomeTokens.segmentedItemVertical)
        }
        event { click { onClick() } }
        Text {
            attr {
                text(label)
                fontSize(BusHomeTokens.fontSizeBody)
                if (active) {
                    fontWeightBold()
                    color(palette.onSelected)
                } else {
                    fontWeightMedium()
                    color(BusHomeTokens.textTertiary)
                }
            }
        }
    }
}

/**
 * Agent 头像栈（`avatar-stack`）：26 圆、1.5px 白描边、左叠 -8，最后一格固定省略号
 *
 * 整块就是「管理全部 Agent」的入口，点击打开 [BusAgentSheet]
 */
private fun ViewContainer<*, *>.BusHomeAvatarStack(
    agents: List<BusHomeAgent>,
    selectedKeys: Set<String>,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(BusHomeTokens.avatarSize)
            flexDirectionRow()
            alignItemsCenter()
            // 设计稿只给左侧 8 的留白，用来顶住第一格的 -8 叠放位移
            paddingLeft(BusHomeSectionTokens.avatarStackPaddingLeft)
        }
        event { click { onClick() } }
        busHomeAvatarSlots(agents, selectedKeys).forEach { slot ->
            BusHomeAvatarSlotView(slot)
        }
    }
}

private fun ViewContainer<*, *>.BusHomeAvatarSlotView(slot: BusAvatarSlot) {
    View {
        attr {
            size(BusHomeTokens.avatarSize, BusHomeTokens.avatarSize)
            borderRadius(BusHomeTokens.avatarSize / 2f)
            // 每格都左移叠放，第一格靠栈容器的 padding-left 顶回原位
            marginLeft(-BusHomeTokens.avatarOverlap)
            overflow(true)
            backgroundColor(
                if (slot.filled) BusHomeTokens.cardFill else BusHomeSectionColors.avatarEmpty,
            )
            border(Border(BusHomeTokens.avatarBorder, BorderStyle.SOLID, BusHomeTokens.cardFill))
            boxShadow(
                BoxShadow(
                    0f,
                    BusHomeSectionTokens.avatarShadowY,
                    BusHomeSectionTokens.avatarShadowBlur,
                    BusHomeTokens.avatarShadow,
                ),
            )
            alignItemsCenter()
            justifyContentCenter()
        }
        if (slot.ellipsis) {
            Text {
                attr {
                    text("…")
                    fontSize(BusHomeSectionTokens.avatarEllipsisFont)
                    fontWeightBold()
                    color(BusHomeTokens.textSecondary)
                    textAlignCenter()
                }
            }
        } else if (slot.filled) {
            Image {
                attr {
                    src(ImageUri.commonAssets(slot.asset))
                    // 照片类要裁成圆：overflow(true) 裁子节点，图片自己再带一份圆角兜底
                    borderRadius(BusHomeTokens.avatarSize / 2f)
                    overflow(true)
                    if (slot.isPhoto) {
                        size(BusHomeTokens.avatarSize, BusHomeTokens.avatarSize)
                        resizeCover()
                    } else {
                        size(BusHomeSectionTokens.avatarLineIcon, BusHomeSectionTokens.avatarLineIcon)
                        resizeContain()
                    }
                }
            }
        }
    }
}

// ---------- 任务区 ----------

/**
 * 任务列表（最近 / 按项目共用）
 *
 * 沿用 M2-11：`List + vforLazy`，数据源是页面持有的 `observableList`，靠 `diffUpdate`
 * 增量更新，滚动条必须隐藏（§16.10 第 3 条），否则展开面板时整行会左移
 * 顶部 56 渐隐条是 List 的兄弟节点、绝对定位、不吃触摸（§16.3）
 */
internal fun ViewContainer<*, *>.BusHomeTaskList(
    rows: ObservableList<BusTaskAreaRow>,
    cardWidth: Float,
    rowWidth: Float,
    palette: BusHomePalette,
    topFadeVisible: () -> Boolean,
    initialRenderRows: Int,
    maxRenderedRows: Int,
    preloadHeight: Float,
    onScrollY: (Float) -> Unit,
    onOpenTask: (BusHomeTask) -> Unit,
    onToggleGroup: (String) -> Unit,
    onGroupMenu: (BusHomeProjectGroup) -> Unit,
) {
    View {
        attr {
            flex(1f)
            flexDirectionColumn()
        }
        List {
            attr {
                flex(1f)
                showScrollerIndicator(false)
                firstContentLoadMaxIndex(initialRenderRows)
                preloadViewDistance(preloadHeight)
            }
            event {
                scroll { params -> onScrollY(params.offsetY) }
                // 同上：回弹落定后补判一次，避免渐隐条在列表贴顶时残留
                scrollEnd { params -> onScrollY(params.offsetY) }
            }
            vforLazy({ rows }, maxLoadItem = maxRenderedRows) { row, _, _ ->
                BusTaskAreaRowView(
                    row = row,
                    cardWidth = cardWidth,
                    rowWidth = rowWidth,
                    palette = palette,
                    onOpenTask = onOpenTask,
                    onToggleGroup = onToggleGroup,
                    onGroupMenu = onGroupMenu,
                )
            }
        }
        BusHomeListTopFade(palette = palette, visible = topFadeVisible)
    }
}

/** 列表顶部 56 高的实心渐隐条（`#task-top-fade`）：未滚动 opacity 0，滚出顶部后 1 */
private fun ViewContainer<*, *>.BusHomeListTopFade(palette: BusHomePalette, visible: () -> Boolean) {
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, right = 0f)
            height(BusHomeTokens.topFadeHeight)
            touchEnable(false)
            backgroundLinearGradient(
                Direction.TO_BOTTOM,
                ColorStop(palette.topFadeStart, 0f),
                ColorStop(palette.topFadeMiddle, BusHomeTokens.topFadeStopMiddle),
                ColorStop(palette.topFadeBody, BusHomeTokens.topFadeStopBody),
                ColorStop(palette.topFadeClear, 1f),
            )
            // 设计稿是 `transition: opacity .16s ease`；直接改 opacity 会「啪」地刷出一道白条
            opacity(if (visible()) 1f else 0f)
            animation(Animation.easeInOut(0.16f), visible())
        }
    }
}

/**
 * 任务区一行：自己长出所属容器卡的一段（底色、圆角、卡内边距、投影、卡间距）
 *
 * §16.4 补丁的 `1px solid rgba(255,255,255,.72)` 描边没有落到片段上：
 * Kuikly 的 Border 四边同值，逐行描边会在相邻行之间压出横线，
 * 所以卡片只保留底色 + 圆角 + 主投影（单层，取 0 14 34 那层）
 */
private fun ViewContainer<*, *>.BusTaskAreaRowView(
    row: BusTaskAreaRow,
    cardWidth: Float,
    rowWidth: Float,
    palette: BusHomePalette,
    onOpenTask: (BusHomeTask) -> Unit,
    onToggleGroup: (String) -> Unit,
    onGroupMenu: (BusHomeProjectGroup) -> Unit,
) {
    View {
        attr {
            width(cardWidth)
            marginLeft(BusHomeTokens.spacePage)
            marginRight(BusHomeTokens.spacePage)
            flexDirectionColumn()
            backgroundColor(palette.surface)
            borderRadius(busTaskAreaCardRadius(row))
            paddingLeft(BusHomeSectionTokens.cardPaddingHorizontal)
            paddingRight(BusHomeSectionTokens.cardPaddingHorizontal)
            paddingTop(if (row.cardFirst) BusHomeSectionTokens.cardPaddingTop else 0f)
            paddingBottom(if (row.cardLast) busTaskAreaCardPaddingBottom(row) else 0f)
            marginTop(if (row.listFirst) BusHomeTokens.spaceListTop else 0f)
            marginBottom(busTaskAreaCardBottomGap(row))
            // 投影只挂卡的首尾片段：中段也投的话相邻行之间会压出一道灰带
            if (row.cardFirst || row.cardLast) {
                boxShadow(
                    BoxShadow(
                        0f,
                        BusHomeSectionTokens.cardShadowY,
                        BusHomeSectionTokens.cardShadowBlur,
                        BusHomeSectionColors.cardShadow,
                    ),
                )
            }
        }
        when (row) {
            is BusTaskAreaRow.Task -> BusTaskRowContent(
                task = row.task,
                rowWidth = rowWidth,
                dense = row.dense,
                palette = palette,
                onClick = { onOpenTask(row.task) },
            )

            is BusTaskAreaRow.GroupHeader -> BusProjectHeaderContent(
                row = row,
                rowWidth = rowWidth,
                palette = palette,
                onToggle = { onToggleGroup(row.group.key) },
                onMenu = { onGroupMenu(row.group) },
            )
        }
    }
}

/** 卡首尾片段的圆角；单独一行成卡时四角全圆 */
internal fun busTaskAreaCardRadius(row: BusTaskAreaRow): BorderRectRadius {
    val radius = BusHomeTokens.radiusCard
    return when {
        row.cardFirst && row.cardLast -> BorderRectRadius(radius, radius, radius, radius)
        row.cardFirst -> BorderRectRadius(radius, radius, 0f, 0f)
        row.cardLast -> BorderRectRadius(0f, 0f, radius, radius)
        else -> BorderRectRadius(0f, 0f, 0f, 0f)
    }
}

/** 卡尾片段的下内边距：分组卡 `5 16 10`，时间线卡 `5 16 3` */
internal fun busTaskAreaCardPaddingBottom(row: BusTaskAreaRow): Float = when {
    row is BusTaskAreaRow.GroupHeader -> BusHomeSectionTokens.groupCardPaddingBottom
    row is BusTaskAreaRow.Task && row.dense -> BusHomeSectionTokens.groupCardPaddingBottom
    else -> BusHomeSectionTokens.cardPaddingBottom
}

/** 卡尾片段与下一张卡的间距；列表末行改成给胶囊与 FAB 预留 128 */
internal fun busTaskAreaCardBottomGap(row: BusTaskAreaRow): Float = when {
    row.listLast -> BusHomeSectionTokens.listBottomInset
    row.cardLast -> BusHomeTokens.gapControl
    else -> 0f
}

/** 任务行内容：Agent 图标 36 圆 + 标题/时间 + 状态点与摘要 */
private fun ViewContainer<*, *>.BusTaskRowContent(
    task: BusHomeTask,
    rowWidth: Float,
    dense: Boolean,
    palette: BusHomePalette,
    onClick: () -> Unit,
) {
    val status = busTaskRowStatus(task.status, task.statusLabel)
    val verticalPadding = if (dense || !task.pending) {
        BusHomeSectionTokens.rowPaddingVerticalDense
    } else {
        BusHomeSectionTokens.rowPaddingVertical
    }
    val asset = busTaskRowAsset(task)
    View {
        attr {
            width(rowWidth)
            flexDirectionRow()
            alignItemsFlexStart()
            paddingTop(verticalPadding)
            paddingBottom(verticalPadding)
        }
        event { click { onClick() } }
        View {
            attr {
                size(BusSheetTokens.rowIconSize, BusSheetTokens.rowIconSize)
                borderRadius(BusSheetTokens.rowIconSize / 2f)
                backgroundColor(BusHomeTokens.field)
                overflow(true)
                alignItemsCenter()
                justifyContentCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(asset))
                    // 同头像栈：照片类铺满 36 圆，圆角靠 overflow 裁，图片自身再兜一层
                    borderRadius(BusSheetTokens.rowIconSize / 2f)
                    overflow(true)
                    if (busAgentIconIsPhoto(asset)) {
                        size(BusSheetTokens.rowIconSize, BusSheetTokens.rowIconSize)
                        resizeCover()
                    } else {
                        size(BusHomeSectionTokens.rowIconLineSize, BusHomeSectionTokens.rowIconLineSize)
                        resizeContain()
                    }
                }
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                marginLeft(BusHomeTokens.gapRow)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(task.title)
                        fontSize(BusHomeTokens.fontSizeTitle)
                        fontWeightBold()
                        lineHeight(BusHomeSectionTokens.titleLineHeight)
                        color(palette.textPrimary)
                        flex(1f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
                Text {
                    attr {
                        text(task.time)
                        fontSize(BusHomeTokens.fontSizeCaption)
                        lineHeight(BusHomeSectionTokens.captionLineHeight)
                        color(BusHomeTokens.textTertiary)
                        marginLeft(BusHomeTokens.gapControl)
                    }
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(if (dense) BusHomeSectionTokens.metaRowTopDense else BusHomeSectionTokens.metaRowTop)
                }
                View {
                    attr {
                        size(BusHomeSectionTokens.onlineDot, BusHomeSectionTokens.onlineDot)
                        borderRadius(BusHomeSectionTokens.onlineDot / 2f)
                        backgroundColor(status.dot)
                        marginRight(BusHomeSectionTokens.metaGap)
                    }
                }
                Text {
                    attr {
                        text(status.label)
                        fontSize(BusHomeTokens.fontSizeCaption)
                        lineHeight(BusHomeSectionTokens.captionLineHeight)
                        if (status.emphasized) fontWeightBold() else fontWeightMedium()
                        color(status.text)
                        marginRight(BusHomeSectionTokens.metaGap)
                    }
                }
                if (!dense) {
                    Text {
                        attr {
                            text(task.agent)
                            fontSize(BusHomeTokens.fontSizeMeta)
                            fontWeightMedium()
                            color(BusHomeTokens.textSecondary)
                            lines(1)
                            textOverFlowTail()
                            marginRight(BusHomeSectionTokens.metaGap)
                        }
                    }
                }
                val summary = busTaskRowSummaryText(task)
                if (summary.isNotBlank()) {
                    Text {
                        attr {
                            text("· $summary")
                            fontSize(BusHomeTokens.fontSizeMeta)
                            lineHeight(BusHomeSectionTokens.captionLineHeight)
                            color(BusHomeTokens.textTertiary)
                            flex(1f)
                            lines(1)
                            textOverFlowTail()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分组头（`group-head`）：28 高，标题 14/600 + 16 箭头
 *
 * 箭头：展开朝下、收起朝右；右侧不再放菜单图标，
 * 「移出项目」改由长按分组头触发
 */
private fun ViewContainer<*, *>.BusProjectHeaderContent(
    row: BusTaskAreaRow.GroupHeader,
    rowWidth: Float,
    palette: BusHomePalette,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
) {
    // 发丝线要贴到卡片内边缘，所以这一行不带左右内缩，由内部元素自己收
    View {
        attr {
            width(rowWidth)
            flexDirectionColumn()
        }
        if (row.divider) {
            View {
                attr {
                    height(BusHomeSectionTokens.hairlineHeight)
                    marginLeft(BusHomeSectionTokens.cardPaddingHorizontal)
                    marginRight(BusHomeSectionTokens.cardPaddingHorizontal)
                    backgroundColor(BusHomeSectionColors.hairline)
                }
            }
        }
        View {
            attr {
                height(BusHomeSectionTokens.groupHeadHeight)
                flexDirectionRow()
                alignItemsCenter()
                // 卡片本身已有 16 内边距，箭头贴到内容右缘即与标题左缘对称
            }
            event {
                click { onToggle() }
                longPress { params ->
                    if (params.isStart && row.showMenu) onMenu()
                }
            }
            Text {
                attr {
                    text(row.group.name)
                    fontSize(BusHomeSectionTokens.groupHeadFont)
                    fontWeightBold()
                    color(palette.textPrimary)
                    flex(1f)
                    lines(1)
                    textOverFlowTail()
                }
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("chevron-down.svg"))
                    size(BusHomeSectionTokens.groupHeadIcon, BusHomeSectionTokens.groupHeadIcon)
                    tintColor(BusHomeTokens.textTertiary)
                    transform(Rotate(if (row.expanded) 0f else 270f))
                }
            }
        }
    }
}

/** 任务区空态（有电脑但一条可见任务都没有）：保留原有插画与文案 */
internal fun ViewContainer<*, *>.BusHomeEmptyTasks(palette: BusHomePalette, isDark: Boolean) {
    View {
        attr {
            flex(1f)
            flexDirectionColumn()
            alignItemsCenter()
            justifyContentCenter()
            paddingBottom(BusHomeSectionTokens.emptyBottomInset)
        }
        Image {
            attr {
                src(
                    ImageUri.commonAssets(
                        if (isDark) "empty-no-tasks-dark.svg" else "empty-no-tasks.svg",
                    ),
                )
                size(BusHomeSectionTokens.emptyIconWidth, BusHomeSectionTokens.emptyIconHeight)
            }
        }
        Text {
            attr {
                text("暂无任务")
                fontSize(BusHomeSectionTokens.emptyTitleFont)
                fontWeightBold()
                color(palette.textSecondary)
                marginTop(BusHomeSectionTokens.emptyTitleTop)
                textAlignCenter()
            }
        }
        Text {
            attr {
                text("任务完成后\n回到这里查看")
                fontSize(BusHomeTokens.fontSizeBody)
                color(palette.textTertiary)
                lineHeight(BusHomeSectionTokens.emptySubLineHeight)
                marginTop(BusHomeTokens.gapControl)
                textAlignCenter()
            }
        }
    }
}

/**
 * 无设备空态：还没有连接电脑（插画取自 empty-state-assets/empty-no-device.svg）
 *
 * 没有电脑时主区不再留白，给三条连接入口：查看引导 / 扫码添加连接 / 通过 SSH 连接。
 * 配色沿用 v86 冷灰体系，不恢复旧稿的紫色强调。
 */
internal fun ViewContainer<*, *>.BusHomeNoComputer(
    palette: BusHomePalette,
    onGuide: () -> Unit,
    onScan: () -> Unit,
    onSsh: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            flexDirectionColumn()
            alignItemsCenter()
            justifyContentCenter()
            paddingBottom(BusHomeSectionTokens.emptyBottomInset)
        }
        Image {
            attr {
                // 这张插画原来只有浅色一版，深色下整块白机身直接压在 #151517 的页面底上，
                // 和同屏其他缺省页（都有 -dark 一版）差一整档；补齐深色变体后按主题取
                src(
                    ImageUri.commonAssets(
                        if (palette.isDark) "empty-no-device-dark.svg" else "empty-no-device.svg",
                    ),
                )
                size(BusHomeSectionTokens.emptyIconWidth, BusHomeSectionTokens.emptyIconHeight)
            }
        }
        Text {
            attr {
                text("还没有连接电脑")
                fontSize(BusHomeSectionTokens.emptyTitleFont)
                fontWeightBold()
                color(palette.textSecondary)
                marginTop(BusHomeSectionTokens.emptyTitleTop)
                textAlignCenter()
            }
        }
        Text {
            attr {
                text("连接后即可在手机上查看\n电脑上的 Agent 和任务")
                fontSize(BusHomeTokens.fontSizeBody)
                color(palette.textTertiary)
                lineHeight(BusHomeSectionTokens.emptySubLineHeight)
                marginTop(BusHomeTokens.gapControl)
                textAlignCenter()
            }
        }
        View {
            attr {
                marginTop(BusHomeSectionTokens.noDeviceGuideTop)
                borderRadius(BusHomeTokens.radiusNavEntry)
                border(Border(1f, BorderStyle.DASHED, BusHomeSectionColors.noDeviceDashed))
                paddingLeft(BusHomeSectionTokens.noDeviceGuideHorizontal)
                paddingRight(BusHomeSectionTokens.noDeviceGuideHorizontal)
                paddingTop(BusHomeSectionTokens.noDeviceGuideVertical)
                paddingBottom(BusHomeSectionTokens.noDeviceGuideVertical)
            }
            event { click { onGuide() } }
            Text {
                attr {
                    text("查看引导")
                    fontSize(BusHomeTokens.fontSizeBody)
                    fontWeightBold()
                    color(palette.textSecondary)
                }
            }
        }
        View {
            attr {
                marginTop(BusHomeSectionTokens.noDeviceLinksTop)
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text("扫码添加连接 ›")
                    fontSize(BusHomeTokens.fontSizeBody)
                    fontWeightBold()
                    color(palette.textPrimary)
                }
                event { click { onScan() } }
            }
            Text {
                attr {
                    text("通过 SSH 连接 ›")
                    fontSize(BusHomeTokens.fontSizeBody)
                    color(palette.textSecondary)
                    marginLeft(BusHomeSectionTokens.noDeviceLinkGap)
                }
                event { click { onSsh() } }
            }
        }
    }
}

// ---------- 底部栏 ----------

/**
 * 底部 160 高的 `bottom-fade`（§16.3 底部收口）：贴页面底边、不吃触摸
 *
 * 设计稿在整块画布上还有一层 3.5% 的 `mix-blend-mode: multiply` 噪点，
 * Kuikly 没有混合模式，跳过这层
 */
internal fun ViewContainer<*, *>.BusHomeBottomFade(isDark: Boolean) {
    if (isDark) return
    View {
        attr {
            absolutePosition(left = 0f, right = 0f, bottom = 0f)
            height(BusHomeTokens.bottomFadeHeight)
            touchEnable(false)
            backgroundLinearGradient(
                Direction.TO_TOP,
                ColorStop(BusHomeTokens.bgBase, 0f),
                ColorStop(BusHomeTokens.bottomFadeMid, BusHomeSectionTokens.bottomFadeMiddle),
                ColorStop(BusHomeTokens.topFadeClear, 1f),
            )
        }
    }
}

/**
 * 底部悬浮栏（`bottom-bar`）：胶囊居中、FAB 贴右
 *
 * 设计稿用 `margin: 0 auto` 让胶囊在 FAB 左侧的剩余空间里居中，
 * 这里用两个等分占位块做出同一结果。整栏绝对定位，
 * 面板开合不会挤压列表高度（§16.10 第 1 条），
 * 页面内覆盖层（设置页）打开时整栏透明且不接收触摸
 */
internal fun ViewContainer<*, *>.BusHomeBottomBar(
    barWidth: Float,
    palette: BusHomePalette,
    viewMode: () -> String,
    badgeCount: () -> Int,
    hidden: () -> Boolean,
    fabEnabled: Boolean,
    onSelectListView: () -> Unit,
    onSelectApprovalView: () -> Unit,
    onNewTask: () -> Unit,
) {
    View {
        attr {
            positionType(FlexPositionType.ABSOLUTE)
            left(BusHomeTokens.spacePage)
            // 贴底距离取设计值与实际导航栏高度的较大者：无导航栏时就是稿子的 36
            bottom(maxOf(BusHomeTokens.capsuleBottomInset, pagerData.safeAreaInsets.bottom))
            width(barWidth)
            flexDirectionRow()
            alignItemsCenter()
            opacity(if (hidden()) 0f else 1f)
            touchEnable(!hidden())
        }
        View { attr { flex(1f) } }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                borderRadius(BusHomeTokens.radiusCapsuleShell)
                backgroundColor(palette.surfaceOnLight(BusHomeSectionColors.capsuleFill))
                border(Border(1f, BorderStyle.SOLID, palette.borderOnLight(BusHomeSectionColors.capsuleBorder)))
                boxShadow(
                    BoxShadow(
                        0f,
                        BusHomeSectionTokens.capsuleShadowY,
                        BusHomeSectionTokens.capsuleShadowBlur,
                        BusHomeSectionColors.capsuleShadow,
                    ),
                )
                padding(BusHomeTokens.capsuleShellPadding)
            }
            BusHomeCapsuleSegment(
                label = "列表",
                palette = palette,
                outlineAsset = "capsule-messages-outline.svg",
                filledAsset = "capsule-messages-filled.svg",
                selected = { busIsListViewMode(viewMode()) },
                badgeCount = { 0 },
                onClick = onSelectListView,
            )
            BusHomeCapsuleSegment(
                label = "待审批",
                palette = palette,
                outlineAsset = "capsule-terminal-outline.svg",
                filledAsset = "capsule-terminal-filled.svg",
                selected = { viewMode() == BUS_VIEW_APPROVAL },
                badgeCount = badgeCount,
                onClick = onSelectApprovalView,
            )
        }
        View { attr { flex(1f) } }
        View {
            attr {
                size(BusHomeTokens.fabSize, BusHomeTokens.fabSize)
                borderRadius(BusHomeTokens.fabSize / 2f)
                // 禁用态不再整块 opacity：透明度层会把圆角背景和阴影合成到矩形范围，
                // 在浅灰页面上露出一块方形底；改成只去掉投影、图标转灰
                backgroundColor(palette.surfaceOnLight(BusHomeTokens.cardFill))
                if (fabEnabled) {
                    // CSS 的 box-shadow 只画在元素外侧；fill=true 会把阴影垫到圆底
                    boxShadow(
                        BoxShadow(
                            0f,
                            BusHomeSectionTokens.fabShadowY,
                            BusHomeSectionTokens.fabShadowBlur,
                            BusHomeTokens.fabShadow,
                            fill = false,
                        ),
                    )
                }
                alignItemsCenter()
                justifyContentCenter()
            }
            event {
                click {
                    if (fabEnabled) onNewTask()
                }
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("new-session.svg"))
                    size(BusHomeSectionTokens.fabIconSize, BusHomeSectionTokens.fabIconSize)
                    tintColor(if (fabEnabled) palette.textPrimary else palette.textTertiary)
                }
            }
        }
    }
}

/** 胶囊分段：84×52、radius 26；选中 = #1D1B20 实心 + filled 图标，未选中 = 透明 + outline + .72 */
private fun ViewContainer<*, *>.BusHomeCapsuleSegment(
    label: String,
    palette: BusHomePalette,
    outlineAsset: String,
    filledAsset: String,
    selected: () -> Boolean,
    badgeCount: () -> Int,
    onClick: () -> Unit,
) {
    View {
        attr {
            size(BusHomeTokens.capsuleWidth, BusHomeTokens.capsuleHeight)
            borderRadius(BusHomeTokens.radiusCapsule)
            backgroundColor(if (selected()) palette.selectedFill else Color(0x00000000))
            alignItemsCenter()
            justifyContentCenter()
            opacity(if (selected()) 1f else BusHomeSectionTokens.capsuleIdleOpacity)
            // 分段只有图标，无障碍名字用文案补上
            accessibility(label)
        }
        event { click { onClick() } }
        vif({ selected() }) {
            Image {
                attr {
                    src(ImageUri.commonAssets(filledAsset))
                    size(BusHomeTokens.capsuleIconSize, BusHomeTokens.capsuleIconSize)
                    tintColor(palette.onSelected)
                }
            }
        }
        vif({ !selected() }) {
            Image {
                attr {
                    src(ImageUri.commonAssets(outlineAsset))
                    size(BusHomeTokens.capsuleIconSize, BusHomeTokens.capsuleIconSize)
                    tintColor(palette.textTertiary)
                }
            }
        }
        // 待审批角标只出现在终端分段上（列表分段传 0，永远不出角标）
        vif({ badgeCount() > 0 }) {
            View {
                attr {
                    positionType(FlexPositionType.ABSOLUTE)
                    top(BusHomeTokens.badgeTop)
                    right(BusHomeTokens.badgeEnd)
                    minWidth(BusHomeTokens.badgeMinWidth)
                    height(BusHomeTokens.badgeHeight)
                    borderRadius(BusHomeTokens.radiusBadge)
                    backgroundColor(BusHomeTokens.badgeFill)
                    paddingLeft(BusHomeTokens.spaceBadge)
                    paddingRight(BusHomeTokens.spaceBadge)
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Text {
                    attr {
                        text(busHomeBadgeLabel(badgeCount()))
                        fontSize(BusHomeTokens.fontSizeBadge)
                        fontWeightBold()
                        color(BusHomeTokens.badgeText)
                        textAlignCenter()
                    }
                }
            }
        }
    }
}
