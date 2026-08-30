package com.example.dsh.conversation

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

class DshSessionScopeTest {
    @Test
    fun storageKeysIsolateLocalRelayAndSshCaches() {
        assertEquals("local", DshSessionScope(DshConnectionMode.LOCAL).storageKey)
        assertEquals("relay:host-1", DshSessionScope(DshConnectionMode.RELAY, "host-1").storageKey)
        assertEquals("ssh:default", DshSessionScope(DshConnectionMode.SSH).storageKey)
        assertEquals(
            "ssh:office",
            DshSessionScope(DshConnectionMode.SSH, "office").storageKey,
        )
    }
}
