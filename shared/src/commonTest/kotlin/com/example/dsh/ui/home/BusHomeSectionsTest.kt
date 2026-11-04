package com.example.dsh.ui.home

import com.example.dsh.ui.dshTopInset
import com.tencent.kuikly.core.base.BorderRectRadius
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 首页渲染层纯函数断言：外层容器卡的切分、状态取色、头像栈格子、审批计数
 *
 * 这些都不碰 Kuikly 渲染，只验算「行数据 -> 该画成什么样」的映射，
 * 因为 v86 的卡片不是真的父容器，而是由每一行自己长出卡的一段，
 * 位置标记一旦算错就是肉眼可见的圆角 / 间距错位
 */
class BusHomeSectionsTest {

    private fun task(
        id: String,
        status: String,
        pending: Boolean,
        summary: String = "",
        lastMessage: String = "",
    ) = BusHomeTask(
        id = id,
        title = "任务 $id",
        status = status,
        statusLabel = taskStatusLabel(status),
        agent = "Codex",
        agentKey = "codex",
        project = "agentbus",
        time = "10-29 08:30",
        taskType = "code",
        pending = pending,
        summary = summary,
        lastMessage = lastMessage,
    )

    private fun agent(key: String, source: String, connectorId: String? = null) = BusHomeAgent(
        key = key,
        name = key,
        available = true,
        enabled = true,
        status = "ok",
        source = source,
        connectorId = connectorId,
    )

    private fun item(key: String, replyOnly: Boolean) = BusApprovalItem(
        key = key,
        taskId = key.substringBefore('|'),
        requestId = key.substringAfter('|'),
        kind = if (replyOnly) "input" else "command",
        title = "卡 $key",
        project = "agentbus",
        agentName = "Codex",
        time = "10-29 08:30",
        summary = "请求执行：ls",
        questions = emptyList(),
        replyOnly = replyOnly,
    )

    // ---------- 最近视图：两张容器卡 ----------

    @Test
    fun recentRowsSplitIntoPendingAndRestCards() {
        val rows = busTaskAreaRecentRows(
            listOf(
                task("p1", "waitingApproval", pending = true),
                task("p2", "waitingInput", pending = true),
                task("r1", "running", pending = false),
                task("c1", "completed", pending = false),
            ),
        )
        // 两张卡各自的首尾才带圆角：p1/r1 圆上角，p2/c1 圆下角
        assertEquals(listOf(true, false, true, false), rows.map { it.cardFirst })
        assertEquals(listOf(false, true, false, true), rows.map { it.cardLast })
        // 列表首尾决定顶部 16 与底部 128 预留
        assertEquals(listOf(true, false, false, false), rows.map { it.listFirst })
        assertEquals(listOf(false, false, false, true), rows.map { it.listLast })
    }

    @Test
    fun recentRowsWithoutPendingStayInOneCard() {
        val rows = busTaskAreaRecentRows(
            listOf(task("r1", "running", false), task("c1", "completed", false)),
        )
        assertTrue(rows.first().cardFirst && !rows.first().cardLast)
        assertTrue(rows.last().cardLast && !rows.last().cardFirst)
    }

    @Test
    fun recentRowsWithSingleTaskRoundAllCorners() {
        val rows = busTaskAreaRecentRows(listOf(task("only", "running", false)))
        val row = rows.single()
        assertTrue(row.cardFirst && row.cardLast && row.listFirst && row.listLast)
        assertEquals(
            BusHomeSectionTokens.listBottomInset,
            busTaskAreaCardBottomGap(row),
        )
    }

    @Test
    fun cardEdgesDriveRadiusPaddingAndGap() {
        val middle = BusTaskAreaRow.Task(task("m", "running", false))
        assertEquals(0f, busTaskAreaCardBottomGap(middle))

        val lastOfTopCard = BusTaskAreaRow.Task(
            task("p", "waitingApproval", true),
            cardFirst = false,
            cardLast = true,
        )
        assertEquals(BusHomeTokens.gapControl, busTaskAreaCardBottomGap(lastOfTopCard))
        assertEquals(BusHomeSectionTokens.cardPaddingBottom, busTaskAreaCardPaddingBottom(lastOfTopCard))

        // 分组卡的下内边距是 10，不是时间线卡的 3
        val groupTail = BusTaskAreaRow.GroupHeader(
            group = BusHomeProjectGroup(key = "g", name = "G", tasks = emptyList()),
            expanded = true,
            showMenu = false,
            divider = false,
            cardLast = true,
        )
        assertEquals(BusHomeSectionTokens.groupCardPaddingBottom, busTaskAreaCardPaddingBottom(groupTail))
    }

