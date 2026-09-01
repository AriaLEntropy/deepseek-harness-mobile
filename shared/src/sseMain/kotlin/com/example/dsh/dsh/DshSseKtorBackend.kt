package com.example.dsh.dsh

import com.tencent.kuikly.core.log.KLog
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal actual fun createDshSseBackend(): DshSseBackend = DshSseKtorBackend()

private class DshSseKtorBackend : DshSseBackend {
    private val client by lazy { createDshHttpClient() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun connect(url: String, token: String, onEvent: (DshSseEvent) -> Unit): DshSseHandle {
        KLog.i(TAG, "connect requested")
        val job = scope.launch {
            try {
                openSse(url, token, onEvent)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SSEClientException) {
                if (error.response?.status == HttpStatusCode.UpgradeRequired) {
                    openWebSocket(url, token, onEvent)
                } else {
                    emit(onEvent, DshSseEvent(DshSseEventKind.ERROR, message = error.message ?: "SSE connection failed"))
                }
            } catch (error: Throwable) {
                emit(onEvent, DshSseEvent(DshSseEventKind.ERROR, message = error.message ?: "SSE connection failed"))
            }
        }
        return JobHandle(job)
    }

    private suspend fun openSse(url: String, token: String, onEvent: (DshSseEvent) -> Unit) {
        client.sse(url, request = {
            headers.append(HttpHeaders.Accept, "text/event-stream")
            headers.append(HttpHeaders.CacheControl, "no-cache")
            if (token.isNotEmpty()) headers.append(HttpHeaders.Authorization, "Bearer $token")
        }) {
            emit(onEvent, DshSseEvent(DshSseEventKind.OPEN))
            incoming.collect { event ->
                event.data?.takeIf { it.isNotEmpty() }?.let { data ->
                    emit(onEvent, DshSseEvent(DshSseEventKind.FRAME, data))
                }
            }
            emit(onEvent, DshSseEvent(DshSseEventKind.CLOSED))
        }
    }

    private suspend fun openWebSocket(url: String, token: String, onEvent: (DshSseEvent) -> Unit) {
        client.webSocket(dshWebSocketUrl(url), request = {
            if (token.isNotEmpty()) headers.append(HttpHeaders.Authorization, "Bearer $token")
        }) {
            emit(onEvent, DshSseEvent(DshSseEventKind.OPEN))
            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText().orEmpty()
                if (text.isNotEmpty()) emit(onEvent, DshSseEvent(DshSseEventKind.FRAME, text))
            }
            emit(onEvent, DshSseEvent(DshSseEventKind.CLOSED))
        }
    }

    private suspend fun emit(onEvent: (DshSseEvent) -> Unit, event: DshSseEvent) {
        withContext(Dispatchers.Main) { onEvent(event) }
    }

    private class JobHandle(private val job: Job) : DshSseHandle {
        override fun close() {
            job.cancel()
        }
    }

    companion object {
        private const val TAG = "DshEventStream"
    }
}
