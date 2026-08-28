package com.example.dsh.relay

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

internal data class DshRelayNativeState(
    val phase: String,
    val message: String = "",
    val localPort: Int = 0,
    val localToken: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val relayOrigin: String = "",
    val paired: Boolean = false,
    val generation: Long = 0,
)

internal object DshRelayManager {
    private const val TAG = "DshRelay"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val listeners = CopyOnWriteArrayList<(DshRelayNativeState) -> Unit>()
    private val generation = AtomicLong(0)
    private val connecting = AtomicBoolean(false)
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private var secrets: DshRelaySecrets? = null
    private var appContext: Context? = null
    private var loopback: DshRelayLoopbackServer? = null
    private var socket: WebSocket? = null
    private var cipher: SecureCipher? = null
    private var accessSessionId = ""
    private var localToken = ""
    private var hostName = ""
    private var relayOrigin = ""
    private var hostId = ""
    @Volatile private var stopped = true
    @Volatile private var state = DshRelayNativeState("IDLE")
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun addListener(listener: (DshRelayNativeState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (DshRelayNativeState) -> Unit) {
        listeners -= listener
    }

    fun current(): DshRelayNativeState = state

    fun attach(context: Context) {
        appContext = context.applicationContext
        if (secrets == null) secrets = DshRelaySecrets(context.applicationContext)
        val stored = secrets ?: return
        if (stored.hasPairing()) {
            hostId = stored.hostId().orEmpty()
            hostName = stored.hostName().orEmpty()
            relayOrigin = stored.relayOrigin().orEmpty()
            publish(state.copy(paired = true, hostId = hostId, hostName = hostName, relayOrigin = relayOrigin, phase = if (state.phase == "READY") "READY" else "IDLE"))
        }
    }

    fun pairFromQr(context: Context, qr: String): Map<String, Any> {
        attach(context)
        publish(DshRelayNativeState("PAIRING", "正在配对"))
        return try {
            val link = parsePairQr(qr)
            val claimToken = SealedTunnelCrypto.deriveClaimToken(link.masterKey)
            val body = JSONObject()
                .put("pairId", link.pairId)
                .put("claimToken", claimToken)
                .put("deviceLabel", "Android")
                .put("platform", "android")
            val response = http.newCall(
                Request.Builder().url("${link.origin}/pair/claim-device").post(body.toString().toRequestBody(jsonMedia)).build(),
            ).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) {
                val reason = json.optString("error").ifBlank { "claim failed (${response.code})" }
                publish(DshRelayNativeState("ERROR", reason, paired = secrets?.hasPairing() == true))
                return mapOf("ok" to false, "message" to reason)
            }
            val newHostId = json.optString("hostId")
            val token = json.optString("clientToken")
            val name = json.optString("hostName").ifBlank { "电脑" }
            require(newHostId.isNotBlank() && token.isNotBlank()) { "pairing response missing credentials" }
            secrets?.save(link.masterKey, token, newHostId, name, link.origin)
            hostId = newHostId
            hostName = name
            relayOrigin = link.origin
            publish(DshRelayNativeState("IDLE", "已配对", hostId = newHostId, hostName = name, relayOrigin = link.origin, paired = true))
            mapOf("ok" to true, "hostId" to newHostId, "hostName" to name, "relayOrigin" to link.origin, "pairedAt" to System.currentTimeMillis())
        } catch (error: Exception) {
            Log.e(TAG, "pair failed", error)
            val message = error.message ?: "配对失败"
            publish(DshRelayNativeState("ERROR", message, paired = secrets?.hasPairing() == true))
            mapOf("ok" to false, "message" to message)
        }
    }

    fun connect(context: Context) {
        attach(context)
        if (loopbackListening()) {
            stopped = false
            startForeground(context)
            registerNetwork()
            Log.i(TAG, "reusing loopback port=${loopback?.port}")
            return
        }
        if (connecting.getAndSet(true)) return
        stopped = false
        startForeground(context)
        registerNetwork()
        dropLoopback("new-connect")
        publish(
            DshRelayNativeState(
                "CONNECTING",
                "正在申请访问票",
                hostId = hostId,
                hostName = hostName,
                relayOrigin = relayOrigin,
                paired = secrets?.hasPairing() == true,
                generation = generation.get(),
            ),
        )
        thread(name = "dsh-relay-connect") { connectInternal() }
    }

