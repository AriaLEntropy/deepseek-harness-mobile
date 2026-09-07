package com.example.dsh.infrastructure

internal actual class DshLock actual constructor() {
    private val monitor = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
