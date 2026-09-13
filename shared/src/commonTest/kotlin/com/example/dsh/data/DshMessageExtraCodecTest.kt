package com.example.dsh.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageExtraCodec
import com.example.dsh.message.DshMessageRole

class DshMessageExtraCodecTest {

    private fun base() = DshMessage(id = "m1", role = DshMessageRole.ASSISTANT, content = "hi")

    @Test
    fun encodeReturnsNullWhenNoCompositeFields() {
        assertNull(DshMessageExtraCodec.encode(base()))
    }

    @Test
    fun decodeReturnsBaseForMissingOrBrokenJson() {
        val base = base()
        assertEquals(base, DshMessageExtraCodec.decode(base, null))
        assertEquals(base, DshMessageExtraCodec.decode(base, "   "))
        assertEquals(base, DshMessageExtraCodec.decode(base, "not json"))
    }

    @Test
    fun roundTripsReadableContentAndSourceSeq() {
        val message = base().copy(readableContent = "hello", sourceSeq = 7)
        val encoded = DshMessageExtraCodec.encode(message)
        val decoded = DshMessageExtraCodec.decode(base(), encoded)
        assertEquals("hello", decoded.readableContent)
        assertEquals(7, decoded.sourceSeq)
    }

    @Test
    fun roundTripsAttachmentIds() {
        val message = base().copy(attachmentIds = listOf("a", "b"))
        val encoded = DshMessageExtraCodec.encode(message)
        val decoded = DshMessageExtraCodec.decode(base(), encoded)
        assertEquals(listOf("a", "b"), decoded.attachmentIds)
    }

    @Test
    fun decodeIgnoresJsonWithoutCompositeFields() {
        assertEquals(base(), DshMessageExtraCodec.decode(base(), "{}"))
    }
}
