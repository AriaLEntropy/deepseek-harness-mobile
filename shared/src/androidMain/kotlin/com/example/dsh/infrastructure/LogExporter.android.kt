package com.example.dsh.infrastructure

import java.io.File

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val file = File(dir, filename)
    file.parentFile?.mkdirs()
    try { file.writeText(content) } catch (t: Throwable) { file.delete(); throw t }
    return file.absolutePath
}

internal actual fun appendExportFile(path: String, content: String) { File(path).appendText(content) }

@Suppress("NewApi")
internal actual fun renameExportFile(fromPath: String, toPath: String) {
    val from = File(fromPath)
    val to = File(toPath)
    to.parentFile?.mkdirs()
    if (!from.renameTo(to)) {
        // Windows JVM also runs shared tests; on Android <26 use POSIX rename (API 21).
        if (android.os.Build.VERSION.SDK_INT >= 26 || System.getProperty("java.vm.name") != "Dalvik") {
            java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } else {
            android.system.Os.rename(fromPath, toPath)
        }
    }
}

internal actual fun deleteExportFile(path: String) {
    val file = File(path)
    check(!file.exists() || file.delete()) { "无法删除导出文件" }
}

internal actual fun fileSizeBytes(path: String): Long = File(path).length()

internal actual fun listExportFiles(dir: String): List<String> {
    val files = File(dir).listFiles() ?: return emptyList()
    return files.sortedBy { it.lastModified() }.map { it.absolutePath }
}

internal actual fun shareExportFile(path: String) {
    android.util.Log.i("DshExport", "Export file ready at: $path")
}

internal actual fun localTimezoneOffsetMillis(): Long =
    java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
