@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.dsh.infrastructure

import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.fclose
import platform.posix.remove
import platform.posix.rename

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val path = "$dir/$filename"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
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

@OptIn(ExperimentalForeignApi::class)
internal actual fun renameExportFile(fromPath: String, toPath: String) {
    check(rename(fromPath, toPath) == 0) { "无法发布导出文件" }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteExportFile(path: String) {
    remove(path)
}

internal actual fun listExportFiles(dir: String): List<String> =
    NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, error = null)
        ?.mapNotNull { (it as? String)?.let { name -> "$dir/$name" } }.orEmpty()

internal actual fun fileSizeBytes(path: String): Long {
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(path)) return 0
    val attributes = manager.attributesOfItemAtPath(path, error = null) ?: error("无法读取文件大小")
    return (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: error("文件大小缺失")
}

internal actual fun shareExportFile(path: String) = Unit

internal actual fun localTimezoneOffsetMillis(): Long =
    NSTimeZone.defaultTimeZone.secondsFromGMT.toLong() * 1000L
