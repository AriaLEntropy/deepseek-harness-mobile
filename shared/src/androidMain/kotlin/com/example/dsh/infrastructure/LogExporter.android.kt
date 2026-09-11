package com.example.dsh.infrastructure

import java.io.File

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val file = File(dir, filename)
    file.parentFile?.mkdirs()
    file.writeText(content)
    return file.absolutePath
}

internal actual fun appendExportFile(path: String, content: String) { File(path).appendText(content) }

internal actual fun shareExportFile(path: String) {
    android.util.Log.i("DshExport", "Export file ready at: $path")
}

internal actual fun localTimezoneOffsetMillis(): Long =
    java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
