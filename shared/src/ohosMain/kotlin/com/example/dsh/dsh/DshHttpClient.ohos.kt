package com.example.dsh.dsh

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createDshHttpClient(): HttpClient = HttpClient(CIO) {
    installDshSseClient()
}
