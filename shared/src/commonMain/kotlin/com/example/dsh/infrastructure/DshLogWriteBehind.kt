package com.example.dsh.infrastructure

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * 异步写后缓存：日志先进入内存队列，批量刷入 SQLite。
 *
 * 生命周期状态机：
 * - `Running`：[onStart] 启动唯一后台写入任务，随后 [enqueue] 持续接收日志。
 * - `Draining`：收到停止请求后不再接收新日志（迟到事件计数丢弃），后台任务把剩余队列刷完后结束。
 * - `Stopped`：[onStop] 完成后进入，拒绝一切写入。
 *
 * 调度模型：所有入队只唤醒同一个后台任务（[Channel.CONFLATED]），后台任务按
 * 「达到批量阈值立即刷 / 否则延迟合并刷」运行；写入失败时按有上限的指数退避自动重试，
 * 即使没有新日志也会在存储恢复后自行落库，不再为每条事件排队一个刷写任务。
 *
 * 容量策略（双层）：
 * 1. 内存队列：maxEntries / maxBytes，超限时优先丢弃 DEBUG→INFO 级别的旧日志
 * 2. 数据库：maxStorageBytes，每次 flush 后检查，超限时淘汰最旧日志
 *
 * 取数上限：snapshot 查询数据库时使用 [snapshotLimit]，与内存队列上限 [maxEntries]
 * 解耦，可单独调大以便界面/导出看到更多历史，而不增加内存驻留压力。
 *
 * 并发模型：enqueue 来自任意调用线程（主线程为主），后台任务在 `Dispatchers.Default`
 * 执行，两者与 onStop/clear/snapshot 并发访问共享队列。所有共享状态由 [lock] 保护，
 * 耗时 IO（appendBatch / 容量查询）由 [storeLock] 串行化并在临界区之外执行。
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
    private val retryInitialMs: Long = 500,
    private val retryMaxMs: Long = 30_000,
    private val maxStorageTrimRounds: Int = 16,
    private val sanitize: (String) -> String = { LogSanitizer.sanitize(it) },
) {
    private val lock = DshLock()
    // Serialize DB operations and flush/clear boundaries; enqueue never waits on SQLite.
    private val storeLock = DshLock()
    private var revision = 0L
    private var clearEpoch = 0L
    private val pending = mutableListOf<LogEvent>()

    /** 唯一后台写入任务的唤醒信号；合并所有排队请求，任务数量与事件数无关。 */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** 从库内已有最大 seq 之后开始编号；惰性初始化，避免在构造期执行阻塞 IO。 */
    private var nextSeq = 0L
    private var seqInitialized = false
    private var transientSeq = Long.MIN_VALUE

    private var workerJob: Job? = null
    private var totalBytes = 0L
    private var droppedCount = 0L
    private var storageTrimCount = 0L
    private var started = false

    /** 停止后不再接收新日志；迟到事件计入 [droppedCount]。 */
    @Volatile
    private var accepting = true

    @Volatile
    private var stopped = false

    /**
     * 按操作保存失败原因；只有同一操作恢复才清除。用于向页面暴露降级状态，
     * 而不是把日志存储异常抛给对话业务。
     */
    private val failures = mutableMapOf<String, String>()
    @Volatile private var maintenancePending = true
    @Volatile private var flushRequested = false

    /** 非阻塞启动唯一后台任务；该任务初始化序号并检查容量。幂等。 */
    fun onStart() {
        val shouldStart = lock.withLock {
            if (started || stopped) false else {
                started = true
                workerJob = scope.launch { runWorker() }
                true
            }
        }
        // 早于 onStart 的入队会先把信号放进 Channel；后台任务启动后需要被唤醒一次。
        if (shouldStart) wake.trySend(Unit)
    }

    /** Called only by background/store operations. Failed reads never allocate persistent IDs. */
    private fun ensureSeqInitialized(): Boolean {
        if (lock.withLock { seqInitialized }) return true
        val maxSeq = runCatching { logStore.maxSeq() }
            .onFailure { recordFailure("sequence", it) }.getOrNull() ?: return false
        lock.withLock {
            nextSeq = maxSeq + 1
            for (i in pending.indices) pending[i] = pending[i].copy(seq = nextSeq++)
            seqInitialized = true
        }
        clearFailure("sequence")
        return true
    }

    fun enqueue(event: LogEvent): LogEvent {
        var shouldWake = false
        val seqd = lock.withLock {
            // Before the DB is readable, IDs are queue-local only; flush assigns durable IDs.
            val e = event.copy(seq = if (seqInitialized) nextSeq++ else transientSeq++)
            if (!accepting) {
                droppedCount++
            } else {
                revision++
                pending.add(e)
                totalBytes += e.size
                enforceCapacity()
                shouldWake = pending.isNotEmpty()
            }
            e
        }
        if (shouldWake) wake.trySend(Unit)
        return seqd
    }

    /** 同步刷写所有待写日志到数据库，并检查存储容量。返回是否全部成功。 */
    fun flush(): Boolean = storeLock.withLock { flushLocked() }

    /** Non-blocking lifecycle hook: coalesces work on the single background writer. */
    fun requestFlush() { flushRequested = true; wake.trySend(Unit) }

    private fun flushLocked(isCancelled: () -> Boolean = { false }): Boolean {
        if (isCancelled()) throw CancellationException("日志读取已取消")
        if (!ensureSeqInitialized()) return false
        var success = true
        val batch = lock.withLock {
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
            val append = runCatching { logStore.appendBatch(sanitized) }
            if (append.isFailure) {
                recordFailure("write", append.exceptionOrNull()!!)
                success = false
                // 落库失败：回灌原始批次，等待退避重试（容量超限时按策略丢弃）。
                lock.withLock {
                    pending.addAll(0, batch)
                    totalBytes += batch.sumOf { it.size.toLong() }
                    enforceCapacity()
                }
            } else {
                clearFailure("write")
            }
        }
        maintenancePending = !runCatching { enforceStorageLimit(isCancelled) }
            .onFailure { if (it is CancellationException) throw it else recordFailure("maintenance", it) }.getOrDefault(false)
        return success && !maintenancePending
    }

    /**
     * 停止：进入 Draining，拒绝迟到事件，取消后台任务并做一次同步排空。
     * 存储不可用时未落库的日志保留在内存并由 [pendingCount] 报告。幂等。
     */
    fun onStop() {
        val shouldStop = lock.withLock {
            if (stopped) false else { stopped = true; accepting = false; started = false; true }
        }
        if (!shouldStop) return
        val job = lock.withLock { val j = workerJob; workerJob = null; j }
        job?.cancel()
        wake.trySend(Unit)
        flush()
    }

    fun snapshot(): List<LogEvent> = storeLock.withLock {
        ensureSeqInitialized()
        // 待写队列尚未落库，需要在这里脱敏；库内记录在 flush/importCrash 时已脱敏，读取不再重复处理。
        val pendingSnapshot = lock.withLock { pending.toList() }.map { sanitizeEvent(it) }
        val storeEvents = runCatching {
            logStore.query(LogFilter(), limit = snapshotLimit, offset = 0)
        }.onFailure { recordFailure("query", it) }.onSuccess { clearFailure("query") }.getOrDefault(emptyList())
        (pendingSnapshot + storeEvents).distinctBy { it.seq }.sortedBy { it.seq }
    }

    fun version(): Long = lock.withLock { revision }

    /**
     * 有界分页读取。每页只在 [storeLock] 内查询，[consume] 在锁外执行，
     * 因此导出等回调的文件 IO 不会长时间占用写入锁；批次之间检查 [isCancelled]，
     * 取消能在有限批次内停止扫描。新插入的记录 seq 更大，不会影响游标推进；
     * 并发淘汰掉尚未读到的旧记录时，视为快照读取的合理缺失。
     */
    fun forEachPage(
        filter: LogFilter,
        isCancelled: () -> Boolean = { false },
        consume: (List<LogEvent>) -> Unit,
    ) {
        if (isCancelled()) throw CancellationException("日志读取已取消")
        check(storeLock.withLock { flushLocked(isCancelled) } || lock.withLock { seqInitialized && pending.isEmpty() }) { "日志写入失败，请重试" }
        var cursor = filter.beforeSeq
        while (true) {
            if (isCancelled()) throw CancellationException("日志读取已取消")
            val page = storeLock.withLock {
                runCatching { logStore.query(filter.copy(beforeSeq = cursor), 256, 0) }
                    .onFailure { recordFailure("query", it) }
                    .getOrThrow()
            }
            clearFailure("query")
            if (isCancelled()) throw CancellationException("日志读取已取消")
            if (page.isEmpty()) break
            // 库内记录在 flush/importCrash 时已脱敏，读取直接返回，避免整表重复跑正则拖慢查询。
            consume(page)
            val next = page.last().seq
            check(cursor == null || next < cursor) { "日志分页游标未推进" }
            cursor = next
        }
    }

    /**
     * 单页游标读取：只取匹配 [filter] 的前 [limit] 条，不做整表遍历。
     * 用于日志页懒加载/增量刷新，避免为了看一页而扫描全部保留记录。
     */
    fun readSlice(filter: LogFilter, limit: Int, isCancelled: () -> Boolean = { false }): List<LogEvent> {
        if (isCancelled()) throw CancellationException("日志读取已取消")
        check(storeLock.withLock { flushLocked(isCancelled) } || lock.withLock { seqInitialized && pending.isEmpty() }) { "日志写入失败，请重试" }
        if (isCancelled()) throw CancellationException("日志读取已取消")
        return storeLock.withLock {
            runCatching { logStore.query(filter, limit, 0) }
                .onFailure { recordFailure("query", it) }
                .onSuccess { clearFailure("query") }
                .getOrThrow()
        }
    }

    fun clear(crashId: String? = null) = storeLock.withLock {
        // Preserve pending records on failure; only acknowledge a successful local clear.
        runCatching { logStore.clearAndMarkCrash(crashId) }.onFailure { recordFailure("clear", it) }.getOrThrow()
        lock.withLock {
            clearEpoch++
            pending.clear()
            revision++
            totalBytes = 0
        }
        clearFailure("clear")
        maintenancePending = true
        requestFlush()
    }

    fun clearVersion(): Long = lock.withLock { clearEpoch }

    /** A successful return acknowledges the SQLite transaction, not merely an in-memory enqueue. */
    fun importCrash(id: String, event: LogEvent, expectedClearVersion: Long): Boolean = storeLock.withLock {
        if (lock.withLock { clearEpoch != expectedClearVersion || stopped }) return@withLock false
        check(ensureSeqInitialized()) { "日志数据库暂不可用，崩溃记录保留待重试" }
        val seq = lock.withLock { nextSeq++ }
        val imported = runCatching { logStore.appendCrashOnce(id, sanitizeEvent(event.copy(seq = seq))) }
            .onFailure { recordFailure("crash", it) }.getOrThrow()
        clearFailure("crash")
        if (imported) lock.withLock { revision++ }
        maintenancePending = true
        requestFlush()
        imported
    }

    fun droppedCount(): Long = lock.withLock { droppedCount }

    fun storageTrimCount(): Long = lock.withLock { storageTrimCount }

    /** 停止后仍留在内存（尚未落库）的日志条数，用于报告未保存数量。 */
    fun pendingCount(): Int = lock.withLock { pending.size }

    /** 存储是否处于降级状态（最近一次存储操作失败且尚未恢复）。 */
    fun isDegraded(): Boolean = lock.withLock { failures.isNotEmpty() }

    /** 降级原因；正常时为 null。供日志页以非阻塞方式提示。 */
    fun lastFailure(): String? = lock.withLock { failures.values.takeIf { it.isNotEmpty() }?.joinToString("；") }

    private fun recordFailure(operation: String, t: Throwable) {
        lock.withLock {
            failures[operation] = LogSanitizer.sanitize(t.message?.takeIf { it.isNotBlank() } ?: "日志存储不可用")
        }
    }

    private fun clearFailure(operation: String) {
        lock.withLock { failures.remove(operation) }
    }

    /**
     * 唯一后台写入任务：等待唤醒 → 合并刷盘；失败则指数退避后重试，直到成功或任务被取消。
     * 任务数量恒定为一，突发入队不会创建新任务。
     */
    private suspend fun runWorker() {
        var backoff = retryInitialMs
        while (currentCoroutineContext().isActive) {
            val hasPending = lock.withLock { pending.isNotEmpty() }
            if (!hasPending && !maintenancePending) {
                if (stopped) break
                wake.receive()
                continue
            }
            val batchReady = lock.withLock { pending.size >= batchFlushSize }
            if (hasPending && !batchReady && !flushRequested) {
                // A wake is not a batch threshold: keep coalescing until full or timeout.
                withTimeoutOrNull(flushDelayMs) {
                    while (lock.withLock { pending.size < batchFlushSize } && !stopped && !flushRequested) wake.receive()
                }
            }
            flushRequested = false
            val ok = flush()
            if (ok) {
                backoff = retryInitialMs
            } else {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(retryMaxMs)
            }
        }
    }

    /** 对单条事件延迟脱敏，并同步修正 size 的字符估算；磁盘容量另用实际文件字节。 */
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

    /**
     * 容量硬上限：以数据库实际磁盘字节数为准，分批淘汰并复查，直到降到回收目标或无法再推进。
     * - 优先删除最旧的 DEBUG/INFO（保留 WARN/ERROR）；低级别删空后按最旧任意级别兜底。
     * - 每批删除后 VACUUM 回收物理页，使实际文件大小真正下降。
     * - 单次维护的轮次有上限，避免长时间持锁；存储异常静默降级。
     */
    private fun enforceStorageLimit(isCancelled: () -> Boolean = { false }): Boolean {
        val initial = logStore.diskBytes()
        if (initial <= maxStorageBytes) {
            clearFailure("maintenance")
            return true
        }
        val target = maxStorageBytes / 2
        var trimmed = false
        var rounds = 0
        while (rounds++ < maxStorageTrimRounds.coerceAtLeast(1)) {
            if (isCancelled()) throw CancellationException("日志维护已取消")
            val total = logStore.count()
            if (total <= 0L) {
                logStore.compact()
                trimmed = true
                break
            }
            val low = logStore.countLowLevel()
            // Reserve the final rounds for exhausting low-level rows and a hard fallback.
            // Otherwise a large population of tiny Info rows can starve large Error rows.
            val lastRound = rounds >= maxStorageTrimRounds
            val lowOnly = low > 0 && !lastRound
            val amount = when {
                lastRound -> total
                lowOnly && rounds >= (maxStorageTrimRounds / 2).coerceAtLeast(1) -> low
                lowOnly -> (low + 1) / 2
                else -> (total + 3) / 4
            }.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            val deleted = logStore.deleteOldest(amount, lowLevelOnly = lowOnly)
            if (deleted > 0) trimmed = true
            // Stop deleting if compaction fails; retry maintenance independently of new events.
            logStore.compact()
            val now = logStore.diskBytes()
            if (now <= target || deleted <= 0) break
        }
        if (trimmed) lock.withLock { storageTrimCount++; revision++ }
        val bytes = logStore.diskBytes()
        check(bytes <= maxStorageBytes) { "日志磁盘占用仍超限：$bytes / $maxStorageBytes 字节，正在重试清理" }
        clearFailure("maintenance")
        return true
    }
}
