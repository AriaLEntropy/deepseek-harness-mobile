package com.example.dsh.infrastructure

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DshHttpClientTest {
    @Test
    fun webSocketUrlRewritesHttpSchemes() {
        assertEquals("ws://127.0.0.1:3080/api/events.mux", dshWebSocketUrl("http://127.0.0.1:3080/api/events.mux"))
        assertEquals("wss://host/events", dshWebSocketUrl("https://host/events"))
        assertEquals("ws://already", dshWebSocketUrl("ws://already"))
    }
}
