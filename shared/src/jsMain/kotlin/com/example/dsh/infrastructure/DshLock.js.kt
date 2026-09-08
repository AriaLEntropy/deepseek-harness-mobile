package com.example.dsh.infrastructure

/** JS 单线程执行模型，无需互斥。 */
internal actual class DshLock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = block()
}
