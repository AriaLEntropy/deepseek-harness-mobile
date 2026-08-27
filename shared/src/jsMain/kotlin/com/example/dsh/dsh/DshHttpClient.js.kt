package com.example.dsh.dsh

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

internal actual fun createDshHttpClient(): HttpClient = HttpClient(Js) {
    installDshSseClient()
}
