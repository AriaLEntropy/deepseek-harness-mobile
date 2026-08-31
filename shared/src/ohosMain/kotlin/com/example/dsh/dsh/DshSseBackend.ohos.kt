package com.example.dsh.dsh

internal actual fun createDshSseBackend(): DshSseBackend = DshSseUnsupportedBackend()

private class DshSseUnsupportedBackend : DshSseBackend {
    override fun connect(url: String, token: String, onEvent: (DshSseEvent) -> Unit): DshSseHandle {
        onEvent(
            DshSseEvent(
                DshSseEventKind.ERROR,
                message = "HarmonyOS 使用扫码隧道，不启动本地 SSE 客户端",
            ),
        )
        return NoopHandle
    }

    private object NoopHandle : DshSseHandle {
        override fun close() = Unit
    }
}
