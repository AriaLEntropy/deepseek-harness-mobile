package com.example.dsh.infrastructure

/** JS target has no SQLite storage; log persistence is Android/iOS/OHOS only. */
internal actual fun createDshLogStore(path: String): DshLogStore = EmptyDshLogStore

private object EmptyDshLogStore : DshLogStore {
    override fun appendBatch(events: List<LogEvent>) = Unit
    override fun query(filter: LogFilter, limit: Int, offset: Int): List<LogEvent> = emptyList()
    override fun clear() = Unit
    override fun maxSeq(): Long = 0L
    override fun diskBytes(): Long = 0L
    override fun count(): Long = 0L
    override fun countLowLevel(): Long = 0L
    override fun deleteOldest(limit: Int, lowLevelOnly: Boolean): Int = 0
    override fun compact() = Unit
}
