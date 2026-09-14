package com.example.dsh.platform

/** JS 单线程执行模型，无需互斥。 */
actual class DshLock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = block()
}
