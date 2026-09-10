package com.example.dsh.infrastructure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 内存版 DshLogStore，用于驱动 DshLogWriteBehind 的容量与生命周期测试。 */
private class FakeLogStore : DshLogStore {
    val events = mutableListOf<LogEvent>()
    var storedBytes = 0L
    var dropOldestCalls = 0
    var clearCalls = 0
    var maxSeqValue = 0L

    override fun appendBatch(events: List<LogEvent>) {
        this.events.addAll(events)
        storedBytes += events.sumOf { it.size }
    }

    override fun query(filter: LogFilter, limit: Int, offset: Int): List<LogEvent> =
        events.sortedByDescending { it.seq }.take(limit).drop(offset)

    override fun clear() {
        events.clear()
        storedBytes = 0
        clearCalls++
    }

    override fun sizeBytes(): Long = storedBytes

    override fun dropOldest(keepBytes: Long) {
        dropOldestCalls++
    }

    override fun maxSeq(): Long = maxSeqValue
}

private fun logEvent(
    seq: Long,
    level: LogLevel = LogLevel.INFO,
    message: String = "msg-$seq",
    size: Int = 1,
): LogEvent = LogEvent(
    seq = seq,
    timestamp = seq,
    level = level,
    type = "test",
    sessionId = "s1",
    rpcId = null,
    message = message,
    size = size,
)

private class WriteBehindHarness(
    store: FakeLogStore = FakeLogStore(),
    maxEntries: Int = 5000,
    maxBytes: Int = 5 * 1024 * 1024,
    maxStorageBytes: Long = 50L * 1024 * 1024,
    flushDelayMs: Long = 10_000,
    batchFlushSize: Int = 32,
    snapshotLimit: Int = 20000,
) {
    val store = store
    private val scope = CoroutineScope(Dispatchers.Default)
    val writeBehind = DshLogWriteBehind(
        logStore = store,
        scope = scope,
        maxEntries = maxEntries,
        maxBytes = maxBytes,
        maxStorageBytes = maxStorageBytes,
        flushDelayMs = flushDelayMs,
        batchFlushSize = batchFlushSize,
        snapshotLimit = snapshotLimit,
    )

    fun close() = writeBehind.onStop()
}

class DshLogWriteBehindTest {

    @Test
    fun seqContinuesAfterStoreMax() {
        val h = WriteBehindHarness(store = FakeLogStore().apply { maxSeqValue = 7 })
        try {
            val e1 = h.writeBehind.enqueue(logEvent(seq = 0))
            val e2 = h.writeBehind.enqueue(logEvent(seq = 0))
            assertEquals(8L, e1.seq)
            assertEquals(9L, e2.seq)
        } finally {
            h.close()
        }
    }

    @Test
    fun overMaxEntriesDropsLowestLevelFirst() {
        val h = WriteBehindHarness(maxEntries = 3)
        try {
            h.writeBehind.enqueue(logEvent(seq = 0, level = LogLevel.ERROR))
            h.writeBehind.enqueue(logEvent(seq = 0, level = LogLevel.ERROR))
            h.writeBehind.enqueue(logEvent(seq = 0, level = LogLevel.DEBUG))
            h.writeBehind.enqueue(logEvent(seq = 0, level = LogLevel.DEBUG))
            h.writeBehind.enqueue(logEvent(seq = 0, level = LogLevel.DEBUG))
            assertEquals(2L, h.writeBehind.droppedCount())
            assertEquals(3, h.writeBehind.snapshot().size)
            val levels = h.writeBehind.snapshot().map { it.level }
            assertTrue(levels.count { it == LogLevel.ERROR } == 2)
            assertTrue(levels.count { it == LogLevel.DEBUG } == 1)
        } finally {
            h.close()
        }
    }

    @Test
    fun overMaxBytesDropsInfoThenDebug() {
        val h = WriteBehindHarness(maxBytes = 100, maxEntries = 100)
        try {
            h.writeBehind.enqueue(logEvent(seq = 0, level = LogLevel.ERROR, size = 60))
            h.writeBehind.enqueue(logEvent(seq = 0, level = LogLevel.INFO, size = 60))
            assertEquals(1L, h.writeBehind.droppedCount())
            assertEquals(listOf(LogLevel.ERROR), h.writeBehind.snapshot().map { it.level })

            h.writeBehind.enqueue(logEvent(seq = 0, level = LogLevel.DEBUG, size = 60))
            assertEquals(2L, h.writeBehind.droppedCount())
            assertEquals(listOf(LogLevel.ERROR), h.writeBehind.snapshot().map { it.level })
        } finally {
            h.close()
        }
    }

