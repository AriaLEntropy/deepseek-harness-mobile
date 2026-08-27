package com.example.dsh.dsh

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

internal actual fun createDshHttpClient(): HttpClient = HttpClient(OkHttp) {
    installDshSseClient()
    engine {
        config {
            connectTimeout(10, TimeUnit.SECONDS)
            readTimeout(0, TimeUnit.MILLISECONDS)
            pingInterval(20, TimeUnit.SECONDS)
        }
    }
}
