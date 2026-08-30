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
import io.ktor.client.engine.darwin.Darwin

internal actual fun createDshHttpClient(): HttpClient = HttpClient(Darwin) {
    installDshSseClient()
}
