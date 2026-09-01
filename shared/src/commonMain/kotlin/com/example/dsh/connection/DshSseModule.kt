package com.example.dsh.connection

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.module.Module

internal enum class DshSseEventKind { OPEN, FRAME, ERROR, CLOSED }

internal data class DshSseEvent(
    val kind: DshSseEventKind,
    val data: String = "",
    val message: String = "",
)

internal interface DshSseHandle { fun close() }

internal interface DshSseBackend {
    fun connect(url: String, token: String, onEvent: (DshSseEvent) -> Unit): DshSseHandle
}

internal expect fun createDshSseBackend(): DshSseBackend

/**
 * Local-mode events.mux transport. Android / iOS / JS use Ktor SSE with a
 * WebSocket fallback. HarmonyOS has no Ktor ohos_arm64 variant and uses the
 * scan tunnel instead, so its backend is a no-op.
 */
internal class DshSseModule : Module() {
    private val backend = createDshSseBackend()

    override fun moduleName(): String = MODULE_NAME

    fun connect(url: String, token: String = "", onEvent: (DshSseEvent) -> Unit): DshSseHandle {
        return backend.connect(url, token, onEvent)
    }

    companion object {
        const val MODULE_NAME = "DshSseModule"
    }
}
