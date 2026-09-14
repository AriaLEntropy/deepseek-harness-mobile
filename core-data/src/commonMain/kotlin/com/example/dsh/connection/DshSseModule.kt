package com.example.dsh.connection

import com.tencent.kuikly.core.module.Module

enum class DshSseEventKind { OPEN, FRAME, ERROR, CLOSED }

data class DshSseEvent(
    val kind: DshSseEventKind,
    val data: String = "",
    val message: String = "",
)

interface DshSseHandle { fun close() }

interface DshSseBackend {
    fun connect(url: String, token: String, onEvent: (DshSseEvent) -> Unit): DshSseHandle
}

expect fun createDshSseBackend(): DshSseBackend

/**
 * Local-mode events.mux transport. Android / iOS / JS use Ktor SSE with a
 * WebSocket fallback. HarmonyOS has no Ktor ohos_arm64 variant and uses the
 * scan tunnel instead, so its backend is a no-op.
 */
class DshSseModule : Module() {
    private val backend = createDshSseBackend()

    override fun moduleName(): String = MODULE_NAME

    fun connect(url: String, token: String = "", onEvent: (DshSseEvent) -> Unit): DshSseHandle {
        return backend.connect(url, token, onEvent)
    }

    companion object {
        const val MODULE_NAME = "DshSseModule"
    }
}
