package com.example.dsh.message

/** 消息行的稳定 key：会话内消息 id 唯一。 */
internal fun messageRowKey(sessionId: String, messageId: String): String = "$sessionId:$messageId"
