package com.example.dsh.attachment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.example.dsh.attachment.dshFileIconAsset
import com.example.dsh.attachment.dshFileTypeLabel
import com.example.dsh.attachment.DshFileValidator
import com.example.dsh.attachment.dshFormatFileSize
import com.example.dsh.attachment.DshPendingFile

class DshFileValidationTest {

    private fun file(name: String, bytes: Long, mediaType: String = "application/octet-stream") =
        DshPendingFile(clientId = name, mediaType = mediaType, name = name, dataBase64 = "", bytes = bytes)

    @Test
    fun rejectsOversizedFile() {
        val reason = DshFileValidator.reject(
            existing = emptyList(),
            addition = file("big.bin", DshFileValidator.DEFAULT_MAX_FILE_BYTES + 1),
        )
        assertTrue(reason != null)
        assertTrue(reason!!.contains("50.0MB"), reason)
    }

    @Test
    fun rejectsTooManyFiles() {
        val existing = (1..DshFileValidator.DEFAULT_MAX_FILES).map { file("f$it.txt", 1) }
        val reason = DshFileValidator.reject(existing, file("extra.txt", 1))
        assertEquals("每条消息最多发送 ${DshFileValidator.DEFAULT_MAX_FILES} 个文件", reason)
    }

    @Test
    fun rejectsDuplicateNameAndSize() {
        val existing = listOf(file("a.txt", 10))
        assertEquals("已添加同名同大小的文件", DshFileValidator.reject(existing, file("a.txt", 10)))
    }

    @Test
    fun acceptsValidFile() {
        assertNull(DshFileValidator.reject(emptyList(), file("a.txt", 10)))
    }

    @Test
    fun formatsSizes() {
        assertEquals("0.00KB", dshFormatFileSize(0))
        assertEquals("0.00KB", dshFormatFileSize(-5))
        assertEquals("512B", dshFormatFileSize(512))
        assertEquals("1.00KB", dshFormatFileSize(1024))
        assertEquals("48.00KB", dshFormatFileSize(48L * 1024))
        assertEquals("16.85KB", dshFormatFileSize(17254))
        assertEquals("1.00MB", dshFormatFileSize(1024L * 1024))
    }

    @Test
    fun labelsTypes() {
        assertEquals("DOCX", dshFileTypeLabel("report.docx", ""))
        assertEquals("GZ", dshFileTypeLabel("archive.tar.gz", ""))
        assertEquals("PDF", dshFileTypeLabel("noextension", "application/pdf"))
        assertEquals("TXT", dshFileTypeLabel("notes", "text/plain"))
        assertEquals("IMG", dshFileTypeLabel("pic", "image/png"))
        assertEquals("AUDIO", dshFileTypeLabel("clip", "audio/mpeg"))
        assertEquals("VIDEO", dshFileTypeLabel("movie", "video/mp4"))
        assertEquals("FILE", dshFileTypeLabel("thing", "application/octet-stream"))
        assertEquals("PDF", dshFileTypeLabel("file.verylongext", "application/pdf"))
    }

    @Test
    fun picksIconAssets() {
        assertEquals("file-excel.svg", dshFileIconAsset("a.xlsx"))
        assertEquals("file-pdf.svg", dshFileIconAsset("A.PDF"))
        assertEquals("file-ppt.svg", dshFileIconAsset("a.pptx"))
        assertEquals("file-zip.svg", dshFileIconAsset("a.zip"))
        assertEquals("file-image.svg", dshFileIconAsset("a.png"))
        assertEquals("file-code.svg", dshFileIconAsset("a.md"))
        assertEquals("file-video.svg", dshFileIconAsset("a.mp4"))
        assertEquals("file-audio.svg", dshFileIconAsset("a.mp3"))
        assertEquals("file-word.svg", dshFileIconAsset("a.docx"))
        assertEquals("file-generic.svg", dshFileIconAsset("a.unknown"))
    }
}
