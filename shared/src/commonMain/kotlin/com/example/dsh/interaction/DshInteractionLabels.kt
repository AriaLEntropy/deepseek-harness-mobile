package com.example.dsh.interaction

internal fun interactionFailureLabel(reason: String): String = when (reason) {
    "not-pending" -> "这个问题已经失效，请等 Agent 重新提问"
    "bad-response" -> "提交未被接受，请再选一次后重试"
    "缺少请求编号" -> "这个问题已失效，请等 Agent 重新提问"
    "连接尚未就绪" -> "连接尚未就绪，请稍后再试"
    else -> reason.ifEmpty { "提交失败，请重试" }
}
