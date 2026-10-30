package com.example.dsh.bus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BusTaskActionsTest {
    @Test
    fun onlyStoppedTasksCanBeDeleted() {
        listOf("completed", "failed", "interrupted", "idle").forEach { status ->
            assertTrue(busTaskIsStopped(status), status)
            assertTrue(busCanDeleteTask(status, canDeleteTasks = true), status)
        }
        listOf("running", "waitingApproval", "waitingInput").forEach { status ->
            assertFalse(busTaskIsStopped(status), status)
            assertFalse(busCanDeleteTask(status, canDeleteTasks = true), status)
        }
        assertFalse(busCanDeleteTask("completed", canDeleteTasks = false))
    }

    @Test
    fun confirmationCopyMatchesFr11() {
        assertEquals("永久删除这个会话？", BUS_DELETE_TASK_CONFIRM_TITLE)
        assertEquals(
            "删除后无法恢复；Codex 会同时删除派生的子会话；不会删除项目文件。",
            BUS_DELETE_TASK_CONFIRM_MESSAGE,
        )
        assertEquals("从列表移除项目？", BUS_REMOVE_PROJECT_CONFIRM_TITLE)
        assertEquals(
            "只是从列表里隐藏，不删除目录、代码和会话记录；之后有新活动时会重新出现。",
            BUS_REMOVE_PROJECT_CONFIRM_MESSAGE,
        )
    }

    @Test
    fun commandResultDrivesSuccessAndFailureStates() {
        val success = """
            {"recentResults":[{"commandId":"cmd-1","ok":true,"taskId":"codex:s1","finishedAt":"2026-10-24T00:03:00Z"}]}
        """.trimIndent()
        val ok = busCommandOutcome(success, "cmd-1")
        assertIs<BusCommandOutcome.Success>(ok)
        assertEquals("codex:s1", ok.taskId)

        val failure = """
            {"recentResults":[{"commandId":"cmd-2","ok":false,"error":"会话不存在","finishedAt":"2026-10-24T00:03:00Z"}]}
        """.trimIndent()
        val failed = busCommandOutcome(failure, "cmd-2")
        assertIs<BusCommandOutcome.Failure>(failed)
        assertEquals("会话不存在", failed.error)

        val blankError = """
            {"recentResults":[{"commandId":"cmd-3","ok":false,"finishedAt":"2026-10-24T00:03:00Z"}]}
        """.trimIndent()
        val fallback = busCommandOutcome(blankError, "cmd-3")
        assertIs<BusCommandOutcome.Failure>(fallback)
        assertEquals("操作失败", fallback.error)
    }

    @Test
    fun pendingOrMissingResultsKeepWaiting() {
        val pending = """
            {"recentResults":[{"commandId":"cmd-4","ok":false,"error":"pending","finishedAt":"2026-10-24T00:03:00Z"}]}
        """.trimIndent()
        assertIs<BusCommandOutcome.Pending>(busCommandOutcome(pending, "cmd-4"))

        val otherCommand = """
            {"recentResults":[{"commandId":"cmd-5","ok":true,"finishedAt":"2026-10-24T00:03:00Z"}]}
        """.trimIndent()
        assertIs<BusCommandOutcome.Pending>(busCommandOutcome(otherCommand, "cmd-6"))
        assertIs<BusCommandOutcome.Pending>(busCommandOutcome("not-json", "cmd-6"))
    }
}
