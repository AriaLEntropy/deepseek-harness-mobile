package com.example.dsh.bus

import com.example.dsh.storage.DshLocalStore

/** Bridges the Bus cache contract to the platform DshLocalStore. */
class DshLocalBusSnapshotCache(private val store: DshLocalStore) : BusSnapshotCache {
    override fun load(scopeId: String): String? = store.loadBusCache(scopeId)

    override fun save(scopeId: String, value: String) = store.saveBusCache(scopeId, value)

    override fun clear(scopeId: String) = store.clearBusCache(scopeId)
}
