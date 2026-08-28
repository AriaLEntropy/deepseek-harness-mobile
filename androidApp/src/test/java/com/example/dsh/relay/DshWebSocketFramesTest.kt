package com.example.dsh.relay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DshWebSocketFramesTest {
    @Test
    fun pingIsAnsweredLocallyAndNotForwardedAsText() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val mask = byteArrayOf(9, 8, 7, 6)
        val ping = DshWebSocketFrames.encodeMasked(DshWebSocketFrames.OPCODE_PING, payload, mask)
        val frame = DshWebSocketFrames.readFrame(ByteArrayInputStream(ping))
        requireNotNull(frame)
        assertTrue(DshWebSocketFrames.isPing(frame.opcode))
        assertArrayEquals(payload, frame.payload)
        assertTrue(!DshWebSocketFrames.isData(frame.opcode))

        val pong = DshWebSocketFrames.encodeUnmasked(DshWebSocketFrames.OPCODE_PONG, frame.payload)
        assertEquals(0x8a.toByte(), pong[0])
        assertEquals(4.toByte(), pong[1])
        assertArrayEquals(payload, pong.copyOfRange(2, pong.size))
    }

    @Test
    fun emptyPingEncodesEmptyPong() {
        val ping = DshWebSocketFrames.encodeMasked(
            DshWebSocketFrames.OPCODE_PING,
            ByteArray(0),
            byteArrayOf(1, 2, 3, 4),
        )
        val frame = DshWebSocketFrames.readFrame(ByteArrayInputStream(ping))
        requireNotNull(frame)
        val pong = DshWebSocketFrames.encodeUnmasked(DshWebSocketFrames.OPCODE_PONG, frame.payload)
        assertArrayEquals(byteArrayOf(0x8a.toByte(), 0), pong)
    }

    @Test
    fun closeAndPongAreControlFrames() {
        assertTrue(DshWebSocketFrames.isClose(DshWebSocketFrames.OPCODE_CLOSE))
        assertTrue(DshWebSocketFrames.isPong(DshWebSocketFrames.OPCODE_PONG))
        assertEquals(DshWebSocketFrames.OPCODE_TEXT, DshWebSocketFrames.forwardOpcode(1))
        assertEquals(DshWebSocketFrames.OPCODE_BINARY, DshWebSocketFrames.forwardOpcode(2))
        assertEquals(DshWebSocketFrames.OPCODE_TEXT, DshWebSocketFrames.forwardOpcode(9))
    }

    @Test
    fun textFrameRoundTrip() {
        val body = "{\"type\":\"session/event\"}".toByteArray()
        val masked = DshWebSocketFrames.encodeMasked(
            DshWebSocketFrames.OPCODE_TEXT,
            body,
            byteArrayOf(0x11, 0x22, 0x33, 0x44),
        )
        val frame = DshWebSocketFrames.readFrame(ByteArrayInputStream(masked))
        requireNotNull(frame)
        assertTrue(DshWebSocketFrames.isData(frame.opcode))
        assertEquals(DshWebSocketFrames.OPCODE_TEXT, frame.opcode)
        assertArrayEquals(body, frame.payload)
    }
}
