package com.example.dsh.attachment

import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Color
import kotlin.math.roundToLong
import com.example.dsh.attachment.DshPendingFile

/**
 * 通用文件发送前预检（旧 Host backport）。
 *
 * 官方 0.1.5 对通用文件没有类型白名单、没有大小限制；这里只做移动端体验的前置拦截，
 * 与 host-plugin 附件端点的 50 MiB 上限保持一致，Host 仍是最终裁决者。
 */
object DshFileValidator {
    const val DEFAULT_MAX_FILE_BYTES = 50L * 1024 * 1024
    const val DEFAULT_MAX_FILES = 10

    /** 现有草稿 + 新增文件，返回可读拒绝原因；通过为 null。 */
    fun reject(existing: List<DshPendingFile>, addition: DshPendingFile): String? {
        if (addition.bytes > DEFAULT_MAX_FILE_BYTES) {
            return "单个文件不能超过 ${DshImageValidator.dshFormatBytes(DEFAULT_MAX_FILE_BYTES)}"
        }
        if (existing.size + 1 > DEFAULT_MAX_FILES) {
            return "每条消息最多发送 $DEFAULT_MAX_FILES 个文件"
        }
        if (existing.any { it.name == addition.name && it.bytes == addition.bytes }) {
            return "已添加同名同大小的文件"
        }
        return null
    }
}

/**
 * 文件卡片元信息行：`DOCX 16.85KB`。
 * 类型取扩展名大写；无扩展名时按 MIME 归类。大小固定两位小数，与设计稿一致。
 */
fun dshFileTypeLabel(name: String, mediaType: String): String {
    val ext = name.substringAfterLast('.', "").trim()
    if (ext.isNotEmpty() && ext.length <= 5) return ext.uppercase()
    return when {
        mediaType == "application/pdf" -> "PDF"
        mediaType.startsWith("text/") -> "TXT"
        mediaType.startsWith("image/") -> "IMG"
        mediaType.startsWith("audio/") -> "AUDIO"
        mediaType.startsWith("video/") -> "VIDEO"
        else -> "FILE"
    }
}

/** 文件大小：`16.85KB` / `48.00KB` / `1.20MB`；与设计稿的两位小数格式对齐。 */
fun dshFormatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0.00KB"
    if (bytes < 1024L) return "${bytes}B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.size - 1) {
        value /= 1024.0
        index += 1
    }
    val cents = (value * 100.0).roundToLong()
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "${cents / 100}.${fraction}${units[index]}"
}

/** 按类型选择 TDesign 同款文件图标（`file-*-filled`，单色 + tint 着色）。 */
fun dshFileIconAsset(name: String): String = when (dshFileExtension(name)) {
    "xls", "xlsx", "csv", "numbers" -> "file-excel.svg"
    "pdf" -> "file-pdf.svg"
    "ppt", "pptx", "key" -> "file-ppt.svg"
    "zip", "rar", "7z", "tar", "gz" -> "file-zip.svg"
    "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> "file-image.svg"
    "md", "mdx", "markdown" -> "file-code.svg"
    "mp4", "mov", "avi", "mkv", "flv", "wmv" -> "file-video.svg"
    "mp3", "wav", "flac", "aac", "ogg" -> "file-audio.svg"
    "doc", "docx", "pages", "txt", "rtf" -> "file-word.svg"
    else -> "file-generic.svg"
}

/** 类型着色，逐值对齐 TDesign FileCard 的 `PRESET_FILE_ICONS` 颜色（默认灰）。 */
fun dshFileIconTint(name: String, colors: DshColorTokens): Color = when (dshFileExtension(name)) {
    "xls", "xlsx", "csv", "numbers" -> colors.stateSuccessPrimary
    "pdf" -> colors.stateErrorPrimary
    "ppt", "pptx", "key" -> colors.stateWarnPrimary
    "zip", "rar", "7z", "tar", "gz" -> colors.stateWarnPrimary
    "mp4", "mov", "avi", "mkv", "flv", "wmv" -> colors.stateErrorPrimary
    "mp3", "wav", "flac", "aac", "ogg" -> colors.stateErrorPrimary
    "doc", "docx", "pages", "txt", "rtf" -> colors.stateBusinessPrimary
    else -> colors.labelSecondary
}

private fun dshFileExtension(name: String): String = name.substringAfterLast('.', "").lowercase()
