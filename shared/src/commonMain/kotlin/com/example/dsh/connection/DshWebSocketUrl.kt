package com.example.dsh.connection

internal fun dshWebSocketUrl(httpUrl: String): String = when {
    httpUrl.startsWith("https://") -> "wss://${httpUrl.removePrefix("https://")}"
    httpUrl.startsWith("http://") -> "ws://${httpUrl.removePrefix("http://")}"
    else -> httpUrl
}
