package com.example.dsh.infrastructure

import kotlin.js.Date

internal actual fun currentTimeMillis(): Long = Date.now().toLong()