package com.example.dsh.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import com.example.dsh.ui.interaction.dshPermissionIcon
import com.example.dsh.ui.search.dshRelativeTimeLabel

class DshHomeFormattingTest {

    @Test
    fun relativeTimeLabelBucketsMatchDesign() {
        val now = 1_000_000_000_000L
        assertEquals("", dshRelativeTimeLabel(0L, now))
        assertEquals("刚刚", dshRelativeTimeLabel(now - 1_000L, now))
        assertEquals("2分钟", dshRelativeTimeLabel(now - 120_000L, now))
        assertEquals("2小时", dshRelativeTimeLabel(now - 7_200_000L, now))
        assertEquals("2天", dshRelativeTimeLabel(now - 2L * 86_400_000L, now))
        assertEquals("2个月", dshRelativeTimeLabel(now - 60L * 86_400_000L, now))
        assertEquals("1年", dshRelativeTimeLabel(now - 400L * 86_400_000L, now))
        assertEquals("刚刚", dshRelativeTimeLabel(now + 1_000L, now))
    }

    @Test
    fun permissionIconFollowsSemantics() {
        assertEquals("permission-read.svg", dshPermissionIcon("read-only"))
        assertEquals("permission-danger.svg", dshPermissionIcon("danger-full-access"))
        assertEquals("permission-danger.svg", dshPermissionIcon("full-access"))
        assertEquals("permission-write.svg", dshPermissionIcon("workspace-write"))
        assertEquals("permission-write.svg", dshPermissionIcon("unknown"))
    }
}
