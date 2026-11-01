package com.example.dsh.ui.home

import com.example.dsh.bus.BusStore
import com.example.dsh.bus.busParse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 首页审批投影与 Agent 筛选的数据层断言：
 * 新 wire 字段映射、`canStartTask` 派生、多选筛选、审批投影。
 * 全部是纯函数，不碰渲染。
 */
class BusV86ModelTest {

    // ---------- wire 字段映射 ----------

    @Test
    fun connectorAndDeviceCapabilitiesMapFromSnapshot() {
        val view = previewBusHomeView()
        assertTrue(view.agents.first { it.key == "acp:claude-code" }.canAutoApprove)
        assertFalse(view.agents.first { it.key == "codex" }.canAutoApprove)
        assertTrue(view.device?.canRestartConnectors == true)
    }

    @Test
    fun pendingRequestMapsOntoTask() {
        val task = previewBusHomeView().tasks.first { it.id == "acp:claude-code:s1" }
        assertEquals("toolu_01MergePr", task.requestId)
        assertEquals("permission", task.kind)
        assertEquals("请求执行：git merge pr-42", task.summary)
        assertTrue(task.detail.startsWith("把 origin/feature/packaging"))
        assertTrue(task.hasPendingRequest)

        val question = task.questions.single()
        assertEquals("scope", question.id)
        assertEquals("批准范围", question.question)
        assertEquals(listOf("只这一次" to "仅放行本次命令", "以后都允许" to "写进这台电脑的白名单"), question.options)
        // 单选在协议里是整个省略 multiSelect，收敛成 false。
        assertFalse(question.multiSelect)
    }

    @Test
    fun multiSelectQuestionKeepsLiteralTrueAndBlankOptions() {
        val task = previewBusHomeView().tasks.first { it.id == "dsh:s3" }
        assertEquals("input", task.kind)
        val question = task.questions.single()
        assertTrue(question.multiSelect)
        assertEquals(listOf("relay.log" to "", "tunnel.log" to ""), question.options)
    }

    @Test
    fun taskWithoutPendingRequestStaysClean() {
        val task = previewBusHomeView().tasks.first { it.id == "codex:s2" }
        assertEquals("", task.requestId)
        assertEquals("", task.kind)
        assertEquals(emptyList<BusApprovalQuestion>(), task.questions)
        assertFalse(task.hasPendingRequest)
    }

    @Test
    fun pendingRequestSurvivesSnapshotRoundTrip() {
        val original = previewBusHomeView().tasks.first { it.id == "acp:claude-code:s1" }
        val store = BusStore()
        store.applySnapshot(busParse(BUS_HOME_FIXTURE_JSON))
        val second = BusStore()
        second.applySnapshot(busParse(store.snapshotJson()))
        val roundTripped = second.toHomeView().tasks.first { it.id == "acp:claude-code:s1" }
        assertEquals(original, roundTripped)
    }

    // ---------- canStartTask 派生 ----------

    @Test
    fun canStartTaskDerivesFromAvailableEnabledAndSource() {
        assertTrue(busAgentCanStartTask(available = true, enabled = true, source = "acp"))
        assertTrue(busAgentCanStartTask(available = true, enabled = true, source = "dsh"))
        // 只有 acp / dsh 能在手机上直接发起：电脑端专属的连接器不可。
        assertFalse(busAgentCanStartTask(available = true, enabled = true, source = "codex"))
        assertFalse(busAgentCanStartTask(available = true, enabled = true, source = "claude"))
        assertFalse(busAgentCanStartTask(available = false, enabled = true, source = "acp"))
        assertFalse(busAgentCanStartTask(available = true, enabled = false, source = "acp"))
        assertFalse(busAgentCanStartTask(available = true, enabled = true, source = ""))
    }

    @Test
    fun agentModelExposesCanStartTaskAndMatchesPageRule() {
        val view = previewBusHomeView()
        assertEquals(
            view.agents.filter { it.available && it.enabled && (it.source == "acp" || it.source == "dsh") },
            view.agents.filter { it.canStartTask },
        )
        assertTrue(view.agents.first { it.key == "acp:claude-code" }.canStartTask)
        assertFalse(view.agents.first { it.key == "codex" }.canStartTask)
    }

    // ---------- 多选筛选 ----------

    @Test
    fun multiSelectFilterKeepsUnionAndEmptySetMeansAll() {
        val tasks = previewBusHomeView().tasks
        assertEquals(5, tasks.forAgents(emptySet()).size)
        // claude-code 一条（s1）+ dsh 两条（s3、s5）。
        assertEquals(3, tasks.forAgents(setOf("acp:claude-code", "dsh")).size)
        assertEquals(1, tasks.forAgents(setOf("codex")).size)
        // 未知 key 不报错，只是筛不出东西。
        assertEquals(0, tasks.forAgents(setOf("acp:ghost")).size)
    }

    @Test
    fun singleSelectPathStillWorksAndConvertsToSet() {
        val tasks = previewBusHomeView().tasks
        assertEquals(tasks.forAgent("codex"), tasks.forAgents(busAgentFilterSet("codex")))
        assertEquals(tasks, tasks.forAgent(""))
        assertEquals(emptySet<String>(), busAgentFilterSet(""))
        assertEquals(setOf("codex"), busAgentFilterSet("codex"))
    }

