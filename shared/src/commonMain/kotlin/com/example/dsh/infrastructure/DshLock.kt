package com.example.dsh.infrastructure

/**
 * 轻量互斥锁：保护 [DshLogWriteBehind] 内存队列的跨线程访问。
 *
 * 日志 enqueue 来自任意调用线程（主线程为主），flush 在
 * `Dispatchers.Default` 协程执行，两者并发访问共享队列时需要互斥。
 */
internal expect class DshLock() {
    fun <T> withLock(block: () -> T): T
}
