package com.example.dsh.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTimeZone
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
internal actual fun currentTimeMillis(): Long = time(null) * 1000L

internal actual fun localTimeZoneId(): String =
    NSTimeZone.defaultTimeZone.name.takeIf { it.isNotEmpty() } ?: "UTC"