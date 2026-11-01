package com.example.dsh.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Agent 选择面板的判定层单测：标签映射、报错/停用行、展开态、能力矩阵与图标形状。
 *
 * 面板的渲染函数不在这里测（需要 Kuikly 运行环境），只测抽出来的纯函数，
 * 保证 D 号在页面里持有的状态语义与设计基线一致。
 */
class BusAgentSheetTest {

    // ---------- 状态标签 ----------

    @Test
    fun badgePrefersLaunchFailureOverStatus() {
        val failed = agent(lastError = "找不到 claude 可执行文件", status = "error")
        assertEquals(BusAgentBadge("启动失败", BusAgentBadgeKind.Error), busAgentRowBadge(failed))
    }

    @Test
    fun badgeMapsUnavailableDisabledAndDegraded() {
        assertEquals(BusAgentBadge("未安装", BusAgentBadgeKind.Muted), busAgentRowBadge(agent(available = false)))
        assertEquals(BusAgentBadge("已停用", BusAgentBadgeKind.Muted), busAgentRowBadge(agent(enabled = false)))
        assertEquals(BusAgentBadge("需确认", BusAgentBadgeKind.Pending), busAgentRowBadge(agent(status = "degraded")))
        assertEquals(BusAgentBadge("异常", BusAgentBadgeKind.Error), busAgentRowBadge(agent(status = "error")))
        // 健康连接器不给标签，行里只留名称与任务数。
        assertNull(busAgentRowBadge(agent()))
    }

    @Test
    fun badgeForFixtureAgentsIsAllEmpty() {
        // fixture 里四个连接器都是 available+enabled+status ok，且没有 lastError。
        previewBusHomeView().agents.forEach { assertNull(busAgentRowBadge(it)) }
    }

    // ---------- 行种类 ----------

    @Test
    fun errorRowCoversLastErrorTextAndErrorStatus() {
        assertTrue(busAgentRowIsError(agent(lastError = "spawn ENOENT")))
        assertTrue(busAgentRowIsError(agent(status = "error")))
        assertFalse(busAgentRowIsError(agent()))
    }

    @Test
    fun disabledRowIsNotCheckable() {
        assertTrue(busAgentRowIsDisabled(agent(enabled = false)))
        assertTrue(busAgentRowIsDisabled(agent(available = false)))
        assertFalse(busAgentRowIsDisabled(agent()))
        // 停用/未安装不可勾选；报错与需确认仍然可以勾选。
        assertFalse(busAgentRowIsCheckable(agent(enabled = false)))
        assertFalse(busAgentRowIsCheckable(agent(available = false)))
        assertTrue(busAgentRowIsCheckable(agent(lastError = "boom")))
        assertTrue(busAgentRowIsCheckable(agent(status = "degraded")))
    }

    // ---------- 展开态 ----------

    @Test
    fun onlyOneRowExpandsAndEmptyKeyExpandsNothing() {
        assertTrue(busAgentRowIsExpanded("codex", "codex"))
        assertFalse(busAgentRowIsExpanded("codex", "dsh"))
        assertFalse(busAgentRowIsExpanded("", ""))
        assertFalse(busAgentRowIsExpanded("", "codex"))
    }

    @Test
    fun toggleExpandedOpensSwitchesAndCloses() {
        assertEquals("codex", busToggleAgentExpanded("", "codex"))
        // 从一个 Agent 直接换到另一个：只保留最后点开的。
        assertEquals("dsh", busToggleAgentExpanded("codex", "dsh"))
        assertEquals("", busToggleAgentExpanded("codex", "codex"))
    }

    // ---------- 展开区能力矩阵 ----------

