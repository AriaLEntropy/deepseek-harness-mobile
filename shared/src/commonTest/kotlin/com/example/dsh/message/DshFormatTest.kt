package com.example.dsh.message

import kotlin.test.Test
import kotlin.test.assertEquals
import com.example.dsh.message.dshFormatTurnDuration

class DshFormatTest {

    @Test
    fun turnDurationFormatsSecondsAndMinutes() {
        assertEquals("0秒", dshFormatTurnDuration(0L))
        assertEquals("0秒", dshFormatTurnDuration(500L))
        assertEquals("0秒", dshFormatTurnDuration(-5L))
        assertEquals("1秒", dshFormatTurnDuration(1_000L))
        assertEquals("59秒", dshFormatTurnDuration(59_000L))
        assertEquals("1分00秒", dshFormatTurnDuration(60_000L))
        assertEquals("1分30秒", dshFormatTurnDuration(90_000L))
        assertEquals("60分00秒", dshFormatTurnDuration(3_600_000L))
    }
}
