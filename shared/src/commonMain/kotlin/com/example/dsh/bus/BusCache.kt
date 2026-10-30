package com.example.dsh.bus

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * Cache scope ids. DSH native keeps its old prefixes; Bus uses its own.
 * `bus:<hostId>` never shares a row with `relay:<hostId>` / `ssh:<profile>`.
 */
object BusCacheScope {
    fun bus(hostId: String): String = "bus:$hostId"
    fun relay(hostId: String): String = "relay:$hostId"
    fun ssh(profile: String): String = "ssh:$profile"

    /** Scopes owned by one Relay-paired computer and cleared together on removal. */
    fun hostScopes(hostId: String): List<String> = listOf(bus(hostId), relay(hostId))
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
        if (hostId.isBlank()) return
        // Reject malformed or non-snapshot payloads instead of poisoning the scope.
        val snapshot = runCatching { busParse(snapshotJson) }.getOrNull() ?: return
        if (busCleanSnapshot(snapshot) == null) return
        val payload = buildJsonObject {
            put("savedAt", JsonPrimitive(nowIso()))
            put("snapshot", snapshot)
        }
        cache.save(BusCacheScope.bus(hostId), payload.toString())
    }

    fun load(hostId: String): BusCacheEntry? {
        if (hostId.isBlank()) return null
        val raw = cache.load(BusCacheScope.bus(hostId)) ?: return null
        val root = runCatching { busParse(raw) as? JsonObject }.getOrNull() ?: return null
        val savedAt = (root["savedAt"] as? JsonPrimitive)?.contentOrNull ?: return null
        // A truncated/foreign payload must not surface as a renderable snapshot.
        val snapshot = root["snapshot"] as? JsonObject ?: return null
        if (busCleanSnapshot(snapshot) == null) return null
        return BusCacheEntry(snapshot, savedAt)
    }

    fun clear(hostId: String) {
        if (hostId.isBlank()) return
        cache.clear(BusCacheScope.bus(hostId))
    }
}
