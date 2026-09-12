package com.example.dsh.infrastructure

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.native.ref.createCleaner

@OptIn(kotlin.experimental.ExperimentalNativeApi::class, ExperimentalForeignApi::class)
internal actual class DshLock actual constructor() {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().ptr.also {
        check(pthread_mutex_init(it, null) == 0) { "无法创建日志互斥锁" }
    }
    private val cleaner = createCleaner(mutex) { pthread_mutex_destroy(it); nativeHeap.free(it) }

    actual fun <T> withLock(block: () -> T): T {
        pthread_mutex_lock(mutex)
        try { return block() } finally { pthread_mutex_unlock(mutex) }
    }
}
