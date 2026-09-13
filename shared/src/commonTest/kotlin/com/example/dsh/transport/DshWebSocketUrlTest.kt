package com.example.dsh.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import com.example.dsh.transport.dshWebSocketUrl

class DshWebSocketUrlTest {

    @Test
    fun upgradesHttpToWs() {
        assertEquals("ws://127.0.0.1:3080/events", dshWebSocketUrl("http://127.0.0.1:3080/events"))
    }

    @Test
    fun upgradesHttpsToWss() {
        assertEquals("wss://host/events", dshWebSocketUrl("https://host/events"))
    }

    @Test
    fun leavesWsAndUnknownSchemesUntouched() {
        assertEquals("ws://host/events", dshWebSocketUrl("ws://host/events"))
        assertEquals("127.0.0.1:3080", dshWebSocketUrl("127.0.0.1:3080"))
    }
}
