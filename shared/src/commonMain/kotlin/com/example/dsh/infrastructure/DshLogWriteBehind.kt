package com.example.dsh.infrastructure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 异步写后缓存：日志先进入内存队列，批量刷入 SQLite。
 *
 * 生命周期：
 * - [onStart]  在页面 created 时调用，检查数据库容量并执行启动清理
 * - [enqueue]  运行时持续调用，日志进入内存队列
 * - [flush]    批量刷入数据库（自动触发或手动调用）
 * - [onStop]   页面销毁时调用，强制 flush 并取消待执行的刷写任务
 *
 * 容量策略（双层）：
 * 1. 内存队列：maxEntries / maxBytes，超限时优先丢弃 DEBUG→INFO 级别的旧日志
 * 2. 数据库：maxStorageBytes，每次 flush 后检查，超限时淘汰最旧日志
 */
internal class DshLogWriteBehind(
    private val logStore: DshLogStore,
    private val scope: CoroutineScope,
    private val maxEntries: Int = 5000,
    private val maxBytes: Int = 5 * 1024 * 1024,
    private val maxStorageBytes: Long = 50L * 1024 * 1024,
    private val flushDelayMs: Long = 500,
    private val batchFlushSize: Int = 32,
) {
    private val pending = mutableListOf<LogEvent>()
    private var nextSeq = 1L
    private var flushJob: Job? = null
    private var totalBytes = 0L
    private var droppedCount = 0L
    private var storageTrimCount = 0L
    private var started = false

    /** 启动时调用：检查数据库容量，超限则立即清理。 */
    fun onStart() {
        if (started) return
        started = true
        enforceStorageLimit()
    }

    fun enqueue(event: LogEvent): LogEvent {
        val seqd = event.copy(seq = nextSeq++)
        pending.add(seqd)
        totalBytes += seqd.size
        enforceCapacity()
        if (pending.size >= batchFlushSize) {
            scheduleFlush(0)
        } else if (flushJob == null || flushJob?.isCompleted != false) {
            scheduleFlush(flushDelayMs)
        }
        return seqd
    }

    /** 同步刷写所有待写日志到数据库，并检查存储容量。 */
    fun flush() {
        if (pending.isEmpty()) {
            enforceStorageLimit()
            return
        }
        val batch = pending.toList()
        pending.clear()
        totalBytes = 0
        logStore.appendBatch(batch)
        enforceStorageLimit()
    }

    /** 停止时调用：取消待执行刷写任务，强制同步 flush。 */
    fun onStop() {
        flushJob?.cancel()
        flushJob = null
        flush()
    }

    fun snapshot(): List<LogEvent> {
        val storeEvents = logStore.query(LogFilter(), limit = maxEntries, offset = 0)
        return (pending + storeEvents).sortedBy { it.seq }
    }

    fun clear() {
        pending.clear()
        totalBytes = 0
        flushJob?.cancel()
        flushJob = null
        logStore.clear()
    }

    fun droppedCount(): Long = droppedCount

    fun storageTrimCount(): Long = storageTrimCount

    private fun enforceCapacity() {
        if (pending.size <= maxEntries && totalBytes <= maxBytes) return
        // Drop oldest entries, prefer low-level, until under both limits
        while (pending.isNotEmpty() && (pending.size > maxEntries || totalBytes > maxBytes)) {
            // Find the oldest low-level event (DEBUG first, then INFO)
            val dropIdx = pending.indexOfFirst { it.level == LogLevel.DEBUG }
                .takeIf { it >= 0 }
                ?: pending.indexOfFirst { it.level == LogLevel.INFO }
                ?.takeIf { it >= 0 }
                ?: 0
            val removed = pending.removeAt(dropIdx)
            totalBytes -= removed.size
            droppedCount++
        }
    }

    /** 检查数据库容量，超过 maxStorageBytes 时淘汰最旧日志。 */
    private fun enforceStorageLimit() {
        val current = logStore.sizeBytes()
        if (current <= maxStorageBytes) return
        logStore.dropOldest(maxStorageBytes / 2)
        storageTrimCount++
    }

    private fun scheduleFlush(delayMs: Long) {
        flushJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            flush()
        }
    }
}