    @Test
    fun capabilityRowsFollowAgentAndDeviceCapabilities() {
        val rows = busAgentCapabilityRows(
            agent(
                taskCount = 2,
                lastError = "",
                models = listOf(model("gpt-5-codex"), model("codex-mini", display = "Codex Mini")),
                canDeleteTasks = true,
                canAutoApprove = true,
                source = "acp",
                connectorId = "codex",
            ),
            canRestartConnectors = true,
        )
        assertEquals(
            listOf("任务数", "可用模型", "可新建任务", "支持自动批准", "可删除会话", "重启连接器（电脑级）"),
            rows.map { it.label },
        )
        assertEquals("2", rows[0].text)
        assertEquals("gpt-5-codex · Codex Mini", rows[1].text)
        // 可新建任务看 canStartTask：acp + 可用 + 已启用。
        assertEquals(true, rows[2].supported)
        assertEquals(true, rows[3].supported)
        assertEquals(true, rows[4].supported)
        assertEquals(true, rows[5].supported)
    }

    @Test
    fun capabilityRowsMarkRestartUnsupportedWithoutFlag() {
        val rows = busAgentCapabilityRows(agent(source = "acp", connectorId = "codex"), canRestartConnectors = false)
        // 前五项是 Agent 自己的能力，最后一项是电脑级能力。
        assertEquals(true, rows[2].supported)
        assertEquals(false, rows[5].supported)
        // 文本行的 supported 恒为 null，渲染时才决定画文字还是勾/横。
        assertNull(rows[0].supported)
        assertNull(rows[1].supported)
    }

    @Test
    fun modelSummaryJoinsNamesAndFallsBackToDash() {
        assertEquals("—", busAgentModelSummary(emptyList()))
        assertEquals("gpt-5-codex", busAgentModelSummary(listOf(model("gpt-5-codex"))))
        // displayName 缺失时回退 id，顺序保持连接器给的顺序。
        assertEquals(
            "Codex Mini · claude",
            busAgentModelSummary(listOf(model("codex-mini", display = "Codex Mini"), model("claude", display = ""))),
        )
    }

    @Test
    fun taskCountLabelAndRetryLabel() {
        assertEquals("0 个任务", busAgentTaskCountLabel(0))
        assertEquals("12 个任务", busAgentTaskCountLabel(12))
        assertEquals("重试启动", busAgentRetryLabel(isBusy = false))
        assertEquals("重启中…", busAgentRetryLabel(isBusy = true))
    }

    // ---------- 官方图标形状 ----------

    @Test
    fun photoAssetsCoverWhileLineAssetsContain() {
        assertTrue(busAgentIconIsPhoto(BusAgentVisuals.CODEX))
        assertTrue(busAgentIconIsPhoto(BusAgentVisuals.CLAUDE))
        assertTrue(busAgentIconIsPhoto(BusAgentVisuals.DEEPSEEK))
        assertFalse(busAgentIconIsPhoto(BusAgentVisuals.OPENCODE))
        assertFalse(busAgentIconIsPhoto(BusAgentVisuals.FALLBACK))
        // 大写扩展名也算照片类。
        assertTrue(busAgentIconIsPhoto("AGENT.PNG"))
    }

    private fun agent(
        key: String = "codex",
        name: String = "Codex",
        available: Boolean = true,
        enabled: Boolean = true,
        status: String = "ok",
        source: String = "codex",
        connectorId: String? = null,
        taskCount: Int = 0,
        lastError: String = "",
        models: List<BusHomeModelOption> = emptyList(),
        canDeleteTasks: Boolean = false,
        canAutoApprove: Boolean = false,
    ): BusHomeAgent = BusHomeAgent(
        key = key,
        name = name,
        available = available,
        enabled = enabled,
        status = status,
        source = source,
        connectorId = connectorId,
        taskCount = taskCount,
        lastError = lastError,
        models = models,
        canDeleteTasks = canDeleteTasks,
        canAutoApprove = canAutoApprove,
    )

    private fun model(id: String, display: String = id): BusHomeModelOption =
        BusHomeModelOption(id = id, displayName = display)
}
