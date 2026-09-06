package com.example.dsh.infrastructure

internal data class LogEvent(
    val seq: Long,
    val timestamp: Long,
    val level: LogLevel,
    val type: String,
    val sessionId: String?,
    val rpcId: String?,
    val message: String,
    val size: Int,
)