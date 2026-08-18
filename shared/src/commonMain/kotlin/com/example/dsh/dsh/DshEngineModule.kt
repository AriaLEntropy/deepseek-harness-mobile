package com.example.dsh.dsh

import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal enum class DshEnginePhase {
    IDLE,
    PREPARING,
    STARTING,
    READY,
    ERROR,
    STOPPED,
    UNSUPPORTED,
}

internal data class DshEngineState(
    val phase: DshEnginePhase,
    val progress: Int = 0,
    val message: String = "",
)

/** Cross-platform owner of the native embedded-Harness bridge. */
internal class DshEngineModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    fun start(onState: (DshEngineState) -> Unit) {
        toNative(
            keepCallbackAlive = true,
            methodName = "start",
            param = null,
            callback = { value ->
                val phase = runCatching {
                    DshEnginePhase.valueOf(value?.optString("phase") ?: "ERROR")
                }.getOrDefault(DshEnginePhase.ERROR)
                onState(DshEngineState(
                    phase = phase,
                    progress = value?.optInt("progress") ?: 0,
                    message = value?.optString("message").orEmpty(),
                ))
            },
            syncCall = false,
        )
    }

    fun status(): DshEngineState {
        val raw = toNative(false, "status", null, null, true).toString()
        val value = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val phase = runCatching {
            DshEnginePhase.valueOf(value.optString("phase", "ERROR"))
        }.getOrDefault(DshEnginePhase.ERROR)
        return DshEngineState(phase, value.optInt("progress"), value.optString("message"))
    }

    fun stop() {
        toNative(false, "stop", null, null, false)
    }

    companion object {
        const val MODULE_NAME = "DshEngineModule"
    }
}
