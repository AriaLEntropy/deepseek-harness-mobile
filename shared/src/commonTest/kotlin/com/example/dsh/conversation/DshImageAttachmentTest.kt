package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 3 附件链路测试：imageLimits 解析、预检（MIME/大小/像素/边长/数量/总量）、
 * 发送前草稿状态机。纯逻辑层测试，不依赖网络与平台。
 */
class DshImageAttachmentTest {

    private val limits = DshImageLimits(
        maxImageBytes = 2L * 1024 * 1024,
        maxImagesPerMessage = 3,
        maxMessageImageBytes = 5L * 1024 * 1024,
        maxImagePixels = 4_000_000L,
        maxImageDimension = 4096L,
        mediaTypes = listOf("image/png", "image/jpeg", "image/webp", "image/gif"),
    )

    private fun image(
        clientId: String = "i1",
        mime: String = "image/png",
        bytes: Long = 1024,
        width: Int = 100,
        height: Int = 100,
        state: DshImageDraftState = DshImageDraftState.SELECTED,
    ) = DshPendingImage(
        clientId = clientId,
        mediaType = mime,
        name = "pic.png",
        dataBase64 = "aGVsbG8=",
        previewDataUrl = "data:image/png;base64,aGVsbG8=",
        bytes = bytes,
        width = width,
        height = height,
        state = state,
    )

    // ---------- imageLimits 解析 ----------

    @Test
    fun fromJsonParsesFullLimits() {
        val value = JSONObject(
            """{"maxImageBytes":2097152,"maxImagesPerMessage":3,"maxMessageImageBytes":5242880,
               "maxImagePixels":4000000,"maxImageDimension":4096,
               "mediaTypes":["image/png","image/jpeg"]}""",
        )
        val parsed = DshImageLimits.fromJson(value)
        assertNotNull(parsed)
        assertEquals(2097152L, parsed.maxImageBytes)
        assertEquals(3, parsed.maxImagesPerMessage)
        assertEquals(4096L, parsed.maxImageDimension)
        assertEquals(listOf("image/png", "image/jpeg"), parsed.mediaTypes)
    }

    @Test
    fun fromJsonReturnsNullWhenEssentialFieldMissing() {
        assertNull(DshImageLimits.fromJson(JSONObject("""{"maxImageBytes":1,"maxImagesPerMessage":1}""")))
        assertNull(
            DshImageLimits.fromJson(
                JSONObject(
                    """{"maxImageBytes":2097152,"maxImagesPerMessage":3,"maxMessageImageBytes":5242880,
                       "maxImagePixels":4000000,"maxImageDimension":4096,"mediaTypes":[]}""",
                ),
            ),
        )
        assertNull(DshImageLimits.fromJson(null))
    }

    @Test
    fun defaultLimitsAreConservativeAndNonZero() {
        assertEquals(20L * 1024 * 1024, DshImageLimits.DEFAULT.maxImageBytes)
        assertEquals(20, DshImageLimits.DEFAULT.maxImagesPerMessage)
        assertEquals(8192L, DshImageLimits.DEFAULT.maxImageDimension)
        assertTrue(DshImageLimits.DEFAULT.mediaTypes.contains("image/webp"))
    }

    // ---------- 单张预检 ----------

    @Test
    fun rejectSinglePassesValidImage() {
        assertNull(DshImageValidator.rejectSingle(image(), limits))
    }

    @Test
    fun rejectSingleRejectsUnsupportedMime() {
        val rejection = DshImageValidator.rejectSingle(image(mime = "image/bmp"), limits)
        assertTrue(rejection is DshImageRejection.UnsupportedType)
        assertEquals("image/bmp", (rejection as DshImageRejection.UnsupportedType).mediaType)
    }

    @Test
    fun rejectSingleRejectsOversizedFile() {
        val rejection = DshImageValidator.rejectSingle(image(bytes = 3L * 1024 * 1024), limits)
        assertTrue(rejection is DshImageRejection.TooLarge)
        assertEquals(2L * 1024 * 1024, (rejection as DshImageRejection.TooLarge).limitBytes)
    }

