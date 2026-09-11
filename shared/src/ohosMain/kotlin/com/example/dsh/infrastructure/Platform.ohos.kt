package com.example.dsh.infrastructure

import kotlin.system.getTimeMillis
import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun currentTimeMillis(): Long = getTimeMillis()

internal actual fun localTimeZoneId(): String =
    getenv("TZ")?.toKString()?.takeIf { it.isNotEmpty() } ?: "UTC"