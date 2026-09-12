package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Keeps original event envelopes (including tool views) until every page is available. */
internal class DshHistoryLoader(
    private val request: (JSONObject, (JSONObject?, DshRpcError?) -> Unit) -> Unit,
    private val generation: () -> Long,
    private val schedule: (() -> Unit) -> Unit,
) {
    fun loadAll(
        sessionId: String,
        onSuccess: (JSONArray) -> Unit,
        onError: (String) -> Unit,
        isCurrent: () -> Boolean = { true },
    ) {
        val entries = mutableMapOf<Int, JSONObject>()
        var expectedGeneration: Long? = null
        var before: Int? = null
        var finished = false
        fun next() {
            if (finished) return
            if (!isCurrent()) { finished = true; return }
            if (expectedGeneration != null && expectedGeneration != generation()) {
                finished = true; onError("连接已变化，请重新读取历史"); return
            }
            val requestedBefore = before
            request(JSONObject().apply {
                put("sessionId", sessionId)
                put("maxMessages", 80)
                requestedBefore?.let { put("beforeSeq", it) }
            }) { value, error ->
                if (finished || !isCurrent()) { finished = true; return@request }
                if (error != null || value == null) {
                    finished = true; onError(error?.message ?: "历史响应为空"); return@request
                }
                if (expectedGeneration == null) expectedGeneration = generation()
                if (expectedGeneration != generation()) {
                    finished = true; onError("连接已变化，请重新读取历史"); return@request
                }
                val parsed = runCatching {
                    val page = value.optJSONArray("events") ?: error("历史响应缺少 events")
                    check(value.opt("hasMore") is Boolean) { "历史响应缺少 hasMore，无法确认历史完整性" }
                    var oldest: Int? = null
                    for (index in 0 until page.length()) {
                        val entry = page.optJSONObject(index) ?: error("历史事件格式无效")
                        val seq = dshWireEvent(entry).optInt("seq", -1)
                        check(seq >= 0 && (requestedBefore == null || seq < requestedBefore)) { "历史分页游标未推进" }
                        oldest = minOf(oldest ?: seq, seq)
                        if (seq !in entries) entries[seq] = entry
                    }
                    val more = value.optBoolean("hasMore")
                    check(!more || (oldest != null && oldest > 0)) { "历史分页为空或游标未推进" }
                    before = oldest
                    more
                }
                parsed.onSuccess { more ->
                    if (more) schedule { next() } else {
                        finished = true
                        val merged = JSONArray()
                        entries.keys.sorted().forEach { merged.put(entries.getValue(it)) }
                        onSuccess(merged)
                    }
                }.onFailure { finished = true; onError(it.message ?: "历史解析失败") }
            }
        }
        next()
    }
}
