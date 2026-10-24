package com.example.dsh.relay

import com.example.dsh.bus.BusHttpResult
import com.example.dsh.bus.BusSocket
import com.example.dsh.bus.BusTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Bus transport over the existing sealed-tunnel loopback. The tunnel is already
 * authenticated, so every request only needs the local bearer token.
 */
class TunnelBusTransport(
    private val port: Int,
    private val token: String,
) : BusTransport {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun request(method: String, path: String, body: String?): BusHttpResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port$path")
                .header("Authorization", "Bearer $token")
                .apply {
                    when (method.uppercase()) {
                        "POST" -> post((body ?: "{}").toRequestBody(JSON))
                        "PUT" -> put((body ?: "").toRequestBody(JSON))
                        else -> get()
                    }
                }
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    BusHttpResult.Http(response.code, response.body?.string().orEmpty())
                }
            }.getOrElse { BusHttpResult.Failure(it.message ?: "transport failed") }
        }

    override fun openSocket(
        path: String,
        onOpen: () -> Unit,
        onFrame: (String) -> Unit,
        onFailure: (String) -> Unit,
    ): BusSocket {
        val request = Request.Builder()
            .url("http://127.0.0.1:$port$path")
            .header("Authorization", "Bearer $token")
            .build()
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onOpen.invoke()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    onFrame.invoke(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onFailure.invoke(t.message ?: "socket failed")
                }
            },
        )
        return object : BusSocket {
            override fun close() {
                socket.close(1000, "closed")
            }
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
