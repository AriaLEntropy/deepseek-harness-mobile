package com.example.dsh.infrastructure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.concurrent.Volatile

/**
 * 应用级唯一日志服务。
 *
 * 日志写入器和序号分配由本对象作为唯一所有者管理，生命周期与 App 进程一致，而不是
 * 绑定到某个主页实例。这样：
 * - 连接设置页首次进入即可开始采集，覆盖「主页创建之前」的连接探测日志；
 * - 主页重建、多个主页实例并存时复用同一写入器，序号不会冲突，旧页面销毁也不会
 *   停止新实例的日志服务；
 * - 页面只通过 [current] 拿到查询能力，不再创建/替换/关闭写入器。
 *
 * [ensureStarted] 幂等：只接受首次传入的数据库目录，之后的调用直接返回已有实例。
 */
internal object DshLogService {
    private val lifecycle = DshLock()
    private var scope: CoroutineScope? = null

    /** 唯一写入器；volatile 供日志热路径无锁读取。创建/替换仍在 [lifecycle] 保护下。 */
    @Volatile
    private var writer: DshLogWriteBehind? = null

    /** 当前唯一写入器；尚未初始化时返回 null。 */
    val current: DshLogWriteBehind? get() = writer

    /** 幂等启动。只创建轻量写入器；建库在后台执行，失败保留队列并自动重试。 */
    fun ensureStarted(dir: String): DshLogWriteBehind? {
        if (dir.isBlank()) return null
        return lifecycle.withLock {
            writer?.let { return@withLock it }
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            // Opening SQLite may block/fail. Defer it to the writer and retry after failure.
            val store = RetryingDshLogStore { createDshLogStore("$dir/dsh_logs.db") ?: error("无法打开日志数据库") }
            val newWriter = DshLogWriteBehind(store, newScope)
            newWriter.onStart()
            scope = newScope
            writer = newWriter
            newWriter
        }
    }

    /** 非阻塞刷盘请求；供页面隐藏/销毁等时机调用，不改变所有权。 */
    fun flush() {
        current?.requestFlush()
    }

    /** App 级关闭：排空并释放。仅在进程退出时调用，页面销毁不得调用。 */
    fun shutdown() {
        lifecycle.withLock {
            writer?.onStop()
            scope?.cancel()
            writer = null
            scope = null
        }
    }

}

/** Access is serialized by the writer's store lock. Failed constructors remain retryable. */
private class RetryingDshLogStore(private val factory: () -> DshLogStore) : DshLogStore {
    private var value: DshLogStore? = null
    private fun store(): DshLogStore = value ?: factory().also { value = it }
    override fun appendBatch(events: List<LogEvent>) = store().appendBatch(events)
    override fun query(filter: LogFilter, limit: Int, offset: Int) = store().query(filter, limit, offset)
    override fun clear() = store().clear()
    override fun clearAndMarkCrash(id: String?) = store().clearAndMarkCrash(id)
    override fun appendCrashOnce(id: String, event: LogEvent) = store().appendCrashOnce(id, event)
    override fun close() { value?.close(); value = null }
    override fun maxSeq() = store().maxSeq()
    override fun diskBytes() = store().diskBytes()
    override fun count() = store().count()
    override fun countLowLevel() = store().countLowLevel()
    override fun deleteOldest(limit: Int, lowLevelOnly: Boolean) = store().deleteOldest(limit, lowLevelOnly)
    override fun compact() = store().compact()
}
