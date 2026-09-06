package com.example.dsh.infrastructure

/** JS target has no SQLite storage; log persistence is Android/iOS/OHOS only. */
internal actual fun createDshLogStore(path: String): DshLogStore = EmptyDshLogStore

private object EmptyDshLogStore : DshLogStore {
    override fun appendBatch(events: List<LogEvent>) = Unit
    override fun query(filter: LogFilter, limit: Int, offset: Int): List<LogEvent> = emptyList()
    override fun clear() = Unit
    override fun sizeBytes(): Long = 0L
    override fun dropOldest(keepBytes: Long) = Unit
}