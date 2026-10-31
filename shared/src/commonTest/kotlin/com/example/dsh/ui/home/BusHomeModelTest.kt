package com.example.dsh.ui.home

import com.example.dsh.bus.BusStore
import com.example.dsh.bus.busParse
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BusHomeModelTest {
    @Test
    fun fixtureMapsDeviceCountsAndRows() {
        val view = previewBusHomeView()
        val device = view.device ?: error("fixture 应有一台电脑")

        assertEquals("Mac mini", device.name)
        assertEquals("macos", device.platform)
        assertEquals("platform-apple.svg", device.platformAsset)
        assertTrue(device.online)

        assertEquals(2, view.pendingCount)
        assertEquals(1, view.runningCount)
        assertEquals(5, view.tasks.size)
        assertEquals(4, view.agents.size)
        assertEquals("Claude Code", view.agents.first { it.key == "acp:claude-code" }.name)

        val first = view.tasks.first()
        assertEquals("合并前端打包优化 PR", first.title)
        assertEquals("waitingApproval", first.status)
        assertEquals("待审批", first.statusLabel)
        assertEquals("Claude Code", first.agent)
        assertEquals("acp:claude-code", first.agentKey)
        assertEquals("agentbus", first.project)
        assertEquals("code", first.taskType)
        assertTrue(first.pending)
    }

    @Test
    fun storeSnapshotJsonRoundTripsThroughBusStore() {
        val store = BusStore()
        store.applySnapshot(busParse(BUS_HOME_FIXTURE_JSON))
        val wire = store.snapshotJson()

        val second = BusStore()
        val receipt = second.applySnapshot(busParse(wire))
        assertNotNull(receipt)
        assertEquals(5, receipt.taskCount)

        val view = second.toHomeView()
        assertEquals("Mac mini", view.device?.name)
        assertEquals("platform-apple.svg", view.device?.platformAsset)
        assertEquals(2, view.pendingCount)
        assertEquals(1, view.runningCount)
        assertEquals(5, view.tasks.size)
    }

    @Test
    fun actionCapabilitiesMapAndSurviveSnapshotRoundTrip() {
        val view = previewBusHomeView()
        assertTrue(view.device?.canRemoveProjects == true)
        assertTrue(view.agents.first { it.key == "codex" }.canDeleteTasks)
        assertTrue(view.agents.first { it.key == "acp:claude-code" }.canDeleteTasks)
        assertFalse(view.agents.first { it.key == "dsh" }.canDeleteTasks)
        assertEquals(
            "/Users/me/Projects/agentbus",
            view.projectGroups("").first { it.name == "agentbus" }.path,
        )

        val store = BusStore()
        store.applySnapshot(busParse(BUS_HOME_FIXTURE_JSON))
        val second = BusStore()
        assertNotNull(second.applySnapshot(busParse(store.snapshotJson())))
        val roundTripped = second.toHomeView()
        assertTrue(roundTripped.device?.canRemoveProjects == true)
        assertTrue(roundTripped.agents.first { it.key == "codex" }.canDeleteTasks)
    }

    @Test
    fun placeholderProjectGroupsAreNotRemovable() {
        val snapshot = """
            {"agents":[{"agentId":"host-1","name":"pc","platform":"windows","online":true,"lastSeenAt":"","appVersion":"","connectors":[]}],
             "tasks":[{"id":"codex:s1","agentId":"host-1","source":"codex","title":"t","outsideProject":true,"projectName":"","status":"completed","origin":"watch","controllable":true,"startedAt":"","updatedAt":""}],
             "projects":[],"recentResults":[],"seq":1,"generatedAt":""}
        """.trimIndent()
        val store = BusStore()
        assertNotNull(store.applySnapshot(busParse(snapshot)))
        val groups = store.toHomeView().projectGroups("")
        assertEquals(1, groups.size)
        assertEquals("", groups.single().path)
    }

    @Test
    fun projectGroupsFollowProjectOrderAndAgentFilter() {
        val view = previewBusHomeView()

        val groups = view.projectGroups("")
        assertEquals(listOf("agentbus", "ui-kit", "notes"), groups.map { it.name })
        assertEquals(3, groups.first().tasks.size)

        val codex = view.projectGroups("codex")
        assertEquals(1, codex.size)
        assertEquals("agentbus", codex.single().name)
        assertEquals("重构 Bus 缓存层", codex.single().tasks.single().title)
    }

    @Test
    fun taskTypeFallsBackToGenericIcon() {
        assertEquals("task-code.svg", TaskIconRegistry.iconFor("code"))
        assertEquals("task-note.svg", TaskIconRegistry.iconFor("note"))
        assertEquals("session.svg", TaskIconRegistry.iconFor("unknown"))
        assertEquals("session.svg", TaskIconRegistry.iconFor(null))
    }

    @Test
    fun bodyStateUsesDisabledComputerCardInsteadOfEmptyArt() {
        // 没有电脑时即使没有任务，也不进入“没有任务”空态。
        assertEquals(BusHomeBodyState.DisabledComputer, busHomeBodyState(hasDevice = false, visibleTaskCount = 0))
        assertEquals(BusHomeBodyState.DisabledComputer, busHomeBodyState(hasDevice = false, visibleTaskCount = 5))
        assertEquals(BusHomeBodyState.NoTasks, busHomeBodyState(hasDevice = true, visibleTaskCount = 0))
        assertEquals(BusHomeBodyState.Content, busHomeBodyState(hasDevice = true, visibleTaskCount = 2))
    }

    @Test
    fun agentEmptyStateOnlyWhenListIsEmpty() {
        assertTrue(busHomeAgentsEmpty(emptyList()))
        assertFalse(busHomeAgentsEmpty(previewBusHomeView().agents))
    }

    @Test
    fun projectRowsFlattenHeadersAndRespectCollapse() {
        val groups = previewBusHomeView().projectGroups("")
        val rows = busHomeProjectRows(groups, collapsed = emptySet())
        assertEquals(groups.size + groups.sumOf { it.tasks.size }, rows.size)
        assertIs<BusHomeProjectRow.Header>(rows.first())
        assertEquals(rows.map { it.key }.distinct().size, rows.size)

        val collapsed = setOf(groups.first().key)
        val folded = busHomeProjectRows(groups, collapsed)
        assertEquals(
            rows.size - groups.first().tasks.size,
            folded.size,
        )
        assertTrue(folded.none { it is BusHomeProjectRow.Task && it.task in groups.first().tasks })
    }

    @Test
    fun fourHundredTasksMapAndGroupWithinBudget() {
        // M2-11 规模：400 个任务、20 个项目。只测纯模型转换，渲染由列表懒加载兜底。
        val tasks = (0 until 400).joinToString(",") { index ->
            val project = index % 20
            """{"id":"codex:s$index","agentId":"host-1","source":"codex","title":"task $index",
               "projectPath":"C:/work/p$project","projectName":"p$project","status":"completed",
               "origin":"watch","controllable":true,"startedAt":"2026-10-31T10:00:00Z",
               "updatedAt":"2026-10-31T${(10 + project % 10).toString().padStart(2, '0')}:00:00Z"}"""
        }
        val snapshot = """
            {"agents":[{"agentId":"host-1","name":"pc","platform":"windows","online":true,
             "lastSeenAt":"","appVersion":"","connectors":[]}],
             "tasks":[$tasks],"projects":[],"recentResults":[],"seq":1,"generatedAt":""}
        """.trimIndent()
        val store = BusStore()
        assertNotNull(store.applySnapshot(busParse(snapshot)))

        var groups = emptyList<BusHomeProjectGroup>()
        var rows = emptyList<BusHomeProjectRow>()
        val elapsed = measureTimeMillis {
            repeat(5) {
                val view = store.toHomeView()
                groups = view.projectGroups("")
                rows = busHomeProjectRows(groups, collapsed = emptySet())
            }
        }
        assertEquals(400, groups.sumOf { it.tasks.size })
        assertEquals(20, groups.size)
        assertEquals(400 + 20, rows.size)
        assertTrue(elapsed < 2_000, "400 tasks mapping/grouping took ${elapsed}ms")
    }
}
