package com.example.dsh.module

import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

internal class KRDshSseModule : KuiklyRenderBaseModule() {
    private val connections = ConcurrentHashMap<String, SseConnection>()

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        val value = runCatching { JSONObject(params ?: "{}") }.getOrDefault(JSONObject())
        return when (method) {
            "connect" -> {
                val connectionId = value.optString("connectionId")
                val url = value.optString("url")
                if (connectionId.isEmpty() || url.isEmpty() || callback == null) return null
                connections.remove(connectionId)?.close()
                SseConnection(
                    url = url,
                    token = value.optString("token"),
                    emit = { event -> activity?.runOnUiThread { callback.invoke(event) } },
                    onFinished = { connections.remove(connectionId) },
                ).also {
                    connections[connectionId] = it
                    it.start()
                }
                null
            }
            "disconnect" -> {
                connections.remove(value.optString("connectionId"))?.close()
                null
            }
            else -> null
        }
    }

    private class SseConnection(
        private val url: String,
        private val token: String,
        private val emit: (Map<String, Any>) -> Unit,
        private val onFinished: () -> Unit,
    ) {
        @Volatile private var closed = false
        @Volatile private var connection: HttpURLConnection? = null

        fun start() {
            Thread({ readStream() }, "dsh-sse").start()
        }

        fun close() {
            closed = true
            connection?.disconnect()
        }

        private fun readStream() {
            try {
                val activeConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = 0
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("Cache-Control", "no-cache")
                    if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
                }
                connection = activeConnection
                val statusCode = activeConnection.responseCode
                if (statusCode !in 200..299) {
                    emitError("events.mux failed with HTTP $statusCode")
                    return
                }
                emit(mapOf("kind" to "OPEN"))
                BufferedReader(InputStreamReader(activeConnection.inputStream, Charsets.UTF_8)).use { reader ->
                    val data = StringBuilder()
                    while (!closed) {
                        val line = reader.readLine() ?: break
                        when {
                            line.isEmpty() -> flushData(data)
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substringAfter("data:").trimStart())
                            }
                        }
                    }
                    flushData(data)
                }
                if (!closed) emit(mapOf("kind" to "CLOSED"))
            } catch (error: Throwable) {
                if (!closed) emitError(error.message ?: "SSE connection failed")
            } finally {
                connection?.disconnect()
                connection = null
                onFinished()
            }
        }

        private fun flushData(data: StringBuilder) {
            if (data.isEmpty()) return
            emit(mapOf("kind" to "FRAME", "data" to data.toString()))
            data.setLength(0)
        }

        private fun emitError(message: String) {
            emit(mapOf("kind" to "ERROR", "message" to message))
        }
    }

    companion object {
        const val MODULE_NAME = "DshSseModule"
        private const val CONNECT_TIMEOUT_MS = 10_000
    }
}
