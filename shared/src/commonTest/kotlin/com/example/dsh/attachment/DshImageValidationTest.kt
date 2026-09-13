package com.example.dsh.attachment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.example.dsh.attachment.DshImageLimits
import com.example.dsh.attachment.DshImageRejection
import com.example.dsh.attachment.DshImageValidator
import com.example.dsh.attachment.DshPendingImage

class DshImageValidationTest {

    private val limits = DshImageLimits.DEFAULT

    private fun image(
        name: String = "a.png",
        mediaType: String = "image/png",
        bytes: Long = 1024,
        width: Int = 100,
        height: Int = 100,
    ) = DshPendingImage(
        clientId = name,
        mediaType = mediaType,
        name = name,
        dataBase64 = "",
        previewDataUrl = "",
        bytes = bytes,
        width = width,
        height = height,
    )

    @Test
    fun rejectsUnsupportedType() {
        assertIs<DshImageRejection.UnsupportedType>(DshImageValidator.rejectSingle(image(mediaType = "image/bmp"), limits))
    }

    @Test
    fun rejectsTooLarge() {
        assertIs<DshImageRejection.TooLarge>(
            DshImageValidator.rejectSingle(image(bytes = limits.maxImageBytes + 1), limits),
        )
    }

    @Test
    fun rejectsTooManyPixels() {
        assertIs<DshImageRejection.TooManyPixels>(
            DshImageValidator.rejectSingle(image(width = 10000, height = 10000), limits),
        )
    }

    @Test
    fun rejectsOversizedDimensionEvenUnderPixelBudget() {
        assertIs<DshImageRejection.DimensionTooLarge>(
            DshImageValidator.rejectSingle(image(width = 9000, height = 1000), limits),
        )
    }

    @Test
    fun skipsPixelChecksWhenDimensionsUnknown() {
        assertNull(DshImageValidator.rejectSingle(image(width = 0, height = 0), limits))
    }

    @Test
    fun acceptsValidImage() {
        assertNull(DshImageValidator.rejectSingle(image(), limits))
    }

    @Test
    fun batchRejectsTooMany() {
        val existing = (1..limits.maxImagesPerMessage).map { image(name = "e$it.png") }
        assertIs<DshImageRejection.TooMany>(DshImageValidator.rejectBatch(existing, listOf(image(name = "extra.png")), limits))
    }

    @Test
    fun batchRejectsTotalTooLarge() {
        val each = limits.maxImageBytes - 1
        val additions = (1..11).map { image(name = "a$it.png", bytes = each) }
        assertIs<DshImageRejection.TotalTooLarge>(DshImageValidator.rejectBatch(emptyList(), additions, limits))
    }

    @Test
    fun batchAcceptsWithinLimits() {
        assertNull(DshImageValidator.rejectBatch(listOf(image()), listOf(image(name = "b.png")), limits))
    }

    @Test
    fun rendersRejectionText() {
        assertTrue(DshImageValidator.rejectionText(DshImageRejection.UnsupportedType("image/bmp")).contains("image/bmp"))
        assertEquals("单张图片不能超过 20.0MB", DshImageValidator.rejectionText(DshImageRejection.TooLarge(limits.maxImageBytes)))
        assertEquals("每条消息最多发送 20 张图片", DshImageValidator.rejectionText(DshImageRejection.TooMany(20)))
    }
}
