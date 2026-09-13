package com.example.dsh.attachment

import com.example.dsh.attachment.DshFileValidator
import com.example.dsh.attachment.DshImageLimits
import com.example.dsh.attachment.DshPendingFile
import com.example.dsh.attachment.DshPendingImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.example.dsh.attachment.DshAttachmentIntake
import com.example.dsh.attachment.DshFileIntake
import com.example.dsh.attachment.DshImageIntake

class DshAttachmentIntakeTest {

    private val limits = DshImageLimits.DEFAULT

    private fun file(name: String, bytes: Long, mediaType: String = "application/octet-stream") =
        DshPendingFile(clientId = name, mediaType = mediaType, name = name, dataBase64 = "", bytes = bytes)

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
    fun planFilesAcceptsValidCandidate() {
        val plan = DshAttachmentIntake.planFiles(emptyList(), listOf(file("a.txt", 10)))
        assertEquals(1, plan.size)
        assertEquals("a.txt", assertIs<DshFileIntake.Accepted>(plan[0]).file.name)
    }

    @Test
    fun planFilesRejectsDuplicateAgainstExisting() {
        val plan = DshAttachmentIntake.planFiles(listOf(file("a.txt", 10)), listOf(file("a.txt", 10)))
        assertEquals("已添加同名同大小的文件", assertIs<DshFileIntake.Rejected>(plan[0]).reason)
    }

    @Test
    fun planFilesRejectsWhenCountExceeded() {
        val existing = (1..DshFileValidator.DEFAULT_MAX_FILES).map { file("f$it.txt", 1) }
        val plan = DshAttachmentIntake.planFiles(existing, listOf(file("extra.txt", 1)))
        assertIs<DshFileIntake.Rejected>(plan[0])
    }

    @Test
    fun planFilesKeepsPerItemOrderAndAccumulatesAccepted() {
        val oversize = file("big.bin", DshFileValidator.DEFAULT_MAX_FILE_BYTES + 1)
        val ok = file("ok.txt", 10)
        val duplicate = file("ok.txt", 10)
        val plan = DshAttachmentIntake.planFiles(emptyList(), listOf(oversize, ok, duplicate))
        assertIs<DshFileIntake.Rejected>(plan[0])
        assertIs<DshFileIntake.Accepted>(plan[1])
        assertIs<DshFileIntake.Rejected>(plan[2])
    }

    @Test
    fun planImagesAcceptsValidCandidate() {
        val plan = DshAttachmentIntake.planImages(emptyList(), listOf(image()), limits)
        assertEquals("a.png", assertIs<DshImageIntake.Accepted>(plan[0]).image.name)
    }

    @Test
    fun planImagesMarksRejectedAsInvalidWithReason() {
        val plan = DshAttachmentIntake.planImages(emptyList(), listOf(image(mediaType = "image/bmp")), limits)
        val rejected = assertIs<DshImageIntake.Rejected>(plan[0])
        assertTrue(rejected.reason.contains("image/bmp"), rejected.reason)
        assertTrue(rejected.image.isInvalid)
        assertEquals(rejected.reason, rejected.image.error)
    }

    @Test
    fun planImagesCountsExistingTowardBatchLimit() {
        val existing = (1..limits.maxImagesPerMessage).map { image(name = "e$it.png") }
        val plan = DshAttachmentIntake.planImages(existing, listOf(image(name = "extra.png")), limits)
        assertIs<DshImageIntake.Rejected>(plan[0])
    }

    @Test
    fun planImagesAccumulatesAcceptedWithinOneBatch() {
        val each = limits.maxImageBytes - 1
        val picked = (1..11).map { image(name = "a$it.png", bytes = each) }
        val plan = DshAttachmentIntake.planImages(emptyList(), picked, limits)
        assertEquals(10, plan.filterIsInstance<DshImageIntake.Accepted>().size)
        assertTrue(plan.last() is DshImageIntake.Rejected)
    }
}