    @Test
    fun rejectSingleRejectsTooManyPixels() {
        val rejection = DshImageValidator.rejectSingle(image(width = 3000, height = 2000), limits)
        assertTrue(rejection is DshImageRejection.TooManyPixels)
    }

    @Test
    fun rejectSingleRejectsOversizedDimension() {
        val rejection = DshImageValidator.rejectSingle(image(width = 8192, height = 100), limits)
        assertTrue(rejection is DshImageRejection.DimensionTooLarge)
        assertEquals(4096L, (rejection as DshImageRejection.DimensionTooLarge).limitDimension)
    }

    @Test
    fun rejectSingleSkipsPixelCheckWhenDimensionsUnknown() {
        // 平台未解码出宽高时只校验 MIME/大小，不误伤
        assertNull(DshImageValidator.rejectSingle(image(width = 0, height = 0), limits))
    }

    // ---------- 批量预检 ----------

    @Test
    fun rejectBatchRejectsTooManyImages() {
        val existing = listOf(image("a"), image("b"), image("c"))
        val rejection = DshImageValidator.rejectBatch(existing, listOf(image("d")), limits)
        assertTrue(rejection is DshImageRejection.TooMany)
        assertEquals(3, (rejection as DshImageRejection.TooMany).limitCount)
    }

    @Test
    fun rejectBatchRejectsExceedingTotalBytes() {
        // 单张均在 2MB 上限内（等于边界不超），三张总量 6MB 超过 5MB 总量上限
        val existing = listOf(image("a", bytes = 2L * 1024 * 1024), image("b", bytes = 2L * 1024 * 1024))
        val addition = image("c", bytes = 2L * 1024 * 1024)
        val rejection = DshImageValidator.rejectBatch(existing, listOf(addition), limits)
        assertTrue(rejection is DshImageRejection.TotalTooLarge)
        assertEquals(5L * 1024 * 1024, (rejection as DshImageRejection.TotalTooLarge).limitBytes)
    }

    @Test
    fun rejectBatchRejectsAnySingleViolationAmongAdditions() {
        val ok = image("ok")
        val bad = image("bad", mime = "image/tiff")
        val rejection = DshImageValidator.rejectBatch(listOf(ok), listOf(bad), limits)
        assertTrue(rejection is DshImageRejection.UnsupportedType)
    }

    @Test
    fun rejectBatchPassesWhenAllWithinLimits() {
        assertNull(
            DshImageValidator.rejectBatch(
                listOf(image("a", bytes = 100), image("b", bytes = 200)),
                listOf(image("c", bytes = 300)),
                limits,
            ),
        )
    }

    // ---------- 文案与草稿状态 ----------

    @Test
    fun rejectionTextProducesReadableMessage() {
        val text = DshImageValidator.rejectionText(DshImageRejection.TooMany(3))
        assertTrue(text.isNotEmpty())
        assertTrue(text.contains("3"))
        val oversize = DshImageValidator.rejectionText(DshImageRejection.TooLarge(2L * 1024 * 1024))
        assertTrue(oversize.contains("MB") || oversize.contains("KB"))
    }

    @Test
    fun pendingImageStateFlagsTrackDraftLifecycle() {
        val selected = image()
        assertFalse(selected.isUploading)
        assertFalse(selected.isInvalid)

        val uploading = selected.copy(state = DshImageDraftState.UPLOADING)
        assertTrue(uploading.isUploading)

        val invalid = selected.copy(state = DshImageDraftState.INVALID, error = "超限")
        assertTrue(invalid.isInvalid)
        assertEquals("超限", invalid.error)

        val failed = selected.copy(state = DshImageDraftState.FAILED)
        assertFalse(failed.isInvalid)
        assertFalse(failed.isUploading)
    }

    @Test
    fun dshFormatBytesFormatsSizes() {
        assertEquals("1.0MB", DshImageValidator.dshFormatBytes(1024L * 1024))
        assertEquals("512KB", DshImageValidator.dshFormatBytes(512L * 1024))
    }
}
