package com.example.dsh.infrastructure

import java.io.File

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val file = File(dir, filename)
    file.parentFile?.mkdirs()
    file.writeText(content)
    return file.absolutePath
}

/** Stub — actual share requires Activity context, deferred to UI layer. */
internal actual fun shareExportFile(path: String) {
    android.util.Log.i("DshExport", "Export file ready at: $path")
}