    @Test
    fun flushPersistsBatchAndClearsPending() {
        val h = WriteBehindHarness()
        try {
            h.writeBehind.enqueue(logEvent(seq = 0, message = "a"))
            h.writeBehind.enqueue(logEvent(seq = 0, message = "b"))
            h.writeBehind.flush()
            assertEquals(listOf("a", "b"), h.store.events.map { it.message })
            assertEquals(2L, h.store.storedBytes)
            // flush 后 pending 已清空：snapshot 即库内内容
            assertEquals(listOf("a", "b"), h.writeBehind.snapshot().map { it.message })
        } finally {
            h.close()
        }
    }

    @Test
    fun snapshotMergesPendingAndStoreSortedBySeq() {
        val store = FakeLogStore().apply {
            maxSeqValue = 3
            appendBatch(listOf(logEvent(seq = 1), logEvent(seq = 3)))
        }
        val h = WriteBehindHarness(store = store)
        try {
            val e4 = h.writeBehind.enqueue(logEvent(seq = 0))
            h.writeBehind.flush()
            val e5 = h.writeBehind.enqueue(logEvent(seq = 0))
            assertEquals(4L, e4.seq)
            assertEquals(5L, e5.seq)
            assertEquals(listOf(1L, 3L, 4L, 5L), h.writeBehind.snapshot().map { it.seq })
        } finally {
            h.close()
        }
    }

    @Test
    fun snapshotRespectsSnapshotLimit() {
        val store = FakeLogStore().apply {
            appendBatch((1L..6L).map { logEvent(seq = it) })
        }
        val h = WriteBehindHarness(store = store, snapshotLimit = 3)
        try {
            assertEquals(3, h.writeBehind.snapshot().size)
        } finally {
            h.close()
        }
    }

    @Test
    fun clearEmptiesStoreAndPending() {
        val h = WriteBehindHarness()
        try {
            h.writeBehind.enqueue(logEvent(seq = 0))
            h.writeBehind.flush()
            h.writeBehind.enqueue(logEvent(seq = 0))
            h.writeBehind.clear()
            assertEquals(1, h.store.clearCalls)
            assertTrue(h.store.events.isEmpty())
            assertTrue(h.writeBehind.snapshot().isEmpty())
        } finally {
            h.close()
        }
    }

    @Test
    fun onStartTrimsStorageOnlyWhenOverLimit() {
        val over = FakeLogStore().apply { storedBytes = 60L * 1024 * 1024 }
        val h1 = WriteBehindHarness(store = over)
        try {
            h1.writeBehind.onStart()
            assertEquals(1, h1.store.dropOldestCalls)
            assertEquals(1L, h1.writeBehind.storageTrimCount())
        } finally {
            h1.close()
        }

        val under = FakeLogStore().apply { storedBytes = 10L * 1024 * 1024 }
        val h2 = WriteBehindHarness(store = under)
        try {
            h2.writeBehind.onStart()
            assertEquals(0, h2.store.dropOldestCalls)
            assertEquals(0L, h2.writeBehind.storageTrimCount())
        } finally {
            h2.close()
        }
    }

    @Test
    fun flushTrimsStorageWhenAppendExceedsLimit() {
        // 单条 60MB：超过 maxStorageBytes(50MB)，但需低于 maxBytes 才不会在内存层被丢弃
        val h = WriteBehindHarness(maxBytes = 200 * 1024 * 1024)
        try {
            h.writeBehind.enqueue(logEvent(seq = 0, size = 60 * 1024 * 1024))
            h.writeBehind.flush()
            assertEquals(1, h.store.dropOldestCalls)
            assertEquals(1L, h.writeBehind.storageTrimCount())
        } finally {
            h.close()
        }
    }
}

class LogSanitizerTest {

    @Test
    fun redactsQueryToken() {
        val out = LogSanitizer.sanitize("GET /x?token=abc123&a=1")
        assertTrue(out.contains("token=***"))
        assertFalse(out.contains("abc123"))
    }

    @Test
    fun redactsBearerAuthorization() {
        val out = LogSanitizer.sanitize("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9")
        assertTrue(out.contains("Authorization: Bearer ***"))
        assertFalse(out.contains("eyJhbGciOiJIUzI1NiJ9"))
    }

    @Test
    fun redactsAccessTicket() {
        val out = LogSanitizer.sanitize("ticket=1&access-ticket=t123&b=2")
        assertTrue(out.contains("access-ticket=***"))
        assertFalse(out.contains("t123"))
    }

    @Test
    fun redactsLongBase64Blob() {
        // base64 正则按连续 [A-Za-z0-9+/=] 计数：前缀 "payload=" 也计入长度
        val blob = "A".repeat(80)
        val out = LogSanitizer.sanitize("payload=$blob")
        assertTrue(out.contains("[base64:"), "unexpected: $out")
        assertFalse(out.contains("A".repeat(80)))
    }

