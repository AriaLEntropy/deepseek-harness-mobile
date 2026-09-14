package com.example.dsh.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTimeZone
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long = time(null) * 1000L

internal actual fun localTimeZoneId(): String =
    NSTimeZone.defaultTimeZone.name.takeIf { it.isNotEmpty() } ?: "UTC"
