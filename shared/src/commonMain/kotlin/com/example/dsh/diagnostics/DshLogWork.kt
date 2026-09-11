package com.example.dsh.diagnostics

import kotlinx.coroutines.*
import kotlin.concurrent.Volatile

/** Worker publishes plain data only. The page polls take() on its own Kuikly thread. */
internal class DshLogWork<T>(scope: CoroutineScope, work: () -> T) {
    @Volatile private var result: Result<T>? = null
    @Volatile private var cancelled = false
    private val job = scope.launch { if (!cancelled) result = runCatching(work) }
    fun take(): Result<T>? = result.also { result = null }
    fun cancel() { cancelled = true; job.cancel() }
}
