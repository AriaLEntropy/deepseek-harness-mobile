package com.example.dsh.diagnostics

import kotlinx.coroutines.*
import kotlin.concurrent.Volatile

/**
 * 在后台作用域执行一次阻塞式日志工作，页面在自己的 Kuikly 线程轮询 [take]。
 *
 * 工作函数接收一个 `isCancelled` 探针：应传入 [DshLogQuery] 的分页扫描，在批次边界
 * 检查该探针，从而让 [cancel] 或页面作用域取消能在有限批次内停止实际 IO，而不是
 * 等整次扫描结束。
 */
internal class DshLogWork<T>(scope: CoroutineScope, work: (isCancelled: () -> Boolean) -> T) {
    @Volatile private var result: Result<T>? = null
    @Volatile private var cancelled = false
    private val job = scope.launch {
        if (cancelled) return@launch
        // 协作取消：显式 cancel 或所属作用域被取消（页面销毁）都应在批次边界停下。
        result = runCatching { work { cancelled || !this.isActive } }
    }
    fun take(): Result<T>? = result.also { result = null }
    fun cancel() { cancelled = true; job.cancel() }
}
