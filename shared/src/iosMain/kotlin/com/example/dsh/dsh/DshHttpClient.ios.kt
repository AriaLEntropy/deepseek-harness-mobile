package com.example.dsh.dsh

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createDshHttpClient(): HttpClient = HttpClient(Darwin) {
    installDshSseClient()
}
