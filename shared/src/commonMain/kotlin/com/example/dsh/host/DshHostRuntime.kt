package com.example.dsh.host

import com.example.dsh.log.DshRpcLog
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.host.DshDownlinkFrame
import com.example.dsh.host.DshEventStream
import com.example.dsh.host.DshHostRuntimePhase
import com.example.dsh.host.DshHostRuntimeState
import com.example.dsh.host.DshRawSessionEvent
import com.example.dsh.host.DshRpcError
import com.example.dsh.host.DshWebSocketEvent
import com.example.dsh.host.DshWebSocketEventKind
import com.example.dsh.host.DshWebSocketHandle
import com.example.dsh.host.DshWebSocketModule
import com.example.dsh.host.DshHostConnection
import com.example.dsh.host.DshHostProtocol
import com.example.dsh.host.parseRespondReceipt
import com.tencent.kuikly.core.timer.setTimeout

internal data class DshRpcCall(
    val rpcId: String,
    private val cancelAction: () -> Unit = {},
) {
    fun cancel() = cancelAction()
}

private data class QueuedRpc(
    val generation: Long,
    val method: String,
    val payload: JSONObject,
    val rpcId: String,
    val callback: (JSONObject?, DshRpcError?, String) -> Unit,
)

/** Owns one long-lived mux/host WebSocket connection generation. */
internal class DshHostConnectionRuntime(
    private val network: NetworkModule,
    private val webSocket: DshWebSocketModule,
    private val connection: DshHostConnection,
    private val pagerId: String,
    private val onFrame: (DshDownlinkFrame) -> Unit,
    private val onState: (DshHostRuntimeState) -> Unit = {},
    private val onWorkspaceBaseline: (JSONObject) -> Unit = {},
    private val onSessionBaseline: (JSONObject) -> Unit = {},
    private val onQueueSnapshot: (String) -> Unit = {},
    private val onJobsSnapshot: (String) -> Unit = {},
    private val onSessionStatus: (String, Boolean) -> Unit = { _, _ -> },
    private val onSessionEvent: (String, DshRawSessionEvent) -> Unit = { _, _ -> },
    private val onRemoteEvent: (String) -> Unit = {},
    private val onProjection: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
) {
    private var generation = 0L
    private var rpcSequence = 0L
    private var muxOpen = false
    private var hostOpen = false
    private var hostDescribed = false
    private var productReady = false
    private var stopped = false
    private var starting = false
    private var muxHandle: DshWebSocketHandle? = null
    private var hostHandle: DshWebSocketHandle? = null
    private val bufferedFrames = mutableListOf<DshDownlinkFrame>()
    private val queued = mutableListOf<QueuedRpc>()
    private val rpcLog = DshRpcLog()

    init { start() }

    fun currentState(): DshHostRuntimeState = DshHostRuntimeState(
        phase = when {
            stopped -> DshHostRuntimePhase.STOPPED
            productReady -> DshHostRuntimePhase.READY
            hostDescribed -> DshHostRuntimePhase.SYNCING
            muxOpen || hostOpen -> DshHostRuntimePhase.HOST_HANDSHAKE
            starting -> DshHostRuntimePhase.CONNECTING
            else -> DshHostRuntimePhase.DISCONNECTED
        },
        generation = generation,
        muxOpen = muxOpen,
        hostOpen = hostOpen,
    )

    fun start() {
        if (stopped || starting || productReady) return
        starting = true
        generation += 1
        val myGeneration = generation
        muxOpen = false
        hostOpen = false
        hostDescribed = false
        productReady = false
        bufferedFrames.clear()
        publish(DshHostRuntimePhase.CONNECTING, "正在打开 DSH 事件流")
        muxHandle = webSocket.connect(webSocketUrl(DshHostProtocol.MUX_EVENTS_PATH), connection.token) { event ->
            handleSocketEvent(myGeneration, DshEventStream.MUX, event)
        }
        hostHandle = webSocket.connect(webSocketUrl(DshHostProtocol.HOST_EVENTS_PATH), connection.token) { event ->
            handleSocketEvent(myGeneration, DshEventStream.HOST, event)
        }
    }

    fun stop() {
        rpcLog.cancelAll("连接已停止")
        stopped = true
        generation += 1
        starting = false
        productReady = false
        bufferedFrames.clear()
        queued.clear()
        muxHandle?.close()
        hostHandle?.close()
        muxHandle = null
        hostHandle = null
        publish(DshHostRuntimePhase.STOPPED, "连接已停止")
    }

    fun call(
        method: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?, String) -> Unit,
    ): DshRpcCall {
        val myGeneration = generation
        val rpcId = nextRpcId(myGeneration)
        val request = QueuedRpc(myGeneration, method, payload, rpcId, callback)
        if (stopped) callback(null, DshRpcError("cancelled", "连接已停止"), rpcId)
        else if (productReady) dispatch(request)
        else queued += request
        return DshRpcCall(rpcId) { queued.removeAll { it.rpcId == rpcId } }
    }

    /**
     * POST /api/respond has a ClientResponse body, not a unary RPC body.
     * [ok]=false 时按原版协议发送 error 信封（error.code 如 "cancelled"），
     * 供 apiproxy 的 respond 路由识别「用户取消」等语义响应。
     */
    fun respond(
        rpcId: String,
        value: JSONObject,
        callback: (Boolean, String) -> Unit,
        ok: Boolean = true,
        errorCode: String = "",
        errorMessage: String = "",
    ) {
        val sessionId = value.optString("sessionId").takeIf { it.isNotEmpty() }
        if (rpcId.isEmpty()) {
            DshStreamLog.log(LogLevel.WARN, "app.respond.failed", "reason=empty-rpcId", sessionId)
            callback(false, "缺少请求编号")
            return
        }
        if (!productReady) {
            DshStreamLog.log(LogLevel.WARN, "app.respond.failed", "reason=not-ready", sessionId, rpcId)
            callback(false, "连接尚未就绪")
            return
        }
        val myGeneration = generation
        val body = JSONObject().apply {
            put("type", "client-response")
            put("rpcId", rpcId)
            put("result", JSONObject().apply {
                put("ok", ok)
                if (ok) {
                    put("value", value)
                } else {
                    put("error", JSONObject().apply {
                        put("code", errorCode)
                        put("message", errorMessage)
                        put("details", JSONObject())
                    })
                }
            })
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        network.httpRequest(
            "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.RESPOND_PATH}", true, body, headers, null, REQUEST_TIMEOUT_SECONDS,
        ) { data, success, errorMsg, response ->
            if (stopped || myGeneration != generation) {
                DshStreamLog.log(LogLevel.WARN, "app.respond.failed", "reason=generation-cancelled", sessionId, rpcId)
                callback(false, "generation-cancelled")
                return@httpRequest
            }
            if (!success) {
                DshStreamLog.log(LogLevel.ERROR, "app.respond.failed",
                    "status=${response.statusCode ?: 0} error='${DshStreamLog.preview(errorMsg)}'", sessionId, rpcId)
                callback(false, "respond failed (${response.statusCode ?: 0}): $errorMsg")
                return@httpRequest
            }
            val (accepted, reason) = parseRespondReceipt(data)
            DshStreamLog.log(if (accepted) LogLevel.INFO else LogLevel.WARN,
                if (accepted) "app.respond.complete" else "app.respond.failed",
                "action=${if (ok) "answer" else "cancel"} reason='${DshStreamLog.preview(reason)}' status=${response.statusCode ?: 0}",
                sessionId, rpcId)
            callback(accepted, reason)
        }
    }

    private fun handleSocketEvent(myGeneration: Long, stream: DshEventStream, event: DshWebSocketEvent) {
        if (stopped || myGeneration != generation) return
        when (event.kind) {
            DshWebSocketEventKind.OPEN -> {
                if (stream == DshEventStream.MUX) muxOpen = true else hostOpen = true
                publish(DshHostRuntimePhase.HOST_HANDSHAKE, "事件流已连接")
                if (muxOpen && hostOpen && !hostDescribed) describeHost(myGeneration)
            }
            DshWebSocketEventKind.FRAME -> {
                bufferedFrames += DshDownlinkFrame(myGeneration, stream, event.data)
                if (productReady) flushFrames()
            }
            DshWebSocketEventKind.ERROR, DshWebSocketEventKind.CLOSED -> invalidateGeneration(
                myGeneration, event.message.ifEmpty { "DSH 事件流已断开" },
            )
        }
    }

    private fun describeHost(myGeneration: Long) {
        directCall(myGeneration, DshHostProtocol.HOST_DESCRIBE, JSONObject()) { value, error ->
            if (myGeneration != generation || stopped) return@directCall
            if (error != null || value == null) {
                invalidateGeneration(myGeneration, error?.message ?: "host.describe 失败")
                return@directCall
            }
            hostDescribed = true
            publish(DshHostRuntimePhase.SYNCING, "正在同步远程会话")
            var workspaceDone = false
            var sessionDone = false
            var baselineError: DshRpcError? = null
            fun finishBaseline() {
                if (!workspaceDone || !sessionDone) return
                if (baselineError != null) {
                    invalidateGeneration(myGeneration, baselineError?.message ?: "同步基线失败")
                    return
                }
                productReady = true
                starting = false
                flushFrames()
                publish(DshHostRuntimePhase.READY, "DSH 已就绪")
                val pending = queued.toList()
                queued.clear()
                pending.filter { it.generation == myGeneration }.forEach(::dispatch)
            }
            directCall(myGeneration, DshHostProtocol.WORKSPACE_LIST, JSONObject()) { workspaceValue, errorValue ->
                workspaceDone = true
                if (errorValue != null) baselineError = errorValue
                if (errorValue == null && workspaceValue != null) onWorkspaceBaseline(workspaceValue)
                finishBaseline()
            }
            directCall(myGeneration, DshHostProtocol.SESSION_LIST, JSONObject()) { sessionValue, errorValue ->
                sessionDone = true
                if (errorValue != null) baselineError = errorValue
                if (errorValue == null && sessionValue != null) onSessionBaseline(sessionValue)
                finishBaseline()
            }
        }
    }

    private fun flushFrames() {
        if (!productReady) return
        val frames = bufferedFrames.toList()
        bufferedFrames.clear()
        frames.forEach(onFrame)
    }

    private fun invalidateGeneration(myGeneration: Long, message: String) {
        if (stopped || myGeneration != generation) return
        rpcLog.cancelAll(message)
        generation += 1
        val reconnectGeneration = generation
        muxHandle?.close()
        hostHandle?.close()
        muxHandle = null
        hostHandle = null
        muxOpen = false
        hostOpen = false
        hostDescribed = false
        productReady = false
        starting = false
        bufferedFrames.clear()
        val cancelled = queued.filter { it.generation == myGeneration }
        queued.removeAll { it.generation == myGeneration }
        cancelled.forEach { request ->
            request.callback(null, DshRpcError("generation-cancelled", message), request.rpcId)
        }
        publish(DshHostRuntimePhase.RECONNECTING, message)
        setTimeout(pagerId, RECONNECT_DELAY_MS) {
            if (!stopped && generation == reconnectGeneration) start()
        }
    }

    private fun dispatch(original: QueuedRpc) {
        var httpStatus: Int? = null
        val request = original.copy(callback = { value, error, rpcId ->
            rpcLog.finish(rpcId, error?.code, error?.message.orEmpty(), httpStatus)
            original.callback(value, error, rpcId)
        })
        if (request.generation != generation || stopped) return
        rpcLog.start(request.rpcId, request.method, request.payload.optString("sessionId").takeIf { it.isNotEmpty() })
        val body = JSONObject().apply {
            put("type", "client-request")
            put("rpcId", request.rpcId)
            put("method", request.method)
            put("payload", request.payload)
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        try { network.httpRequest(
            "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.API_PREFIX}/${request.method}",
            true, body, headers, null, REQUEST_TIMEOUT_SECONDS,
        ) { data, success, errorMsg, response ->
            httpStatus = response.statusCode
            if (request.generation != generation || stopped) {
                request.callback(null, DshRpcError("generation-cancelled", "请求所属连接世代已失效"), request.rpcId)
                return@httpRequest
            }
            if (!success) {
                request.callback(null, DshRpcError(
                    "transport-${response.statusCode ?: 0}",
                    "${request.method} failed (${response.statusCode ?: 0}): $errorMsg",
                ), request.rpcId)
                return@httpRequest
            }
            val result = data.optJSONObject("result")
            if (result == null) {
                request.callback(null, DshRpcError("bad-response", "${request.method} 返回了非法 RPC 信封"), request.rpcId)
                return@httpRequest
            }
            if (!result.optBoolean("ok")) {
                val error = result.optJSONObject("error")
                request.callback(null, DshRpcError(
                    error?.optString("code").orEmpty().ifEmpty { "internal" },
                    error?.optString("message").orEmpty().ifEmpty { "${request.method} 失败" },
                    error?.optJSONObject("details")?.toString() ?: "{}",
                ), request.rpcId)
                return@httpRequest
            }
            request.callback(result.optJSONObject("value"), null, request.rpcId)
        } } catch (error: Exception) {
            request.callback(null, DshRpcError("transport-exception", error.message ?: "请求发送失败"), request.rpcId)
        }
    }

    private fun directCall(myGeneration: Long, method: String, payload: JSONObject, callback: (JSONObject?, DshRpcError?) -> Unit) {
        if (myGeneration != generation || stopped) return
        dispatch(QueuedRpc(myGeneration, method, payload, nextRpcId(myGeneration)) { value, error, _ -> callback(value, error) })
    }

    private fun webSocketUrl(path: String): String {
        val base = connection.baseUrl.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://") -> "wss://${base.removePrefix("https://")}"
            base.startsWith("http://") -> "ws://${base.removePrefix("http://")}"
            else -> base
        }
        return "$wsBase$path"
    }

    private fun nextRpcId(myGeneration: Long): String = "dsh-g${myGeneration}-${++rpcSequence}"

    private fun publish(phase: DshHostRuntimePhase, message: String) {
        onState(DshHostRuntimeState(phase, generation, muxOpen, hostOpen, message))
    }

    /**
     * 调用 DSH 插件 HTTP 端点。
     * 插件端点不在标准 RPC 路径 /api/{method} 下，所以不走 call() 方法，
     * 而是直接发 HTTP POST 到插件注册的端点。
     */
    fun callPlugin(
        endpoint: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = callPluginPath("/api/session-manager/$endpoint", payload, callback)

    /** 直接 POST 到插件自有的绝对路径（如 `/api/mobile-attachment/v1/upload`）。 */
    fun callPluginPath(
        path: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        if (stopped) {
            callback(null, DshRpcError("connection-expired", "Connection is closed"))
            return
        }
        val myGeneration = generation
        val url = "${connection.baseUrl.trimEnd('/')}$path"
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        network.httpRequest(url, true, payload, headers, null, REQUEST_TIMEOUT_SECONDS) { data, success, errorMsg, _ ->
            if (stopped || myGeneration != generation) {
                callback(null, DshRpcError("generation-cancelled", "Connection generation changed"))
                return@httpRequest
            }
            if (!success) {
                callback(null, DshRpcError("network-error", "Plugin request failed: $errorMsg"))
                return@httpRequest
            }
            try {
                if (data?.optBoolean("ok", false) == true) {
                    callback(data, null)
                } else {
                    val err = data?.optJSONObject("error")
                    val code = err?.optString("code", "unknown") ?: "unknown"
                    val message = err?.optString("message", "Unknown error") ?: "Unknown error"
                    callback(null, DshRpcError(code, message))
                }
            } catch (e: Exception) {
                callback(null, DshRpcError("parse-error", "Failed to parse plugin response: ${e.message}"))
            }
        }
    }

    /** Versioned mobile bridge, distinct from official unary RPC and Typert Remote. */
    fun loadPluginInventory(callback: (JSONObject?, DshRpcError?) -> Unit) {
        if (stopped) { callback(null, DshRpcError("connection-expired", "请先连接 Host")); return }
        val expected = generation
        val headers = JSONObject().apply {
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        network.httpRequest("${connection.baseUrl.trimEnd('/')}/api/mobile-plugin-inventory/v1/list",
            false, JSONObject(), headers, null, REQUEST_TIMEOUT_SECONDS) { data, success, error, response ->
            if (stopped || expected != generation) {
                callback(null, DshRpcError("generation-cancelled", "连接已变化，请刷新插件列表"))
            } else if (response.statusCode == 404) {
                callback(null, DshRpcError("unsupported", "Host 尚未安装 dsh-mobile-plugin-inventory，请安装项目 host-plugin 中的只读桥接插件"))
            } else if (!success || !data.optBoolean("ok")) {
                callback(null, DshRpcError("inventory-failed", data.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotEmpty() } ?: "读取插件清单失败：$error"))
            } else callback(data, null)
        }
    }

    /** Mutate one Loader entry through the bridge action endpoint: enable / disable / reload. */
    fun pluginAction(entryId: String, action: String, callback: (JSONObject?, DshRpcError?) -> Unit) {
        if (stopped) { callback(null, DshRpcError("connection-expired", "请先连接 Host")); return }
        val expected = generation
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        val body = JSONObject().apply {
            put("entryId", entryId)
            put("action", action)
        }
        network.httpRequest("${connection.baseUrl.trimEnd('/')}/api/mobile-plugin-inventory/v1/action",
            true, body, headers, null, REQUEST_TIMEOUT_SECONDS) { data, success, error, response ->
            if (stopped || expected != generation) {
                callback(null, DshRpcError("generation-cancelled", "连接已变化，请刷新插件列表"))
            } else if (response.statusCode == 404) {
                callback(null, DshRpcError("unsupported", "Host 桥接版本过旧，不支持插件启停，请更新 host-plugin"))
            } else if (!success || !data.optBoolean("ok")) {
                callback(null, DshRpcError("plugin-action-failed", data.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotEmpty() } ?: "插件操作失败：$error"))
            } else callback(data, null)
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 30
        const val RECONNECT_DELAY_MS = 1_000
    }
}

/** API facade used by the current Kuikly page while the raw timeline evolves. */
