package com.example.dsh.dsh

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets

internal expect fun createDshHttpClient(): HttpClient

internal fun HttpClientConfig<*>.installDshSseClient() {
    install(SSE) {
        maxReconnectionAttempts = 0
    }
    install(WebSockets)
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
    }
}

private const val CONNECT_TIMEOUT_MS = 10_000L
