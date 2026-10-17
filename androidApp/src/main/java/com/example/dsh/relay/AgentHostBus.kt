package com.example.dsh.relay

import android.util.Log
import com.example.dsh.bus.BusChannel
import com.example.dsh.bus.BusSync
import com.example.dsh.bus.DescribeAttempts
import com.example.dsh.bus.DescribeDecision
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * After the existing tunnel is up, ask /bus/describe. A 404 keeps the current plugin path.
 * agent-host then follows /bus/ws, falling back to the snapshot long poll.
 */
internal object AgentHostBus {
    private const val TAG = "AgentHostBus"

    fun start(port: Int, token: String, stillCurrent: () -> Boolean) {
        val describeClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val pollClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        val socketClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val decision = identify(describeClient, port, token, stillCurrent)
        if (decision !is DescribeDecision.AgentHost || !stillCurrent()) return
        Log.i(TAG, "host is agent-host agentId=${decision.agentId}")
        val sync = BusSync()
        sync.bind(decision.agentId)
        while (stillCurrent()) {
            if (sync.channel == BusChannel.WebSocket) {
                if (!readSocket(socketClient, port, token, sync, stillCurrent)) sync.onHandshakeFailure()
            } else {
                val body = get(pollClient, port, token, sync.pollPath())
                if (body != null) sync.onSnapshotResponse(body)
                if (!stillCurrent()) return
                Thread.sleep(1_000)
                sync.onPollTick(1_000)
            }
        }
    }

    private fun identify(client: OkHttpClient, port: Int, token: String, stillCurrent: () -> Boolean): DescribeDecision {
        val attempts = DescribeAttempts()
        while (stillCurrent()) {
            val response = runCatching { getResponse(client, port, token, "/bus/describe") }.getOrNull()
            val decision = if (response == null) {
                attempts.onTransportFailure()
            } else {
                attempts.onHttp(response.code, response.body?.string().orEmpty())
            }
            if (decision != null) return decision
        }
        return DescribeDecision.Unknown
    }

    private fun readSocket(
        client: OkHttpClient,
        port: Int,
        token: String,
        sync: BusSync,
        stillCurrent: () -> Boolean,
    ): Boolean {
        val request = Request.Builder()
            .url("http://127.0.0.1:$port${sync.socketPath()}")
            .header("Authorization", "Bearer $token")
            .build()
        val opened = AtomicBoolean(false)
        val failed = AtomicBoolean(false)
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                sync.onHandshakeOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                sync.onSocketFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failed.set(true)
            }
        })
        val startedAt = System.currentTimeMillis()
        while (stillCurrent() && !failed.get() && sync.channel == BusChannel.WebSocket) {
            if (!opened.get() && System.currentTimeMillis() - startedAt > 10_000) {
                failed.set(true)
                break
            }
            Thread.sleep(200)
        }
        socket.cancel()
        return opened.get() && !failed.get()
    }

    private fun get(client: OkHttpClient, port: Int, token: String, path: String): String? {
        return runCatching { getResponse(client, port, token, path)?.body?.string() }.getOrNull()
    }

    private fun getResponse(client: OkHttpClient, port: Int, token: String, path: String): Response? {
        val request = Request.Builder()
            .url("http://127.0.0.1:$port$path")
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute()
    }
}
