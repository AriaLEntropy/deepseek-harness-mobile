package com.example.dsh.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createDshHttpClient(): HttpClient = HttpClient(Js) {
    installDshSseClient()
}
