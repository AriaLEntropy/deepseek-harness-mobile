package com.example.dsh.relay

import android.util.Log
import com.example.dsh.bus.BusChannel
import com.example.dsh.bus.BusSync
import com.example.dsh.bus.DescribeAttempts
import com.example.dsh.bus.DescribeDecision
import com.example.dsh.bus.SnapshotReceipt
import com.example.dsh.bus.busVersionAtLeast
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
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

    fun start(port: Int, token: String, notes: DshRelaySecrets, stillCurrent: () -> Boolean) {
        val describeClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val pollClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        val socketClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val saved = notes.readBus()
        val remembered = saved?.takeIf { it.kind == "agent-host" && busVersionAtLeast(it.protocolVersion) }
        val decision = remembered?.let {
            DescribeDecision.AgentHost(it.agentId, it.protocolVersion, it.forwardsDsh)
        } ?: identify(describeClient, port, token, stillCurrent)
        if (decision is DescribeDecision.Plugin) {
            notes.saveBus(BusNote("dsh-plugin", "", "", false, -1))
        }
        if (decision !is DescribeDecision.AgentHost || !stillCurrent()) return
        if (!busVersionAtLeast(decision.protocolVersion)) {
            Log.w(TAG, "请升级电脑端程序 protocolVersion=${decision.protocolVersion}")
            notes.saveBus(BusNote("agent-host", decision.agentId, decision.protocolVersion, decision.forwardsDsh, saved?.seq ?: -1))
            return
        }
        val note = AtomicReference(
            BusNote("agent-host", decision.agentId, decision.protocolVersion, decision.forwardsDsh, remembered?.seq ?: -1),
        )
        notes.saveBus(note.get())
        Log.i(TAG, "host is agent-host agentId=${decision.agentId}")
        val sync = BusSync()
        sync.bind(decision.agentId)
        remembered?.let { sync.resume(it.seq) }
        val retired = AtomicBoolean(false)
        val current = { stillCurrent() && !retired.get() }
        if (remembered != null) {
            thread(name = "agent-host-describe", isDaemon = true) {
                refresh(describeClient, port, token, notes, note, sync, retired, current)
            }
        }
        while (current()) {
            if (sync.channel == BusChannel.WebSocket) {
                if (!readSocket(socketClient, port, token, sync, notes, note, current)) sync.onHandshakeFailure()
            } else {
                val body = get(pollClient, port, token, sync.pollPath())
                if (body != null) logSnapshot(notes, note, sync.onSnapshotResponse(body))
                if (!current()) return
                Thread.sleep(1_000)
                sync.onPollTick(1_000)
            }
        }
    }

    private fun refresh(
        client: OkHttpClient,
        port: Int,
        token: String,
        notes: DshRelaySecrets,
        note: AtomicReference<BusNote>,
        sync: BusSync,
        retired: AtomicBoolean,
        stillCurrent: () -> Boolean,
    ) {
        when (val fresh = describeOnce(client, port, token, stillCurrent)) {
            is DescribeDecision.Plugin -> {
                notes.saveBus(BusNote("dsh-plugin", "", "", false, -1))
                retired.set(true)
            }
            is DescribeDecision.AgentHost -> {
                if (!busVersionAtLeast(fresh.protocolVersion)) {
                    Log.w(TAG, "请升级电脑端程序 protocolVersion=${fresh.protocolVersion}")
                    notes.saveBus(BusNote("agent-host", fresh.agentId, fresh.protocolVersion, fresh.forwardsDsh, sync.seq))
                    retired.set(true)
                    return
                }
                sync.bind(fresh.agentId)
                val updated = note.get().copy(
                    agentId = fresh.agentId,
                    protocolVersion = fresh.protocolVersion,
                    forwardsDsh = fresh.forwardsDsh,
                    seq = sync.seq,
                )
                note.set(updated)
                notes.saveBus(updated)
            }
            else -> Unit
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

    private fun describeOnce(client: OkHttpClient, port: Int, token: String, stillCurrent: () -> Boolean): DescribeDecision {
        if (!stillCurrent()) return DescribeDecision.Unknown
        val response = runCatching { getResponse(client, port, token, "/bus/describe") }.getOrNull()
            ?: return DescribeDecision.Unknown
        return DescribeAttempts().onHttp(response.code, response.body?.string().orEmpty()) ?: DescribeDecision.Unknown
    }

    private fun readSocket(
        client: OkHttpClient,
        port: Int,
        token: String,
        sync: BusSync,
        notes: DshRelaySecrets,
        note: AtomicReference<BusNote>,
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
                logSnapshot(notes, note, sync.onSocketFrame(text))
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

    private fun logSnapshot(notes: DshRelaySecrets, note: AtomicReference<BusNote>, receipt: SnapshotReceipt?) {
        if (receipt == null) return
        Log.i(TAG, "snapshot seq=${receipt.seq} tasks=${receipt.taskCount}")
        val next = note.get().copy(seq = receipt.seq)
        note.set(next)
        notes.saveBus(next)
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
