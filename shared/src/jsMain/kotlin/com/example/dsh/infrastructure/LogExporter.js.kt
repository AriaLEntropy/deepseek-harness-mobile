package com.example.dsh.infrastructure

/** JS target has no sandboxed file system for export; log export is Android/iOS/OHOS only. */
internal actual fun writeExportFile(dir: String, filename: String, content: String): String = "$dir/$filename"

/** No-op on JS; deferred to native platforms. */
internal actual fun shareExportFile(path: String) = Unit

internal actual fun localTimezoneOffsetMillis(): Long {
    val offsetMinutes = js("new Date().getTimezoneOffset()").unsafeCast<Int>()
    return -offsetMinutes.toLong() * 60L * 1000L
}
