package com.example.dsh.platform

/**
 * 跨线程互斥锁，供日志写入队列在内存队列中做跨线程防护。
 *
 * enqueue 可能来自任意线程，后台 flush 在 `Dispatchers.Default` 协程执行；
 * 两侧都会访问共享内存，需要互斥。
 */
expect class DshLock() {
    fun <T> withLock(block: () -> T): T
}
