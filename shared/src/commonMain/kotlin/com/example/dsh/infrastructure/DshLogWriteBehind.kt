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
 *
 * 取数上限：snapshot 查询数据库时使用 [snapshotLimit]，与内存队列上限 [maxEntries]
 * 解耦，可单独调大以便界面/导出看到更多历史，而不增加内存驻留压力。
 *
 * 并发模型：enqueue 来自任意调用线程（主线程为主），flush 在
 * `Dispatchers.Default` 协程执行，两者与 onStop/clear/snapshot 并发访问
 * 共享队列。所有共享状态（pending/totalBytes/nextSeq/flushJob 等）统一由
 * [lock] 保护；耗时 IO（appendBatch / 容量查询）放在临界区之外执行。
 */
internal class DshLogWriteBehind(
    private val logStore: DshLogStore,
    private val scope: CoroutineScope,
    private val maxEntries: Int = 5000,
    private val maxBytes: Int = 5 * 1024 * 1024,
    private val maxStorageBytes: Long = 50L * 1024 * 1024,
    private val flushDelayMs: Long = 500,
    private val batchFlushSize: Int = 32,
    private val snapshotLimit: Int = 20000,
    private val sanitize: (String) -> String = { LogSanitizer.sanitize(it) },
) {
    private val lock = DshLock()
    // Serialize DB operations and flush/clear boundaries; enqueue never waits on this after initialization.
    private val storeLock = DshLock()
    private var revision = 0L
    private val pending = mutableListOf<LogEvent>()

    /** 从库内已有最大 seq 之后开始编号；惰性初始化，避免在构造期执行阻塞 IO。 */
    private var nextSeq = 0L
    private var seqInitialized = false

    private var flushJob: Job? = null
    private var flushScheduled = false
    private var totalBytes = 0L
    private var droppedCount = 0L
    private var storageTrimCount = 0L
    private var started = false

    /** 启动时调用：初始化写入序号并检查数据库容量，超限则立即清理。 */
    fun onStart() = storeLock.withLock {
        val shouldStart = lock.withLock {
            if (started) {
                false
            } else {
                started = true
                true
            }
        }
        if (shouldStart) {
            val maxSeq = logStore.maxSeq()
            lock.withLock {
                if (!seqInitialized) {
                    nextSeq = maxSeq + 1
                    seqInitialized = true
                }
            }
            enforceStorageLimit()
        }
    }

    private fun ensureSeqInitialized() {
        if (lock.withLock { seqInitialized }) return
        storeLock.withLock {
            val maxSeq = logStore.maxSeq()
            lock.withLock {
                if (!seqInitialized) { nextSeq = maxSeq + 1; seqInitialized = true }
            }
        }
    }

    fun enqueue(event: LogEvent): LogEvent {
        ensureSeqInitialized()
        var scheduleDelay: Long? = null
        val seqd = lock.withLock {
            val e = event.copy(seq = nextSeq++)
            revision++
            pending.add(e)
            totalBytes += e.size
            enforceCapacity()
            scheduleDelay = if (pending.size >= batchFlushSize) {
                0L
            } else if (!flushScheduled) {
                flushScheduled = true
                flushDelayMs
            } else {
                null
            }
            e
        }
        scheduleDelay?.let { scheduleFlush(it) }
        return seqd
    }

    /** 同步刷写所有待写日志到数据库，并检查存储容量。 */
    fun flush() { storeLock.withLock { flushLocked() } }

    private fun flushLocked(): Boolean {
        var success = true
        val batch = lock.withLock {
            flushScheduled = false
            if (pending.isEmpty()) {
                null
            } else {
                val b = pending.toList()
                pending.clear()
                totalBytes = 0
                b
            }
        }
        if (batch != null) {
            val sanitized = batch.map { sanitizeEvent(it) }
            val ok = runCatching { logStore.appendBatch(sanitized) }.isSuccess
            if (!ok) {
                success = false
                // 落库失败：回灌原始批次，等待下次刷写重试（容量超限时按策略丢弃）。
                lock.withLock {
                    pending.addAll(0, batch)
                    totalBytes += batch.sumOf { it.size.toLong() }
                    enforceCapacity()
                }
            }
        }
        enforceStorageLimit()
        return success
    }

    /** 停止时调用：取消待执行刷写任务，强制同步 flush。 */
    fun onStop() {
        val job = lock.withLock {
            flushScheduled = false
            val j = flushJob
            flushJob = null
            j
        }
        job?.cancel()
        flush()
    }

    fun snapshot(): List<LogEvent> = storeLock.withLock {
        val pendingSnapshot = lock.withLock { pending.toList() }.map { sanitizeEvent(it) }
        val storeEvents = logStore.query(LogFilter(), limit = snapshotLimit, offset = 0)
        (pendingSnapshot + storeEvents).distinctBy { it.seq }.map { sanitizeEvent(it) }.sortedBy { it.seq }
    }

    fun version(): Long = lock.withLock { revision }

    /** Worker-only bounded reads. A stable DB view prevents concurrent inserts/eviction shifting pages. */
    fun forEachPage(filter: LogFilter, consume: (List<LogEvent>) -> Unit) = storeLock.withLock {
        check(flushLocked()) { "日志写入失败，请重试" }
        var cursor = filter.beforeSeq
        while (true) {
            val page = logStore.query(filter.copy(beforeSeq = cursor), 256, 0)
            if (page.isEmpty()) break
            consume(page.map { sanitizeEvent(it) })
            val next = page.last().seq
            check(cursor == null || next < cursor) { "日志分页游标未推进" }
            cursor = next
        }
    }

    fun clear() = storeLock.withLock {
        val job = lock.withLock {
            pending.clear()
            revision++
            totalBytes = 0
            flushScheduled = false
            val j = flushJob
            flushJob = null
            j
        }
        job?.cancel()
        logStore.clear()
    }

    fun droppedCount(): Long = lock.withLock { droppedCount }

    fun storageTrimCount(): Long = lock.withLock { storageTrimCount }

    /** 对单条事件延迟脱敏，并同步修正 size 以匹配脱敏后的消息长度。 */
    private fun sanitizeEvent(e: LogEvent): LogEvent {
        val safe = LogSanitizer.sanitize(e)
        val msg = sanitize(safe.message)
        return safe.copy(message = msg, size = (e.size + (msg.length - e.message.length)).coerceAtLeast(0))
    }

    /**
     * 容量淘汰：优先丢弃最旧的低级别日志（DEBUG → INFO → 任意级别），直至回到限额内。
     * 通过一次标记 + 一次重建（O(n)）完成，避免逐条中间删除带来的 O(n²) 移位。
     */
    private fun enforceCapacity() {
        if (pending.size <= maxEntries && totalBytes <= maxBytes) return
        var entries = pending.size
        var bytes = totalBytes
        val drop = HashSet<Int>()
        for (lvl in arrayOf(LogLevel.DEBUG, LogLevel.INFO, null)) {
            if (entries <= maxEntries && bytes <= maxBytes) break
            for (i in pending.indices) {
                if (i in drop) continue
                if (lvl != null && pending[i].level != lvl) continue
                drop.add(i)
                entries--
                bytes -= pending[i].size
                if (entries <= maxEntries && bytes <= maxBytes) break
            }
        }
        if (drop.isEmpty()) return
        val kept = ArrayList<LogEvent>(pending.size - drop.size)
        for (i in pending.indices) if (i !in drop) kept.add(pending[i])
        droppedCount += (pending.size - kept.size).toLong()
        pending.clear()
        pending.addAll(kept)
        totalBytes = bytes
    }

    /** 检查数据库容量，超过 maxStorageBytes 时淘汰最旧日志。 */
    private fun enforceStorageLimit() {
        val current = logStore.sizeBytes()
        if (current <= maxStorageBytes) return
        logStore.dropOldest(maxStorageBytes / 2)
        lock.withLock { storageTrimCount++; revision++ }
    }

    private fun scheduleFlush(delayMs: Long) {
        val job = scope.launch {
            if (delayMs > 0) delay(delayMs)
            flush()
        }
        lock.withLock { flushJob = job }
    }
}
