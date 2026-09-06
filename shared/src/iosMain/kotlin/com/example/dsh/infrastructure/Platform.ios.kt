package com.example.dsh.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
internal actual fun currentTimeMillis(): Long = time(null) * 1000L