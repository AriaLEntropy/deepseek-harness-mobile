package com.example.dsh.bus

import com.example.dsh.host.DshConnectionMode
import com.example.dsh.message.DshMessage
import com.example.dsh.session.DshLegacyRemoteProfile
import com.example.dsh.session.DshRelayProfile
import com.example.dsh.session.DshRemoteProfile
import com.example.dsh.session.DshSession
import com.example.dsh.storage.DshLocalStore
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

    /** 只记录 clear 行为的最小 DshLocalStore；其余接口不会在测试里被调用。 */
    private class RecordingStore : DshLocalStore {
        val busCache = mutableMapOf<String, String>()
        val clearedScopes = mutableListOf<String>()

        override fun loadApiKey(): String = ""
        override fun saveApiKey(apiKey: String) = Unit
        override fun loadLastConnectionMode(): DshConnectionMode = DshConnectionMode.RELAY
        override fun saveLastConnectionMode(mode: DshConnectionMode) = Unit
        override fun loadRemoteProfile(): DshRemoteProfile? = null
        override fun saveRemoteProfile(profile: DshRemoteProfile) = Unit
        override fun loadRelayProfile(): DshRelayProfile? = null
        override fun saveRelayProfile(profile: DshRelayProfile) = Unit
        override fun clearRelayProfile() = Unit
        override fun migrateLegacyRemoteProfile(profile: DshLegacyRemoteProfile): Boolean = false
        override fun loadSessions(scopeId: String): List<DshSession> = emptyList()
        override fun replaceSessions(scopeId: String, sessions: List<DshSession>) = Unit
        override fun loadMessages(scopeId: String, sessionId: String): List<DshMessage> = emptyList()
        override fun replaceMessages(
            scopeId: String,
            sessionId: String,
            messages: List<DshMessage>,
        ) = Unit
        override fun clearScope(scopeId: String) {
            clearedScopes += scopeId
        }
        override fun deleteSession(scopeId: String, sessionId: String) = Unit
        override fun loadBusCache(scopeId: String): String? = busCache[scopeId]
        override fun saveBusCache(scopeId: String, value: String) {
            busCache[scopeId] = value
        }
        override fun clearBusCache(scopeId: String) {
            busCache.remove(scopeId)
        }
    }

    private val snapshot =
        """{"agents":[],"tasks":[],"projects":[],"recentResults":[],"seq":9,"generatedAt":"2026-10-24T00:00:00Z"}"""

    private fun seqOf(entry: BusCacheEntry?): Long? =
        entry?.snapshot?.jsonObject?.get("seq")?.jsonPrimitive?.contentOrNull?.toLong()

    @Test
    fun savesAndLoadsPerHostScope() {
        val cache = MemoryCache()
        val bus = BusCache(cache, nowIso = { "2026-10-24T11:30:00Z" })
        bus.save("host-a", snapshot)

        assertEquals(setOf("bus:host-a"), cache.values.keys)
        val entry = bus.load("host-a")
        assertEquals("2026-10-24T11:30:00Z", entry?.savedAt)
        assertEquals(9L, seqOf(entry))
        assertNull(bus.load("host-b"))
    }

    @Test
    fun savingSameHostOverwritesBusScopeAndLeavesDshScopeAlone() {
        val cache = MemoryCache()
        cache.values["relay:host-a"] = "dsh-native"
        val bus = BusCache(cache, nowIso = { "t" })

        bus.save("host-a", snapshot)
        bus.save("host-a", snapshot.replace("\"seq\":9", "\"seq\":10"))

        assertEquals(10L, seqOf(bus.load("host-a")))
        assertEquals("dsh-native", cache.values["relay:host-a"])
        assertEquals(setOf("relay:host-a", "bus:host-a"), cache.values.keys)
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

        // 合法 JSON 但内部不是快照，同样回退为空。
        cache.values["bus:host-a"] = """{"savedAt":"t","snapshot":{"agents":"nope"}}"""
        assertNull(BusCache(cache, nowIso = { "t" }).load("host-a"))

        // 缺 savedAt 的旧格式也不渲染。
        cache.values["bus:host-a"] = """{"snapshot":$snapshot}"""
        assertNull(BusCache(cache, nowIso = { "t" }).load("host-a"))
    }

    @Test
    fun invalidSnapshotIsNotWritten() {
        val cache = MemoryCache()
        val bus = BusCache(cache, nowIso = { "t" })
        bus.save("host-a", """{"seq":1}""")
        bus.save("", snapshot)
        assertNull(cache.values["bus:host-a"])
        assertNull(cache.values["bus:"])
    }

    @Test
    fun removingComputerClearsBusAndDshRelayScopesOnly() {
        val store = RecordingStore()
        store.busCache["bus:host-a"] = "bus-a"
        store.busCache["bus:host-b"] = "bus-b"
        store.busCache["relay:host-b"] = "dsh-b"

        DshLocalBusSnapshotCache(store).clearHost("host-a")

        assertNull(store.busCache["bus:host-a"])
        assertEquals("bus-b", store.busCache["bus:host-b"])
        assertEquals("dsh-b", store.busCache["relay:host-b"])
        assertEquals(listOf("relay:host-a"), store.clearedScopes)
    }

    @Test
    fun hostScopesCoverBusAndDshRelayOnly() {
        assertEquals(listOf("bus:host-a", "relay:host-a"), BusCacheScope.hostScopes("host-a"))
    }
}
