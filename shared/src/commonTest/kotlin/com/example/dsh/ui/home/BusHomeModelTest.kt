package com.example.dsh.ui.home

import com.example.dsh.bus.BusStore
import com.example.dsh.bus.busParse
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
