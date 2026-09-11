package com.example.dsh.infrastructure

import com.example.dsh.diagnostics.DshLogQuery
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*
import kotlinx.coroutines.*

class DshLogConcurrencyTest {
    private class Store : DshLogStore {
        val rows = mutableListOf<LogEvent>()
        var entered: CountDownLatch? = null
        var release: CountDownLatch? = null
        override fun appendBatch(events: List<LogEvent>) {
            entered?.countDown()
            check(release?.await(5, TimeUnit.SECONDS) != false)
            rows.addAll(events)
        }
        override fun query(filter: LogFilter, limit: Int, offset: Int) = rows.filter {
            (filter.beforeSeq == null || it.seq < filter.beforeSeq) &&
                (filter.sessionId == null || it.sessionId == filter.sessionId)
        }.sortedByDescending { it.seq }.drop(offset).take(limit)
        override fun clear() = rows.clear()
        override fun maxSeq() = rows.maxOfOrNull { it.seq } ?: 0L
        override fun sizeBytes() = rows.sumOf { it.size.toLong() }
        override fun dropOldest(keepBytes: Long) = Unit
    }

    private fun event(seq: Long) = LogEvent(seq, seq, LogLevel.INFO, "test", "s1", "r1", "token=secret-$seq", 20)

    @Test fun clearCannotBeUndoneByAnInflightFlush() {
        val store = Store()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writer = DshLogWriteBehind(store, scope, flushDelayMs = 60000)
        writer.enqueue(event(0))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        store.entered = entered; store.release = release
        val flush = thread { writer.flush() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val cleared = CountDownLatch(1)
            val clear = thread { writer.clear(); cleared.countDown() }
            assertFalse(cleared.await(100, TimeUnit.MILLISECONDS))
            release.countDown()
            flush.join(5000); clear.join(5000)
            assertTrue(cleared.await(1, TimeUnit.SECONDS))
            assertTrue(writer.snapshot().isEmpty())
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun exportWritesEveryRetainedRecordInBoundedBatches() {
        val store = Store().apply { rows.addAll((1L..6200L).map(::event)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writer = DshLogWriteBehind(store, scope)
        val dir = Files.createTempDirectory("dsh-log-export-test").toFile()
        try {
            val path = DshLogQuery().export(writer, dir.path, "logs.txt", "test")
            val content = java.io.File(path).readText()
            assertEquals(6200, content.lineSequence().count { it.contains("rpcId=r1") })
            assertFalse(content.contains("secret-"))
            assertTrue(content.contains("seq=6200"))
            assertTrue(content.contains("seq=1 "))
        } finally { scope.cancel(); dir.deleteRecursively() }
    }
}
