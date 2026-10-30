package com.example.dsh.bus

import com.example.dsh.storage.DshLocalStore

/** Bridges the Bus cache contract to the platform DshLocalStore. */
class DshLocalBusSnapshotCache(private val store: DshLocalStore) : BusSnapshotCache {
    override fun load(scopeId: String): String? = store.loadBusCache(scopeId)

    override fun save(scopeId: String, value: String) = store.saveBusCache(scopeId, value)

    override fun clear(scopeId: String) = store.clearBusCache(scopeId)

    /**
     * Removing a computer must leave no Bus or DSH-native row for that host.
     * `relay:<hostId>` holds the old DSH sessions/messages; `bus:<hostId>` the
     * protocol snapshot. Other hosts and SSH profiles are untouched.
     */
    fun clearHost(hostId: String) {
        if (hostId.isBlank()) return
        store.clearBusCache(BusCacheScope.bus(hostId))
        store.clearScope(BusCacheScope.relay(hostId))
    }
}
