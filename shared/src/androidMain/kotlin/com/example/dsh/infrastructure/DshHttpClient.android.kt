package com.example.dsh.infrastructure

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
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
