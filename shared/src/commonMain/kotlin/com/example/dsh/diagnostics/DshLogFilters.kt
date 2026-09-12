package com.example.dsh.diagnostics

import com.example.dsh.infrastructure.LogFilter
import com.example.dsh.infrastructure.LogLevel
import com.example.dsh.infrastructure.LogExporter

/** Entry parameters only seed editable filters. Empty sets mean no restriction. */
internal data class DshLogFilters(
    val sessions: Set<String> = emptySet(),
    val levels: Set<LogLevel> = emptySet(),
    val types: Set<String> = emptySet(),
    val fromTime: Long? = null,
    val toTime: Long? = null,
    val keyword: String = "",
    val rpcId: String = "",
) {
    val active: Boolean get() = sessions.isNotEmpty() || levels.isNotEmpty() || types.isNotEmpty() ||
        fromTime != null || toTime != null || keyword.isNotEmpty() || rpcId.isNotEmpty()

    fun query(all: Boolean = false): DshLogQuery = if (all) DshLogQuery() else DshLogQuery(
        LogFilter(sessionIds = sessions.toList(), levels = levels.toList(), types = types.toList(),
            fromTime = fromTime, toTime = toTime, rpcId = rpcId.takeIf { it.isNotBlank() }), keyword,
    )

    companion object {
        const val UNASSOCIATED = "__mobile__"
        fun forSession(sessionId: String) = DshLogFilters(
            sessions = sessionId.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet(),
        )

        // Exact recorded types: groups expand to an OR of these values, never message keyword matching.
        val typePresets = linkedMapOf(
            "流式片段" to listOf("assistant/chunk"),
            "工具调用" to listOf("tool/call"),
            "工具结果" to listOf("tool/result"),
            "回合结束" to listOf("turn/end"),
            "连接建立 / 断开" to listOf("connect.connecting", "connect.handshake", "connect.syncing",
                "connect.ready", "connect.error", "connect.stopped", "connect.disconnected"),
            "重连 / 重试" to listOf("connect.reconnecting", "prompt.hold-for-resync"),
            "RPC 起止 / 失败" to listOf("rpc.start", "rpc.complete", "rpc.failed"),
            "mux / host 关键帧" to listOf("host.frame", "host.frame.parse-error", "host.frame.no-payload", "host.frame.invalid"),
        )
    }
}

/** Minute-precision picker: reject impossible dates, include the entire selected end minute. */
internal fun dshLogPickerEpoch(year: Int, month: Int, day: Int, hour: Int, minute: Int, end: Boolean): Long? {
    if (year !in 1970..9999 || month !in 1..12 || hour !in 0..23 || minute !in 0..59) return null
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val days = when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
    if (day !in 1..days) return null
    return LogExporter.parseEpoch(year, month, day, hour, minute) + if (end) 59_999L else 0L
}
