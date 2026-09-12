package com.example.dsh.infrastructure

import kotlinx.cinterop.memScoped
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.remove
import platform.posix.rename
import platform.posix.tzset
import platform.posix.time
import platform.posix.time_tVar
import platform.posix.tm
import platform.posix.localtime_r
import kotlinx.cinterop.*
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.closedir
import platform.posix.stat
import platform.posix.errno
import platform.posix.ENOENT

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val path = "$dir/$filename"
    memScoped {
        val file = fopen(path, "w") ?: error("无法打开导出文件")
        val result = fputs(content, file)
        val closed = fclose(file)
        if (result < 0 || closed != 0) { remove(path); error("导出文件写入失败") }
    }
    return path
}

internal actual fun appendExportFile(path: String, content: String) {
    val file = fopen(path, "a") ?: error("无法打开导出文件")
    val result = fputs(content, file)
    val closed = fclose(file)
    check(result >= 0 && closed == 0) { "导出文件写入失败" }
}

internal actual fun renameExportFile(fromPath: String, toPath: String) {
    check(rename(fromPath, toPath) == 0) { "无法发布导出文件" }
}

internal actual fun deleteExportFile(path: String) {
    remove(path)
}

internal actual fun listExportFiles(dir: String): List<String> {
    val handle = opendir(dir) ?: return emptyList()
    return try {
        buildList {
            while (true) {
                val entry = readdir(handle) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name != "." && name != "..") add("$dir/$name")
            }
        }
    } finally { closedir(handle) }
}

internal actual fun fileSizeBytes(path: String): Long = memScoped {
    val info = alloc<stat>()
    if (stat(path, info.ptr) == 0) info.st_size.toLong()
    else if (errno == ENOENT) 0L else error("无法读取文件大小")
}

internal actual fun shareExportFile(path: String) = Unit

/** OHOS 用 POSIX timezone 全局变量获取系统时区偏移（tzset 初始化后读取） */
internal actual fun localTimezoneOffsetMillis(): Long {
    tzset()
    return memScoped {
        val now = alloc<time_tVar>().apply { value = time(null) }
        val local = alloc<tm>()
        check(localtime_r(now.ptr, local.ptr) != null) { "无法读取本地时区" }
        local.tm_gmtoff.toLong() * 1000L
    }
}