    fun disconnect() {
        stopped = true
        generation.incrementAndGet()
        connecting.set(false)
        unregisterNetwork()
        socket?.cancel()
        socket = null
        cipher = null
        dropLoopback("disconnect")
        stopForeground()
        publish(DshRelayNativeState("STOPPED", "扫码连接已断开", hostId = hostId, hostName = hostName, relayOrigin = relayOrigin, paired = secrets?.hasPairing() == true))
    }

    fun forget() {
        disconnect()
        secrets?.clear()
        hostId = ""
        hostName = ""
        relayOrigin = ""
        publish(DshRelayNativeState("IDLE", "已移除配对"))
    }

    private fun connectInternal() {
        val myGeneration = generation.incrementAndGet()
        try {
            val stored = secrets ?: throw IllegalStateException("missing secrets")
            val master = stored.masterKey() ?: throw IllegalStateException("not paired")
            val clientToken = stored.clientToken() ?: throw IllegalStateException("not paired")
            val origin = stored.relayOrigin().orEmpty().ifBlank { relayOrigin.ifBlank { "http://127.0.0.1:8787" } }
            hostId = stored.hostId().orEmpty()
            hostName = stored.hostName().orEmpty().ifBlank { hostName }
            relayOrigin = origin
            publish(DshRelayNativeState("CONNECTING", "正在申请访问票", hostId = hostId, hostName = hostName, relayOrigin = origin, paired = true, generation = myGeneration))
            val ticketResponse = http.newCall(
                Request.Builder().url("$origin/access-ticket").header("Authorization", "Bearer $clientToken").post("{}".toRequestBody(jsonMedia)).build(),
            ).execute()
            val ticketJson = JSONObject(ticketResponse.body?.string().orEmpty().ifBlank { "{}" })
            if (!ticketResponse.isSuccessful) throw IllegalStateException(ticketJson.optString("error").ifBlank { "ticket failed" })
            accessSessionId = ticketJson.optString("accessSessionId")
            val ticket = ticketJson.optString("ticket")
            val tunnelUrl = ticketJson.optString("tunnelUrl").ifBlank { toWs(origin) + "/client-tunnel" }
            hostId = ticketJson.optString("hostId").ifBlank { hostId }
            require(ticket.isNotBlank() && accessSessionId.isNotBlank()) { "ticket response incomplete" }
            publish(state.copy(phase = "HANDSHAKING", message = "正在建立加密隧道", generation = myGeneration, hostId = hostId, hostName = hostName, relayOrigin = origin, paired = true))
            val clientRandomB64 = SealedTunnelCrypto.encodeBase64Url(SealedTunnelCrypto.randomBytes())
            val hello = envelope(
                "client_hello",
                JSONObject()
                    .put("accessSessionId", accessSessionId)
                    .put("clientRandomB64", clientRandomB64)
                    .put("clientProofB64", SealedTunnelCrypto.clientProof(master, accessSessionId, clientRandomB64)),
            )
            val wsClient = http.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
            wsClient.newWebSocket(
                Request.Builder().url(tunnelUrl).header("Authorization", "Bearer $ticket").build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        socket = webSocket
                        webSocket.send(hello.toString())
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (stopped || myGeneration != generation.get()) return
                        handleOuter(text, master, clientRandomB64, myGeneration, origin)
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (!stopped && myGeneration == generation.get()) scheduleReconnect()
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "relay socket failed", t)
                        if (!stopped && myGeneration == generation.get()) {
                            publish(state.copy(phase = "ERROR", message = t.message ?: "隧道失败"))
                            scheduleReconnect()
                        }
                    }
                },
            )
        } catch (error: Exception) {
            Log.e(TAG, "connect failed", error)
            if (!stopped && myGeneration == generation.get()) {
                publish(DshRelayNativeState("ERROR", error.message ?: "连接失败", hostId = hostId, hostName = hostName, relayOrigin = relayOrigin, paired = true, generation = myGeneration))
                scheduleReconnect()
            }
        } finally {
            connecting.set(false)
        }
    }

    private fun handleOuter(text: String, master: String, clientRandomB64: String, myGeneration: Long, origin: String) {
        val msg = JSONObject(text)
        when (msg.optString("type")) {
            "server_hello" -> {
                val payload = msg.optJSONObject("payload") ?: return
                cipher = SealedTunnelCrypto.createClientCipher(
                    master,
                    accessSessionId,
                    clientRandomB64,
                    payload.optString("serverRandomB64"),
                    payload.optString("serverProofB64"),
                )
                startLoopback(myGeneration, origin)
            }
            "sealed" -> {
                val payload = msg.optJSONObject("payload") ?: return
                val opened = cipher?.open(SealedPayload(payload.optString("seq"), payload.optString("ciphertextB64"))) ?: return
                loopback?.onInner(opened.optString("type"), opened.optJSONObject("payload") ?: JSONObject(), opened.optString("channel"))
            }
            "device_close", "close" -> if (!stopped) scheduleReconnect()
        }
    }

    private fun startLoopback(myGeneration: Long, origin: String) {
        dropLoopback("replace")
        localToken = SealedTunnelCrypto.encodeBase64Url(SealedTunnelCrypto.randomBytes(24))
        val server = DshRelayLoopbackServer(localToken) { type, payload, channel -> sendInner(type, payload, channel) }
        val port = server.start()
        loopback = server
        Log.i(TAG, "loopback listening port=$port")
        publish(DshRelayNativeState("READY", "扫码隧道已连接", localPort = port, localToken = localToken, hostId = hostId, hostName = hostName, relayOrigin = origin, paired = true, generation = myGeneration))
    }

    private fun loopbackListening(): Boolean {
        val server = loopback ?: return false
        return state.phase == "READY" &&
            state.localPort > 0 &&
            server.port == state.localPort &&
            server.isListening()
    }

    private fun dropLoopback(reason: String) {
        val port = loopback?.port ?: 0
        loopback?.stop()
        loopback = null
        localToken = ""
        if (port > 0) Log.i(TAG, "loopback stopped port=$port reason=$reason")
    }

    private fun sendInner(type: String, payload: JSONObject, channel: String) {
        val current = cipher ?: return
        val sealed = current.seal(envelope(type, payload, channel))
        socket?.send(
            envelope(
                "sealed",
                JSONObject().put("accessSessionId", accessSessionId).put("seq", sealed.seq).put("ciphertextB64", sealed.ciphertextB64),
            ).toString(),
        )
    }

    private fun envelope(type: String, payload: JSONObject, channel: String? = null): JSONObject =
        JSONObject().put("v", 1).put("type", type).apply { if (channel != null) put("channel", channel) }.put("id", UUID.randomUUID().toString()).put("ts", System.currentTimeMillis()).put("payload", payload)

    private fun scheduleReconnect() {
        if (stopped) return
        dropLoopback("reconnect")
        publish(
            state.copy(
                phase = "RECONNECTING",
                message = "扫码连接重试中",
                localPort = 0,
                localToken = "",
            ),
        )
        thread(name = "dsh-relay-retry") {
            Thread.sleep(2_000)
            if (!stopped && connecting.compareAndSet(false, true)) connectInternal()
        }
    }

    private fun parsePairQr(raw: String): PairLink {
        val uri = Uri.parse(raw.trim())
        val fragment = uri.fragment.orEmpty()
        val query = fragment.substringAfter('?', "").ifBlank { uri.query.orEmpty() }
        val params = query.split('&').associate { part ->
            val idx = part.indexOf('=')
            if (idx < 0) part to ""
            else Uri.decode(part.substring(0, idx)) to Uri.decode(part.substring(idx + 1))
        }
        val id = params["id"].orEmpty()
        val key = params["key"].orEmpty()
        require(id.isNotBlank() && key.isNotBlank()) { "二维码缺少配对参数" }
        return PairLink("${uri.scheme}://${uri.authority}", id, key)
    }

    private fun toWs(origin: String): String =
        if (origin.startsWith("https:")) origin.replaceFirst("https:", "wss:") else origin.replaceFirst("http:", "ws:")

    private fun startForeground(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, DshRelayForegroundService::class.java)
        val start: () -> Unit = {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
            Unit
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) start()
        else android.os.Handler(android.os.Looper.getMainLooper()).post(start)
    }

    private fun stopForeground() {
        appContext?.stopService(Intent(appContext, DshRelayForegroundService::class.java))
    }

    private fun registerNetwork() {
        val context = appContext ?: return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivity = manager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!stopped && state.phase != "READY" && state.phase != "CONNECTING" && state.phase != "HANDSHAKING") {
                    thread(name = "dsh-relay-net") {
                        if (connecting.compareAndSet(false, true)) connectInternal()
                    }
                }
            }
        }
        networkCallback = callback
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    private fun unregisterNetwork() {
        networkCallback?.let { callback -> runCatching { connectivity?.unregisterNetworkCallback(callback) } }
        networkCallback = null
    }

    private fun publish(next: DshRelayNativeState) {
        state = next
        listeners.forEach { listener -> runCatching { listener(next) } }
    }

    private data class PairLink(val origin: String, val pairId: String, val masterKey: String)
}