    @Test
    fun toggleAgentFilterAddsRemovesAndClears() {
        val all = setOf("acp:claude-code", "codex", "dsh", "acp:opencode")
        assertEquals(setOf("codex"), busToggleAgentFilter(emptySet(), "codex"))
        assertEquals(setOf("dsh"), busToggleAgentFilter(setOf("codex", "dsh"), "codex"))
        // 少选一个再点回来，回到全选。
        assertEquals(all, busToggleAgentFilter(all - "acp:claude-code", "acp:claude-code"))
        // 点「全部 Agent」= 清空筛选（空集合语义就是不过滤）。
        assertEquals(emptySet<String>(), busToggleAgentFilter(all, ""))
    }

    @Test
    fun projectGroupsSupportSetFilterAndMatchSingleSelect() {
        val view = previewBusHomeView()
        assertEquals(view.projectGroups(""), view.projectGroups(emptySet()))
        assertEquals(view.projectGroups("codex"), view.projectGroups(setOf("codex")))

        val two = view.projectGroups(setOf("acp:claude-code", "codex"))
        assertEquals(listOf("agentbus"), two.map { it.name })
        assertEquals(2, two.single().tasks.size)
        // fixture 共 5 条任务；claude-code 与 codex 各 1 条。
        assertEquals(5, view.visibleTaskCount(emptySet()))
        assertEquals(5, view.projectGroups("").sumOf { it.tasks.size })
        assertEquals(2, view.visibleTaskCount(setOf("acp:claude-code", "codex")))
    }

    // ---------- 审批投影 ----------

    @Test
    fun approvalItemsProjectPendingTasksWithRequestDetails() {
        val items = previewBusHomeView().approvalItems(emptySet())
        // 待处理优先 + 更新时间倒序：审批卡在前，等回答的在后。
        assertEquals(listOf("acp:claude-code:s1|toolu_01MergePr", "dsh:s3|ask-01"), items.map { it.key })

        val merge = items.first()
        assertEquals("acp:claude-code:s1", merge.taskId)
        assertEquals("toolu_01MergePr", merge.requestId)
        assertEquals("permission", merge.kind)
        assertEquals("合并前端打包优化 PR", merge.title)
        assertEquals("agentbus", merge.project)
        assertEquals("Claude Code", merge.agentName)
        assertEquals("10-29 08:29", merge.time)
        assertEquals("请求执行：git merge pr-42", merge.summary)
        assertEquals(1, merge.questions.size)
        assertFalse(merge.replyOnly)

        val ask = items.last()
        assertEquals("DeepSeek Harness", ask.agentName)
        assertTrue(ask.replyOnly)
        assertEquals("先看哪份日志？", ask.summary)
    }

    @Test
    fun approvalItemsFilterByAgentKeys() {
        val view = previewBusHomeView()
        assertEquals(1, view.approvalItems(setOf("acp:claude-code")).size)
        assertEquals(1, view.approvalItems(setOf("dsh")).size)
        assertEquals(2, view.approvalItems(setOf("acp:claude-code", "dsh")).size)
        // 只有非待处理任务的 Agent 筛不出审批卡。
        assertEquals(emptyList<BusApprovalItem>(), view.approvalItems(setOf("codex")))
    }

    @Test
    fun approvalItemsDeduplicateByTaskAndRequest() {
        val view = BusHomeView(
            device = null,
            agents = emptyList(),
            tasks = listOf(
                approvalTask(id = "dsh:a", requestId = "r1"),
                // 同一个任务同一条请求被多次投递：只出一条。
                approvalTask(id = "dsh:a", requestId = "r1", summary = "重复的一条"),
                approvalTask(id = "dsh:b", requestId = "r1"),
            ),
        )
        val items = view.approvalItems(emptySet())
        assertEquals(listOf("dsh:a|r1", "dsh:b|r1"), items.map { it.key })
        assertEquals("第一个到的一条", items.first().summary)
    }

    @Test
    fun approvalItemReplyOnlyCoversInputAndMissingKind() {
        assertTrue(approvalTask(id = "dsh:a", requestId = "r1", kind = "input").toApprovalItem().replyOnly)
        assertTrue(approvalTask(id = "dsh:a", requestId = "r1", kind = "").toApprovalItem().replyOnly)
        assertFalse(approvalTask(id = "dsh:a", requestId = "r1", kind = "command").toApprovalItem().replyOnly)
        assertFalse(approvalTask(id = "dsh:a", requestId = "r1", kind = "fileChange").toApprovalItem().replyOnly)
    }

    @Test
    fun approvalSummaryFallsBackToLastMessage() {
        val item = approvalTask(id = "dsh:a", requestId = "r1", summary = "").toApprovalItem()
        assertEquals("最后一条消息", item.summary)
    }

    private fun approvalTask(
        id: String,
        requestId: String,
        kind: String = "command",
        summary: String = "第一个到的一条",
    ): BusHomeTask = BusHomeTask(
        id = id,
        title = "任务 $id",
        status = "waitingApproval",
        statusLabel = "待审批",
        agent = "DSH",
        agentKey = "dsh",
        project = "agentbus",
        time = "10-29 08:29",
        taskType = "code",
        pending = true,
        lastMessage = "最后一条消息",
        requestId = requestId,
        kind = kind,
        summary = summary,
    )

    private fun BusHomeTask.toApprovalItem(): BusApprovalItem =
        BusHomeView(device = null, agents = emptyList(), tasks = listOf(this))
            .approvalItems(emptySet())
            .single()
}