    // ---------- 按项目视图：一张分组卡 ----------

    @Test
    fun projectRowsShareOneCardAndPutHairlineOnLaterHeaders() {
        val groups = listOf(
            BusHomeProjectGroup("a", "AgentBus", listOf(task("a1", "running", false), task("a2", "completed", false)), "/a"),
            BusHomeProjectGroup("b", "Notes", listOf(task("b1", "completed", false)), "/b"),
        )
        val rows = busTaskAreaProjectRows(groups, collapsed = emptySet())
        assertEquals(
            listOf("project-a", "task-a1", "task-a2", "project-b", "task-b1"),
            rows.map { it.key },
        )
        // 一张卡：只有整列首尾带圆角
        assertEquals(listOf(true, false, false, false, false), rows.map { it.cardFirst })
        assertEquals(listOf(false, false, false, false, true), rows.map { it.cardLast })
        val headers = rows.filterIsInstance<BusTaskAreaRow.GroupHeader>()
        assertEquals(listOf(false, true), headers.map { it.divider })
        // 按项目视图的任务行是紧凑排布，且不显示 Agent 名
        assertTrue(rows.filterIsInstance<BusTaskAreaRow.Task>().all { it.dense })
    }

    @Test
    fun collapsedProjectKeepsHeaderOnly() {
        val groups = listOf(
            BusHomeProjectGroup("a", "AgentBus", listOf(task("a1", "running", false)), "/a"),
            BusHomeProjectGroup("b", "Notes", listOf(task("b1", "completed", false)), "/b"),
        )
        val rows = busTaskAreaProjectRows(groups, collapsed = setOf("b"))
        assertEquals(listOf("project-a", "task-a1", "project-b"), rows.map { it.key })
        val folded = rows.last() as BusTaskAreaRow.GroupHeader
        assertFalse(folded.expanded)
    }

    // ---------- 任务行取色与摘要 ----------

    @Test
    fun rowStatusFollowsDesignTokenTable() {
        // §16.1：待审批点与文字同色 #9A6A1F；等待输入点 #B89A52、文字 #B54708
        assertEquals(BusHomeTokens.pendingText, busTaskRowStatus("waitingApproval", "待审批").dot)
        assertEquals(BusHomeTokens.pendingText, busTaskRowStatus("waitingApproval", "待审批").text)
        val waiting = busTaskRowStatus("waitingInput", "等待输入")
        assertEquals(BusHomeSectionColors.waitingDot, waiting.dot)
        assertEquals(BusHomeSectionColors.waitingText, waiting.text)
        // 运行中是黑点黑字 600，已完成是灰点灰字 500
        assertEquals(BusHomeTokens.textPrimary, busTaskRowStatus("running", "运行中").dot)
        assertTrue(busTaskRowStatus("running", "运行中").emphasized)
        val done = busTaskRowStatus("completed", "已完成")
        assertEquals(BusHomeSectionColors.doneDot, done.dot)
        assertFalse(done.emphasized)
        assertEquals(BusHomeTokens.textTertiary, busTaskRowStatus("idle", "空闲").text)
    }

    @Test
    fun rowSummaryFallsBackToLastMessage() {
        assertEquals("请求执行：ls", busTaskRowSummaryText(task("x", "waitingApproval", true, summary = "请求执行：ls", lastMessage = "旧消息")))
        assertEquals("旧消息", busTaskRowSummaryText(task("x", "completed", false, summary = "", lastMessage = "旧消息")))
        assertEquals("", busTaskRowSummaryText(task("x", "completed", false)))
    }

    @Test
    fun rowAssetUsesOfficialAgentIcons() {
        assertEquals(BusAgentVisuals.CLAUDE, busAgentAsset("acp", "claude-code"))
        // source 缺失品牌信息时，用 agentKey / 名字兜底到专属头像
        assertEquals(BusAgentVisuals.CODEX, busTaskRowAsset(task("x", "completed", false).copy(source = "mystery")))
        // 通用 ACP 即使名字叫 Codex，也保持中性头像
        assertEquals(
            BusAgentVisuals.FALLBACK,
            busTaskRowAsset(
                task("x", "completed", false)
                    .copy(source = "acp", connectorId = "some-manifest-id"),
            ),
        )
    }

