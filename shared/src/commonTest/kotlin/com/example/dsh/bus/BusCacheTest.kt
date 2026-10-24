package com.example.dsh.bus

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BusCacheTest {
    private class MemoryCache : BusSnapshotCache {
        val values = mutableMapOf<String, String>()

        override fun load(scopeId: String): String? = values[scopeId]

        override fun save(scopeId: String, value: String) {
            values[scopeId] = value
        }

        override fun clear(scopeId: String) {
            values.remove(scopeId)
        }
    }

    private val snapshot =
        """{"agents":[],"tasks":[],"projects":[],"recentResults":[],"seq":9,"generatedAt":"2026-10-24T00:00:00Z"}"""

    @Test
    fun savesAndLoadsPerHostScope() {
        val cache = MemoryCache()
        val bus = BusCache(cache, nowIso = { "2026-10-24T11:30:00Z" })
        bus.save("host-a", snapshot)

        assertEquals(setOf("bus:host-a"), cache.values.keys)
        val entry = bus.load("host-a")
        assertEquals("2026-10-24T11:30:00Z", entry?.savedAt)
        assertEquals(
            9L,
            entry?.snapshot?.jsonObject?.get("seq")?.jsonPrimitive?.contentOrNull?.toLong(),
        )
        assertNull(bus.load("host-b"))
    }

    @Test
    fun clearingOneHostLeavesOthersAndDshScopes() {
        val cache = MemoryCache()
        cache.values["relay:host-a"] = "dsh"
        cache.values["ssh:profile"] = "ssh"
        val bus = BusCache(cache, nowIso = { "t" })
        bus.save("host-a", snapshot)
        bus.save("host-b", snapshot)

        bus.clear("host-a")

        assertNull(cache.values["bus:host-a"])
        assertNotNull(cache.values["bus:host-b"])
        assertEquals("dsh", cache.values["relay:host-a"])
        assertEquals("ssh", cache.values["ssh:profile"])
    }

    @Test
    fun corruptCacheReturnsNull() {
        val cache = MemoryCache()
        cache.values["bus:host-a"] = "not-json"
        assertNull(BusCache(cache, nowIso = { "t" }).load("host-a"))
    }
}
