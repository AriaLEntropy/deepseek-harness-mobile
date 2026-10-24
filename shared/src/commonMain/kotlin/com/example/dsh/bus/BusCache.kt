package com.example.dsh.bus

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** Cache scope ids. DSH native keeps its old prefixes; Bus uses its own. */
object BusCacheScope {
    fun bus(hostId: String): String = "bus:$hostId"
    fun relay(hostId: String): String = "relay:$hostId"
    fun ssh(profile: String): String = "ssh:$profile"
}

interface BusSnapshotCache {
    fun load(scopeId: String): String?
    fun save(scopeId: String, value: String)
    fun clear(scopeId: String)
}

data class BusCacheEntry(val snapshot: JsonElement, val savedAt: String)

/**
 * Stores the last accepted Bus snapshot per host. The platform layer only has
 * to provide a string key/value store; snapshot parsing stays here.
 */
class BusCache(
    private val cache: BusSnapshotCache,
    private val nowIso: () -> String,
) {
    fun save(hostId: String, snapshotJson: String) {
        val payload = buildJsonObject {
            put("savedAt", JsonPrimitive(nowIso()))
            put("snapshot", busParse(snapshotJson))
        }
        cache.save(BusCacheScope.bus(hostId), payload.toString())
    }

    fun load(hostId: String): BusCacheEntry? {
        val raw = cache.load(BusCacheScope.bus(hostId)) ?: return null
        val root = runCatching { busParse(raw) as? JsonObject }.getOrNull() ?: return null
        val savedAt = (root["savedAt"] as? JsonPrimitive)?.contentOrNull ?: return null
        val snapshot = root["snapshot"] ?: return null
        return BusCacheEntry(snapshot, savedAt)
    }

    fun clear(hostId: String) {
        cache.clear(BusCacheScope.bus(hostId))
    }
}
