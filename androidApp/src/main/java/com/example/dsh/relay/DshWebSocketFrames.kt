package com.example.dsh.relay

import java.io.InputStream

/** RFC 6455 frames for the Relay loopback gateway. */
internal object DshWebSocketFrames {
    const val OPCODE_CONTINUATION = 0
    const val OPCODE_TEXT = 1
    const val OPCODE_BINARY = 2
    const val OPCODE_CLOSE = 8
    const val OPCODE_PING = 9
    const val OPCODE_PONG = 10

    data class Frame(val opcode: Int, val payload: ByteArray)

    fun isClose(opcode: Int): Boolean = opcode == OPCODE_CLOSE
    fun isPing(opcode: Int): Boolean = opcode == OPCODE_PING
    fun isPong(opcode: Int): Boolean = opcode == OPCODE_PONG
    fun isData(opcode: Int): Boolean =
        opcode == OPCODE_CONTINUATION || opcode == OPCODE_TEXT || opcode == OPCODE_BINARY

    fun forwardOpcode(opcode: Int): Int = if (opcode == OPCODE_BINARY) OPCODE_BINARY else OPCODE_TEXT

    fun encodeUnmasked(opcode: Int, payload: ByteArray): ByteArray {
        val headerSize = when {
            payload.size < 126 -> 2
            payload.size <= 0xffff -> 4
            else -> 10
        }
        val out = ByteArray(headerSize + payload.size)
        out[0] = (0x80 or (opcode and 0x0f)).toByte()
        writeLength(out, payload.size, masked = false)
        payload.copyInto(out, headerSize)
        return out
    }

    fun encodeMasked(opcode: Int, payload: ByteArray, mask: ByteArray): ByteArray {
        require(mask.size == 4)
        val headerSize = when {
            payload.size < 126 -> 6
            payload.size <= 0xffff -> 8
            else -> 14
        }
        val out = ByteArray(headerSize + payload.size)
        out[0] = (0x80 or (opcode and 0x0f)).toByte()
        writeLength(out, payload.size, masked = true)
        val maskAt = headerSize - 4
        mask.copyInto(out, maskAt)
        for (i in payload.indices) {
            out[headerSize + i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        }
        return out
    }

    fun readFrame(input: InputStream): Frame? {
        val b1 = input.read()
        val b2 = input.read()
        if (b1 < 0 || b2 < 0) return null
        val opcode = b1 and 0x0f
        val masked = b2 and 0x80 != 0
        var len = b2 and 0x7f
        if (len == 126) {
            val extra = readExact(input, 2) ?: return null
            len = ((extra[0].toInt() and 0xff) shl 8) or (extra[1].toInt() and 0xff)
        } else if (len == 127) {
            val extra = readExact(input, 8) ?: return null
            len = extra.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xff) }
        }
        val mask = if (masked) readExact(input, 4) ?: return null else ByteArray(0)
        val data = if (len == 0) ByteArray(0) else readExact(input, len) ?: return null
        if (masked) {
            for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
        }
        return Frame(opcode, data)
    }

    private fun writeLength(out: ByteArray, size: Int, masked: Boolean) {
        val maskBit = if (masked) 0x80 else 0
        when {
            size < 126 -> out[1] = (maskBit or size).toByte()
            size <= 0xffff -> {
                out[1] = (maskBit or 126).toByte()
                out[2] = ((size shr 8) and 0xff).toByte()
                out[3] = (size and 0xff).toByte()
            }
            else -> {
                out[1] = (maskBit or 127).toByte()
                var n = size.toLong()
                for (i in 9 downTo 2) {
                    out[i] = (n and 0xff).toByte()
                    n = n ushr 8
                }
            }
        }
    }

    private fun readExact(input: InputStream, count: Int): ByteArray? {
        val data = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(data, offset, count - offset)
            if (read < 0) return null
            offset += read
        }
        return data
    }
}
