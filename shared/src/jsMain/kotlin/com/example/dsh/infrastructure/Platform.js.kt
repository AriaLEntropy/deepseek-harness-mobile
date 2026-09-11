package com.example.dsh.infrastructure

import kotlin.js.Date

internal actual fun currentTimeMillis(): Long = Date.now().toLong()

internal actual fun localTimeZoneId(): String {
    val tz: String = js("Intl.DateTimeFormat().resolvedOptions().timeZone")
    return tz.takeIf { it.isNotEmpty() } ?: "UTC"
}