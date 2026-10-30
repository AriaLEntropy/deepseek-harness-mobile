package com.example.dsh.bus

/**
 * Tracks in-flight command ids for timeout/retry. Retries reuse the same id so
 * the host dedups them instead of executing twice.
 */
class BusCommandTracker(
    private val nowMs: () -> Long,
    private val timeoutMs: Long = 60_000,
) {
    private val startedAt = mutableMapOf<String, Long>()

    fun track(commandId: String) {
        startedAt[commandId] = nowMs()
    }

    fun onResult(commandId: String) {
        startedAt.remove(commandId)
    }

    /** True while the id is in flight; used by tests and by the sender to prove cleanup. */
    fun isTracking(commandId: String): Boolean = startedAt.containsKey(commandId)

    fun isExpired(commandId: String): Boolean {
        val started = startedAt[commandId] ?: return false
        return nowMs() - started >= timeoutMs
    }

    fun expired(): List<String> =
        startedAt.filterValues { nowMs() - it >= timeoutMs }.keys.toList()

    fun retry(commandId: String) {
        track(commandId)
    }
}
