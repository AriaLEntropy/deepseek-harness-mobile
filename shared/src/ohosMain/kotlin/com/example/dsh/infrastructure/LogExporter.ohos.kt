package com.example.dsh.infrastructure

import kotlinx.cinterop.memScoped
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.tzset
import platform.posix.timezone

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val path = "$dir/$filename"
    memScoped {
        val file = fopen(path, "w") ?: error("无法打开导出文件")
        val result = fputs(content, file)
        val closed = fclose(file)
        check(result >= 0 && closed == 0) { "导出文件写入失败" }
    }
    return path
}

internal actual fun appendExportFile(path: String, content: String) {
    val file = fopen(path, "a") ?: error("无法打开导出文件")
    val result = fputs(content, file)
    val closed = fclose(file)
    check(result >= 0 && closed == 0) { "导出文件写入失败" }
}

internal actual fun shareExportFile(path: String) = Unit

/** OHOS 用 POSIX timezone 全局变量获取系统时区偏移（tzset 初始化后读取） */
internal actual fun localTimezoneOffsetMillis(): Long {
    tzset()
    return -timezone * 1000L
}
