package com.example.dsh.relay

import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal class DshRelayLoopbackServer(
    private val token: String,
    private val sendInner: (type: String, payload: JSONObject, channel: String) -> Unit,
) {
    private val httpLimit = AtomicInteger(0)
    private val wsLimit = AtomicInteger(0)
    private val httpWaiters = ConcurrentHashMap<String, LinkedBlockingQueue<JSONObject>>()
    private val sockets = ConcurrentHashMap<String, Socket>()
    private var server: ServerSocket? = null
    @Volatile var port: Int = 0
        private set
    @Volatile private var stopped = false

    fun start(): Int {
        val bind = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        server = bind
        port = bind.localPort
        thread(name = "dsh-relay-loopback", isDaemon = true) {
            while (!stopped) {
                val socket = runCatching { bind.accept() }.getOrNull() ?: break
                thread(name = "dsh-relay-conn", isDaemon = true) { handle(socket) }
            }
        }
        return port
    }

    fun isListening(): Boolean {
        val bind = server ?: return false
        return !stopped && !bind.isClosed && port > 0
    }

    fun stop() {
        stopped = true
        runCatching { server?.close() }
        sockets.values.forEach { runCatching { it.close() } }
        sockets.clear()
        httpWaiters.clear()
    }

    fun onInner(type: String, payload: JSONObject, channel: String) {
        when (type) {
            "http_res" -> httpWaiters[channel]?.offer(payload)
            "ws_open_ok" -> httpWaiters[channel]?.offer(JSONObject().put("ok", true))
            "ws_frame" -> writeWsFrame(channel, payload)
            "ws_close" -> {
                val waiter = httpWaiters[channel]
                if (waiter != null) {
                    waiter.offer(
                        JSONObject()
                            .put("ok", false)
                            .put("reason", payload.optString("reason").ifBlank { "host websocket closed" }),
                    )
                } else {
                    runCatching { sockets.remove(channel)?.close() }
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 120_000
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        try {
            val headerBytes = readHeaders(input) ?: return
            val text = String(headerBytes, Charsets.ISO_8859_1)
            val lines = text.split("\r\n")
            val request = lines.firstOrNull()?.split(" ") ?: return
            if (request.size < 2) return
            val method = request[0]
            val path = request[1]
            val headers = linkedMapOf<String, String>()
            lines.drop(1).forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            val auth = headers["authorization"].orEmpty()
            if (auth != "Bearer $token") {
                writeHttp(output, 401, "unauthorized")
                return
            }
            if (headers["upgrade"].equals("websocket", true)) {
                acceptWebSocket(socket, input, output, path, headers)
                return
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length > 2 * 1024 * 1024) {
                writeHttp(output, 413, "request too large")
                return
            }
            val body = if (length > 0) input.readNBytesCompat(length) else ByteArray(0)
            proxyHttp(output, method, path, headers, body)
        } catch (_: Exception) {
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun proxyHttp(
        output: BufferedOutputStream,
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray,
    ) {
        if (httpLimit.incrementAndGet() > 32) {
            httpLimit.decrementAndGet()
            writeHttp(output, 429, "too many tunnels")
            return
        }
        val channel = "http-${UUID.randomUUID()}"
        val queue = LinkedBlockingQueue<JSONObject>()
        httpWaiters[channel] = queue
        val forwarded = JSONObject()
        headers.forEach { (key, value) ->
            if (key == "authorization" || key == "host" || key == "content-length") return@forEach
            forwarded.put(key, value)
        }
        sendInner(
            "http_req",
            JSONObject()
                .put("method", method)
                .put("path", path)
                .put("headers", forwarded)
                .put("bodyB64", android.util.Base64.encodeToString(body, android.util.Base64.NO_WRAP)),
            channel,
        )
        try {
            var headerWritten = false
            while (true) {
                val part = queue.poll(120, TimeUnit.SECONDS) ?: break
                if (!headerWritten) {
                    val status = part.optInt("status", 200)
                    writeRaw(output, "HTTP/1.1 $status OK\r\n")
                    writeRaw(output, "Transfer-Encoding: chunked\r\nConnection: close\r\n")
                    val responseHeaders = part.optJSONObject("headers")
                    responseHeaders?.keys()?.forEach { key ->
                        val name = key as String
                        if (name.equals("content-length", true) || name.equals("transfer-encoding", true)) return@forEach
                        writeRaw(output, "$name: ${responseHeaders.optString(name)}\r\n")
                    }
                    writeRaw(output, "\r\n")
                    headerWritten = true
                }
                val chunk = android.util.Base64.decode(part.optString("bodyB64"), android.util.Base64.DEFAULT)
                if (chunk.isNotEmpty()) {
                    writeRaw(output, "${chunk.size.toString(16)}\r\n")
                    output.write(chunk)
                    writeRaw(output, "\r\n")
                }
                if (part.optBoolean("final")) {
                    writeRaw(output, "0\r\n\r\n")
                    output.flush()
                    break
                }
            }
        } finally {
            httpWaiters.remove(channel)
            httpLimit.decrementAndGet()
        }
    }

    private fun acceptWebSocket(
        socket: Socket,
        input: BufferedInputStream,
        output: BufferedOutputStream,
        path: String,
        headers: Map<String, String>,
    ) {
        if (wsLimit.incrementAndGet() > 16) {
            wsLimit.decrementAndGet()
            writeHttp(output, 503, "too many tunnels")
            return
        }
        val channel = "ws-${UUID.randomUUID()}"
        val queue = LinkedBlockingQueue<JSONObject>()
        httpWaiters[channel] = queue
        val forwarded = JSONObject()
        headers.forEach { (name, value) ->
            if (name == "authorization" || name == "host") return@forEach
            forwarded.put(name, value)
        }
        sendInner("ws_open", JSONObject().put("path", path).put("headers", forwarded), channel)
        val opened = queue.poll(10, TimeUnit.SECONDS)
        httpWaiters.remove(channel)
        if (opened == null || !opened.optBoolean("ok")) {
            wsLimit.decrementAndGet()
            val reason = when {
                opened == null -> "host websocket timeout"
                else -> opened.optString("reason").ifBlank { "host websocket rejected" }
            }
            Log.w(TAG, "ws_open failed path=$path reason=$reason")
            writeHttp(output, 502, reason)
            return
        }
        val key = headers["sec-websocket-key"].orEmpty()
        val accept = websocketAccept(key)
        writeRaw(output, "HTTP/1.1 101 Switching Protocols\r\n")
        writeRaw(output, "Upgrade: websocket\r\nConnection: Upgrade\r\n")
        writeRaw(output, "Sec-WebSocket-Accept: $accept\r\n\r\n")
        output.flush()
        sockets[channel] = socket
        try {
            while (!socket.isClosed) {
                val frame = readWsFrame(input) ?: break
                if (frame.first == 8) {
                    sendInner("ws_close", JSONObject().put("code", 1000).put("reason", ""), channel)
                    break
                }
                sendInner(
                    "ws_frame",
                    JSONObject()
                        .put("dataB64", android.util.Base64.encodeToString(frame.second, android.util.Base64.NO_WRAP))
                        .put("opcode", if (frame.first == 2) 2 else 1),
                    channel,
                )
            }
        } catch (_: Exception) {
        } finally {
            sockets.remove(channel)
            wsLimit.decrementAndGet()
            runCatching { socket.close() }
        }
    }

    private fun writeWsFrame(channel: String, payload: JSONObject) {
        val socket = sockets[channel] ?: return
        val data = android.util.Base64.decode(payload.optString("dataB64"), android.util.Base64.DEFAULT)
        val opcode = if (payload.optInt("opcode") == 2) 2 else 1
        val output = socket.getOutputStream()
        val header = ByteArray(2)
        header[0] = (0x80 or opcode).toByte()
        if (data.size < 126) {
            header[1] = data.size.toByte()
            output.write(header)
        } else {
            header[1] = 126
            output.write(header)
            output.write(byteArrayOf(((data.size shr 8) and 0xff).toByte(), (data.size and 0xff).toByte()))
        }
        output.write(data)
        output.flush()
    }

    private fun readWsFrame(input: BufferedInputStream): Pair<Int, ByteArray>? {
        val b1 = input.read()
        val b2 = input.read()
        if (b1 < 0 || b2 < 0) return null
        val opcode = b1 and 0x0f
        val masked = b2 and 0x80 != 0
        var len = b2 and 0x7f
        if (len == 126) {
            val extra = input.readNBytesCompat(2)
            len = ((extra[0].toInt() and 0xff) shl 8) or (extra[1].toInt() and 0xff)
        } else if (len == 127) {
            val extra = input.readNBytesCompat(8)
            len = ByteBufferWrap.intFrom8(extra)
        }
        val mask = if (masked) input.readNBytesCompat(4) else ByteArray(0)
        val data = input.readNBytesCompat(len)
        if (masked) {
            for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
        }
        return opcode to data
    }

    private fun websocketAccept(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
    }

    private fun readHeaders(input: BufferedInputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var last = 0
        while (true) {
            val next = input.read()
            if (next < 0) return null
            buffer.write(next)
            if (last == '\r'.code && next == '\n'.code && buffer.size() >= 4) {
                val bytes = buffer.toByteArray()
                if (bytes.size >= 4 &&
                    bytes[bytes.size - 4] == '\r'.code.toByte() &&
                    bytes[bytes.size - 3] == '\n'.code.toByte()
                ) return bytes
            }
            last = next
            if (buffer.size() > 64 * 1024) return null
        }
    }

    private fun writeHttp(output: BufferedOutputStream, status: Int, body: String) {
        val bytes = body.toByteArray()
        writeRaw(output, "HTTP/1.1 $status ERROR\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
        output.write(bytes)
        output.flush()
    }

    private fun writeRaw(output: BufferedOutputStream, text: String) {
        output.write(text.toByteArray(Charsets.ISO_8859_1))
    }

    private fun BufferedInputStream.readNBytesCompat(count: Int): ByteArray {
        val data = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(data, offset, count - offset)
            if (read < 0) break
            offset += read
        }
        return if (offset == count) data else data.copyOf(offset)
    }

    companion object {
        private const val TAG = "DshRelayLoopback"
    }
}

private object ByteBufferWrap {
    fun intFrom8(bytes: ByteArray): Int {
        var value = 0
        for (b in bytes) value = (value shl 8) or (b.toInt() and 0xff)
        return value
    }
}
