package com.example.dsh.infrastructure

/** JS target has no sandboxed file system for export; log export is Android/iOS/OHOS only. */
internal actual fun writeExportFile(dir: String, filename: String, content: String): String = error("当前平台尚不支持文件导出")
internal actual fun appendExportFile(path: String, content: String): Unit = error("当前平台尚不支持文件导出")

/** No-op on JS; deferred to native platforms. */
internal actual fun shareExportFile(path: String) = Unit

internal actual fun localTimezoneOffsetMillis(): Long {
    // QuickJS 等嵌入式 JS 引擎无系统时区数据（getTimezoneOffset 恒为 0），
    // 优先使用页面启动时从原生侧同步缓存的偏移
    cachedLocalTimezoneOffsetMillis?.let { return it }
    val offsetMinutes = js("new Date().getTimezoneOffset()").unsafeCast<Int>()
    return -offsetMinutes.toLong() * 60L * 1000L
}
