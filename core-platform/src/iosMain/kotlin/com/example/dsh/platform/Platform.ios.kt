package com.example.dsh.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long = time(null) * 1000L

internal actual fun localTimeZoneId(): String {
    val id = NSTimeZone.systemTimeZone.name
    return if (!id.isNullOrEmpty()) id else "UTC"
}
