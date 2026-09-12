package com.example.dsh.diagnostics

/**
 * 崩溃快照的稳定标识与「已导入」持久化状态。
 *
 * 主页只把每个崩溃快照导入日志中心一次，且跨主页重建、跨进程重启都有效：
 * - 用内容哈希作为稳定 ID，避免依赖某个主页实例的内存字段；
 * - 「已导入」ID 与日志一起在 SQLite 事务内提交，重启后仍能去重；
 * - 清空本地日志时由日志页调用原生 `clearLastCrash` 一并清除快照，但保留清除 ID，
 *   这样即使原生清除失败，同一崩溃也不会在清空后再次出现。
 */
internal object DshCrashMarker {
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L

    /** FNV-1a 64 位 + 长度混合；跨平台确定性，避免使用平台相关的 String.hashCode。 */
    fun idOf(raw: String): String {
        var hash = FNV_OFFSET
        for (ch in raw) {
            hash = (hash xor ch.code.toLong()) * FNV_PRIME
        }
        hash = (hash xor raw.length.toLong()) * FNV_PRIME
        return hash.toString(16)
    }

    /** 仅当该崩溃尚未被消费时才导入。 */
    fun shouldImport(id: String, lastConsumed: String): Boolean = id.isNotEmpty() && id != lastConsumed

}
