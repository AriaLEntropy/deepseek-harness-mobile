package com.example.dsh.infrastructure

import kotlinx.cinterop.memScoped
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val path = "$dir/$filename"
    memScoped {
        val file = fopen(path, "w") ?: return@memScoped
        fputs(content, file)
        fclose(file)
    }
    return path
}

/** Stub — actual share deferred to UI layer. */
internal actual fun shareExportFile(path: String) = Unit