package com.example.dsh.infrastructure

import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.fclose

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val path = "$dir/$filename"
    check((content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)) { "无法写入导出文件" }
    return path
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun appendExportFile(path: String, content: String) {
    val file = fopen(path, "a") ?: error("无法打开导出文件")
    val result = fputs(content, file)
    val closed = fclose(file)
    check(result >= 0 && closed == 0) { "导出文件写入失败" }
}

internal actual fun shareExportFile(path: String) = Unit

internal actual fun localTimezoneOffsetMillis(): Long =
    NSTimeZone.defaultTimeZone.secondsFromGMT.toLong() * 1000L