    @Test
    fun keepsShortBase64LikeText() {
        val short = "abc123+/"
        assertEquals("payload=$short", LogSanitizer.sanitize("payload=$short"))
    }

    @Test
    fun redactsPasswordAndApiKeyQuery() {
        val out = LogSanitizer.sanitize("password=secret&apiKey=ak123")
        assertTrue(out.contains("password=***"))
        assertTrue(out.contains("apiKey=***"))
        assertFalse(out.contains("secret"))
        assertFalse(out.contains("ak123"))
    }

    @Test
    fun redactsGenericUrlQuerySecretsKeepingKeyName() {
        val out = LogSanitizer.sanitize("?signature=abc123&x=1")
        assertTrue(out.contains("?signature=***"))
        assertTrue(out.contains("x=1"))
        assertFalse(out.contains("abc123"))
    }

    @Test
    fun redactsJsonSecretValues() {
        val out = LogSanitizer.sanitize("""{"clientToken":"tok-9","user":"ok","hostToken":"ht"}""")
        assertTrue(out.contains(""""clientToken":"***""""))
        assertTrue(out.contains(""""hostToken":"***""""))
        assertTrue(out.contains(""""user":"ok""""))
        assertFalse(out.contains("tok-9"))
        assertFalse(out.contains("\"ht\""))
    }

    @Test
    fun emptyInputPassesThrough() {
        assertEquals("", LogSanitizer.sanitize(""))
    }

    @Test
    fun plainTextUntouched() {
        assertEquals("hello world", LogSanitizer.sanitize("hello world"))
    }
}

class DshLogSelectTest {

    @Test
    fun emptyFilterSelectsAllOrderedDesc() {
        val s = buildLogSelect(LogFilter(), limit = 100, offset = 0)
        assertFalse(s.sql.contains("WHERE"))
        assertTrue(s.sql.contains("ORDER BY seq DESC"))
        assertEquals(listOf("100", "0"), s.args)
    }

    @Test
    fun exactTypeBecomesEquals() {
        val s = buildLogSelect(LogFilter(types = listOf("chat.message")), limit = 10, offset = 0)
        assertTrue(s.sql.contains("type = ?"))
        assertEquals("chat.message", s.args[0])
    }

    @Test
    fun wildcardTypeBecomesLike() {
        val s = buildLogSelect(LogFilter(types = listOf("chat.*")), limit = 10, offset = 0)
        assertTrue(s.sql.contains("type LIKE ?"))
        assertEquals("chat%", s.args[0])
    }

    @Test
    fun multipleTypesAreOrJoined() {
        val s = buildLogSelect(LogFilter(types = listOf("a.b", "c.*")), limit = 10, offset = 0)
        assertTrue(s.sql.contains("type = ? OR type LIKE ?"))
        assertEquals(listOf("a.b", "c%"), s.args.take(2))
    }

    @Test
    fun levelsBecomeInClause() {
        val s = buildLogSelect(LogFilter(levels = listOf(LogLevel.DEBUG, LogLevel.ERROR)), limit = 10, offset = 0)
        assertTrue(s.sql.contains("level IN (?,?)"))
        assertEquals(listOf("0", "3"), s.args.take(2))
    }

    @Test
    fun textIsWrappedWithPercent() {
        val s = buildLogSelect(LogFilter(text = "err"), limit = 10, offset = 0)
        assertTrue(s.sql.contains("message LIKE ?"))
        assertEquals("%err%", s.args[0])
    }

    @Test
    fun allFiltersAndCombinedWithOrderedArgs() {
        val filter = LogFilter(
            types = listOf("chat.*"),
            levels = listOf(LogLevel.WARN),
            sessionId = "s9",
            rpcId = "r1",
            text = "boom",
            fromTime = 100L,
            toTime = 200L,
        )
        val s = buildLogSelect(filter, limit = 7, offset = 3)
        val clauses = listOf("type LIKE ?", "level IN (?)", "session_id = ?", "rpc_id = ?", "message LIKE ?", "time >= ?", "time <= ?")
        for (c in clauses) assertTrue(s.sql.contains(c), "missing $c in ${s.sql}")
        assertTrue(s.sql.split(" WHERE ")[1].split(" ORDER BY ")[0].split(" AND ").size == 7)
        assertEquals(listOf("chat%", "2", "s9", "r1", "%boom%", "100", "200", "7", "3"), s.args)
    }

    @Test
    fun limitOffsetAlwaysTrailing() {
        val s = buildLogSelect(LogFilter(sessionId = "s1"), limit = 5, offset = 20)
        assertEquals(listOf("s1", "5", "20"), s.args)
    }
}