    // ---------- 头像栈 ----------

    @Test
    fun avatarStackAlwaysEndsWithEllipsisSlot() {
        val agents = listOf(agent("codex", "codex"), agent("acp:claude-code", "acp", "claude-code"))
        val slots = busHomeAvatarSlots(agents, selectedKeys = emptySet())
        assertEquals(BUS_AVATAR_AGENT_SLOTS + 1, slots.size)
        assertTrue(slots.last().ellipsis)
        // 没勾选 = 全部：前几格铺电脑上的 Agent，剩下的给空位
        assertEquals(listOf(true, true, false), slots.take(3).map { it.filled })
        assertEquals(BusAgentVisuals.CODEX, slots[0].asset)
        assertTrue(slots[0].isPhoto)
        assertFalse(slots[2].filled)
    }

    @Test
    fun avatarStackShowsSelectedAgentsFirst() {
        val agents = listOf(agent("codex", "codex"), agent("dsh", "dsh"), agent("pi", "pi"))
        val slots = busHomeAvatarSlots(agents, selectedKeys = setOf("pi"))
        assertEquals(listOf(BusAgentVisuals.PI, "", ""), slots.take(3).map { it.asset })
    }

    // ---------- 审批计数 ----------

    @Test
    fun openApprovalCountIgnoresAnsweredCards() {
        val items = listOf(item("t1|r1", replyOnly = false), item("t2|r2", replyOnly = true))
        assertEquals(2, busHomeOpenApprovalCount(items, emptyMap()))
        assertEquals(1, busHomeOpenApprovalCount(items, mapOf("t1|r1" to true)))
    }

    @Test
    fun badgeLabelCapsAtNinetyNine() {
        assertEquals("3", busHomeBadgeLabel(3))
        assertEquals("99+", busHomeBadgeLabel(100))
    }

    @Test
    fun approvalFilterOnlyHidesListControlsInApprovalView() {
        assertTrue(busIsListViewMode(BUS_VIEW_RECENT))
        assertTrue(busIsListViewMode(BUS_VIEW_PROJECTS))
        assertFalse(busIsListViewMode(BUS_VIEW_APPROVAL))
    }

    // ---------- 尺寸换算 ----------

    @Test
    fun cardAndRowWidthsFollowDesignCanvas() {
        assertEquals(335f, busHomeCardWidth(BusHomeTokens.designPageWidth))
        assertEquals(303f, busHomeRowWidth(BusHomeTokens.designPageWidth))
        // 窄屏也不会算出负数
        assertEquals(0f, busHomeCardWidth(20f))
    }

    @Test
    fun cardRadiusCoversFourCases() {
        val radius = BusHomeTokens.radiusCard
        // BorderRectRadius 只暴露 toString，用它比对四角
        fun expect(topLeft: Float, topRight: Float, bottomLeft: Float, bottomRight: Float) =
            BorderRectRadius(topLeft, topRight, bottomLeft, bottomRight).toString()
        val single = BusTaskAreaRow.Task(task("s", "running", false), cardFirst = true, cardLast = true)
        assertEquals(expect(radius, radius, radius, radius), busTaskAreaCardRadius(single).toString())
        val head = BusTaskAreaRow.Task(task("h", "running", false), cardFirst = true)
        assertEquals(expect(radius, radius, 0f, 0f), busTaskAreaCardRadius(head).toString())
        val tail = BusTaskAreaRow.Task(task("t", "running", false), cardLast = true)
        assertEquals(expect(0f, 0f, radius, radius), busTaskAreaCardRadius(tail).toString())
        val middle = BusTaskAreaRow.Task(task("m", "running", false))
        assertEquals(expect(0f, 0f, 0f, 0f), busTaskAreaCardRadius(middle).toString())
    }

    // ---------- 安全区 ----------

    @Test
    fun topInsetTakesLargerOfStatusBarAndCutout() {
        // 普通机：statusBar = safeTop
        assertEquals(24f, dshTopInset(24f, 24f))
        // 挖孔机：safeAreaInsets.top 比 statusBarHeight 大，取大的
        assertEquals(48f, dshTopInset(24f, 48f))
        // 极端情况也不小于 statusBarHeight
        assertEquals(24f, dshTopInset(24f, 0f))
    }
}
