package com.example.dsh.diagnostics

/**
 * 日志页与主页之间的路由/通知契约。
 *
 * 日志只记录脱敏元数据，跳转会话只需要 sessionId；这里不承载任何事件原文。
 */
internal object DshLogPageContract {
    const val EVENT_JUMP_TO_SESSION = "dsh.log.jump-to-session"
    const val EVENT_JUMP_RESULT = "dsh.log.jump-result"
    const val KEY_OWNER = "logOwnerPagerId"
    const val KEY_REQUEST = "requestId"

    const val KEY_SESSION_ID = "sessionId"
    const val KEY_SESSION_TITLE = "title"
    const val KEY_SESSION_TITLES = "sessionTitles"
}
