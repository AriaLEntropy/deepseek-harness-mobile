package com.example.dsh.infrastructure

import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile

internal actual fun writeExportFile(dir: String, filename: String, content: String): String {
    val path = "$dir/$filename"
    (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    return path
}

/** Stub — actual share requires UIViewController, deferred to UI layer. */
internal actual fun shareExportFile(path: String) = Unit