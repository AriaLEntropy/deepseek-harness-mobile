package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** An unchanged bubble can still acquire Host identity during history reconciliation. */
internal fun dshSyncMessageForkAnchor(local: DshMessage, host: DshMessage): DshMessage {
    val seq = host.sourceSeq?.takeIf { it >= 0 } ?: return local
    return if (local.visuallyEquals(host)) local.copy(sourceSeq = seq) else local
}

/** Message forks must always carry a Host anchor; omitting it forks the latest turn instead. */
internal class DshMessageFork(
    private val request: (JSONObject, (JSONObject?, DshRpcError?) -> Unit) -> Unit,
) {
    fun fork(
        sessionId: String,
        message: DshMessage,
        callback: (String?, DshRpcError?) -> Unit,
    ) {
        val sourceSeq = message.sourceSeq
        val failure = when {
            sessionId.isBlank() -> DshRpcError("session-missing", "请先打开会话")
            message.streaming -> DshRpcError("message-streaming", "内容生成中，请等待本轮结束后再分叉")
            sourceSeq == null || sourceSeq < 0 -> DshRpcError("message-unsynced", "消息尚未同步，请同步历史后重试")
            else -> null
        }
        if (failure != null) {
            callback(null, failure)
            return
        }
        // Host expands this event anchor to its persisted turn/end and rejects open turns.
        request(JSONObject().apply {
            put("sessionId", sessionId)
            put("atSeq", sourceSeq)
        }) { value, error ->
            val childId = value?.optString("sessionId")?.takeIf { it.isNotBlank() && it != sessionId }
            when {
                error != null -> callback(null, error)
                childId == null -> callback(null, DshRpcError("bad-response", "Host 未返回新会话 ID"))
                else -> callback(childId, null)
            }
        }
    }
}
