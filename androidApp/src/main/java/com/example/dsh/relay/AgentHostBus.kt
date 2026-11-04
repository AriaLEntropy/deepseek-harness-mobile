package com.example.dsh.relay

import android.util.Log
import com.example.dsh.bus.BusChannel
import com.example.dsh.bus.BusStore
import com.example.dsh.bus.BusSync
import com.example.dsh.bus.DescribeAttempts
import com.example.dsh.bus.DescribeDecision
import com.example.dsh.bus.SnapshotReceipt
import com.example.dsh.bus.busParse
import com.example.dsh.bus.busSkillListKey
import com.example.dsh.bus.busVersionAtLeast
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    @Volatile
    private var latestHomeJson: String? = null

    @Volatile
    private var latestHostKind: String = ""

    @Volatile
    private var commandPort: Int = 0

    @Volatile
    private var commandToken: String = ""

    /** taskId -> host `taskMessages` payload JSON, updated from /bus/ws events. */
    @Volatile
    private var messagesByTask: Map<String, String> = emptyMap()

    /** busSkillListKey -> latest `skillList` payload JSON. */
    @Volatile
    private var skillListsByKey: Map<String, String> = emptyMap()

    /** skillId -> latest `skillContent` payload JSON. */
    @Volatile
    private var skillContentsById: Map<String, String> = emptyMap()

    fun busHomeJson(): String? = latestHomeJson

    fun busHostKind(): String = latestHostKind

    fun taskMessagesJson(taskId: String): String? = messagesByTask[taskId]

    fun skillListJson(key: String): String? = skillListsByKey[key]

    fun skillContentJson(skillId: String): String? = skillContentsById[skillId]

    /** Clear cached snapshot/commands when the relay is disconnected or forgotten. */
    fun clear() {
        latestHomeJson = null
        latestHostKind = ""
        commandPort = 0
        commandToken = ""
        messagesByTask = emptyMap()
        skillListsByKey = emptyMap()
        skillContentsById = emptyMap()
    }

    fun command(json: String): BusCommandAck? {
        val port = commandPort
        val token = commandToken
        if (port <= 0 || token.isEmpty()) return null
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/bus/commands")
            .header("Authorization", "Bearer $token")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return runCatching {
            OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    BusCommandAck(response.code, response.body?.string().orEmpty())
                }
        }.getOrNull()
    }

    fun start(port: Int, token: String, notes: DshRelaySecrets, stillCurrent: () -> Boolean) {
        latestHomeJson = null
        latestHostKind = ""
        commandPort = port
        commandToken = token
        messagesByTask = emptyMap()
        skillListsByKey = emptyMap()
        skillContentsById = emptyMap()
        val describeClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val pollClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        val socketClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        // bus note 只是缓存：Keystore 轮换/文件损坏时不能中断整条 bus 通道。
        val saved = runCatching { notes.readBus() }.getOrNull()
        val remembered = saved?.takeIf { it.kind == "agent-host" && busVersionAtLeast(it.protocolVersion) }
        val decision = remembered?.let {
            DescribeDecision.AgentHost(it.agentId, it.protocolVersion, it.forwardsDsh)
        } ?: identify(describeClient, port, token, stillCurrent)
        latestHostKind = when (decision) {
            is DescribeDecision.AgentHost -> "agent-host"
            DescribeDecision.Plugin -> "dsh-plugin"
            DescribeDecision.Unknown -> "unknown"
        }
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
        val homeStore = BusStore()
        homeStore.bindDescribe(decision)
        val sync = BusSync()
        sync.bind(decision.agentId)
        // 不能直接用持久化的 seq 续传：手机没有持久化完整 snapshot，
        // 若 since 等于电脑当前 seq，服务端不会补发快照，页面会一直空着。
        // 重连由本次进程内的 sync.seq 负责，进程重启则重新拉全量。
        val retired = AtomicBoolean(false)
        val current = { stillCurrent() && !retired.get() }
        if (remembered != null) {
            thread(name = "agent-host-describe", isDaemon = true) {
                refresh(describeClient, port, token, notes, note, sync, retired, current)
            }
        }
        while (current()) {
            if (sync.channel == BusChannel.WebSocket) {
                if (!readSocket(socketClient, port, token, sync, notes, note, homeStore, current)) sync.onHandshakeFailure()
            } else {
                val body = get(pollClient, port, token, sync.pollPath())
                if (body != null) {
                    logSnapshot(notes, note, sync.onSnapshotResponse(body))
                    applySnapshotBody(homeStore, body)
                }
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
        homeStore: BusStore,
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
                applySocketFrame(homeStore, text)
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

    private fun applySnapshotBody(store: BusStore, body: String) {
        runCatching {
            store.applySnapshot(busParse(body))
            latestHomeJson = store.snapshotJson()
        }
    }

    private fun applySocketFrame(store: BusStore, text: String) {
        runCatching {
            val frame = busParse(text).jsonObject
            when ((frame["type"] as? JsonPrimitive)?.contentOrNull) {
                "snapshot" -> frame["snapshot"]?.let { store.applySnapshot(it) }
                "event" -> {
                    val event = frame["event"] as? JsonObject
                    if (event != null) {
                        store.applyEvent(event)
                        when (event.string("kind")) {
                            "taskMessages" -> {
                                val payload = event["taskMessages"] as? JsonObject
                                val taskId = payload?.string("taskId").orEmpty()
                                if (taskId.isNotEmpty()) {
                                    messagesByTask = messagesByTask + (taskId to payload.toString())
                                }
                            }
                            "skillList" -> {
                                val payload = event["skillList"] as? JsonObject
                                if (payload != null) {
                                    val key = busSkillListKey(
                                        connector = payload.string("connector").orEmpty(),
                                        connectorId = payload.string("connectorId"),
                                        projectPath = payload.string("projectPath").orEmpty(),
                                        taskId = payload.string("taskId").orEmpty(),
                                    )
                                    skillListsByKey = skillListsByKey + (key to payload.toString())
                                }
                            }
                            "skillContent" -> {
                                val payload = event["skillContent"] as? JsonObject
                                val skillId = payload?.string("skillId").orEmpty()
                                if (skillId.isNotEmpty()) {
                                    skillContentsById = skillContentsById + (skillId to payload.toString())
                                }
                            }
                        }
                    }
                }
            }
            latestHomeJson = store.snapshotJson()
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

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

internal data class BusCommandAck(val status: Int, val body: String)
