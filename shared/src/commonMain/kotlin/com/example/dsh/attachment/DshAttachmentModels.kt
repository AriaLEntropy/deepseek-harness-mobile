package com.example.dsh.attachment

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Host 下发的图片接入限额（来自 `imageLimits` projection，Host 为最终裁决者）。 */
internal data class DshImageLimits(
    val maxImageBytes: Long,
    val maxImagesPerMessage: Int,
    val maxMessageImageBytes: Long,
    val maxImagePixels: Long,
    /** 单张图片最大边长（宽、高各自的上限，单位像素）。 */
    val maxImageDimension: Long,
    val mediaTypes: List<String>,
) {
    companion object {
        /** imageLimits projection 缺失时的保守默认，仅用于前置体验；Host 校验仍然权威。 */
        val DEFAULT = DshImageLimits(
            maxImageBytes = 20L * 1024 * 1024,
            maxImagesPerMessage = 20,
            maxMessageImageBytes = 200L * 1024 * 1024,
            maxImagePixels = 64_000_000L,
            maxImageDimension = 8192L,
            mediaTypes = listOf("image/png", "image/jpeg", "image/webp", "image/gif"),
        )

        fun fromJson(value: com.tencent.kuikly.core.nvi.serialization.json.JSONObject?): DshImageLimits? {
            if (value == null) return null
            val mediaTypes = buildList {
                val array = value.optJSONArray("mediaTypes") ?: return@buildList
                for (index in 0 until array.length()) {
                    array.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
            if (mediaTypes.isEmpty()) return null
            return DshImageLimits(
                maxImageBytes = value.optLong("maxImageBytes", 0L).takeIf { it > 0L } ?: return null,
                maxImagesPerMessage = value.optInt("maxImagesPerMessage", 0).takeIf { it > 0 } ?: return null,
                maxMessageImageBytes = value.optLong("maxMessageImageBytes", 0L).takeIf { it > 0L } ?: return null,
                maxImagePixels = value.optLong("maxImagePixels", 0L).takeIf { it > 0L } ?: return null,
                maxImageDimension = value.optLong("maxImageDimension", 0L).takeIf { it > 0L } ?: return null,
                mediaTypes = mediaTypes,
            )
        }
    }
}

/** 输入区待发送图片的发送阶段。 */

/** 输入区待发送图片的发送阶段。 */
internal enum class DshImageDraftState {
    /** 已选择并通过预检，等待随消息发送。 */
    SELECTED,
    /** 预检不通过（超限/类型不支持），显示原因，不进入发送。 */
    INVALID,
    /** 已随 session.prompt 发送，等待 Host 确认。 */
    UPLOADING,
    /** Host 已接收（消息事实包含 ImageAttachmentRef）。 */
    SENT,
    /** 发送失败，可删除或重试。 */
    FAILED,
}

/**
 * 发送前的图片草稿。仅存在于输入区生命周期内，绝不写入会话历史；
 * Host 落盘后历史只保留 [DshImageAttachmentRef] 形式的引用。
 */

/**
 * 发送前的图片草稿。仅存在于输入区生命周期内，绝不写入会话历史；
 * Host 落盘后历史只保留 [DshImageAttachmentRef] 形式的引用。
 */
internal data class DshPendingImage(
    val clientId: String,
    val mediaType: String,
    val name: String,
    /** 规范 Base64（发送给 Host 的 data 字段）。 */
    val dataBase64: String,
    /** 本地预览 dataUrl（UI 缩略图）。 */
    val previewDataUrl: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val state: DshImageDraftState = DshImageDraftState.SELECTED,
    val error: String = "",
) {
    val isInvalid: Boolean get() = state == DshImageDraftState.INVALID
    val isUploading: Boolean get() = state == DshImageDraftState.UPLOADING
}

/** Host 历史中的图片引用（ImageAttachmentRef），不含 path/url/Base64。 */

/** Host 历史中的图片引用（ImageAttachmentRef），不含 path/url/Base64。 */
internal data class DshImageAttachmentRef(
    val attachmentId: String,
    val mediaType: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val name: String = "",
)

/** 输入区待发送的通用文件草稿；仅内存，不落盘。旧 Host 无文件 content 类型，发送前由插件落盘。 */

/** 输入区待发送的通用文件草稿；仅内存，不落盘。旧 Host 无文件 content 类型，发送前由插件落盘。 */
internal enum class DshFileDraftState { SELECTED, UPLOADING, FAILED }

internal data class DshPendingFile(
    val clientId: String,
    val mediaType: String,
    val name: String,
    /** 规范 Base64；仅用于发送草稿，不写历史。 */
    val dataBase64: String,
    val bytes: Long,
    val state: DshFileDraftState = DshFileDraftState.SELECTED,
    val error: String = "",
    /** 插件上传成功后的 Host 落盘路径（写入 prompt handle）。 */
    val path: String = "",
    val sha256: String = "",
    val handle: String = "",
) {
    val isUploading: Boolean get() = state == DshFileDraftState.UPLOADING
    val isFailed: Boolean get() = state == DshFileDraftState.FAILED
    val isUploaded: Boolean get() = handle.isNotEmpty()
}

/** 插件上传结果；与官方 FileAttachmentRef 的差异见 host-plugin/README。 */

/** 插件上传结果；与官方 FileAttachmentRef 的差异见 host-plugin/README。 */
internal data class DshUploadedFile(
    val name: String,
    val mediaType: String,
    val bytes: Long,
    val sha256: String,
    val path: String,
    val handle: String,
)

/** 从用户消息文本解析出的 Host 文件句柄（模型可读的绝对路径）。 */

/** 从用户消息文本解析出的 Host 文件句柄（模型可读的绝对路径）。 */
internal data class DshFileAttachment(
    val name: String,
    val bytes: Long,
    val sha256: String,
    val path: String,
)

/**
 * 解析插件写入 prompt 的 `[file] name (bytes bytes) sha256:<12> path: <path>` 行。
 * 与 host-plugin/attachment.mjs 的 [formatHandle] 保持逐字一致。
 */

/**
 * 解析插件写入 prompt 的 `[file] name (bytes bytes) sha256:<12> path: <path>` 行。
 * 与 host-plugin/attachment.mjs 的 [formatHandle] 保持逐字一致。
 */
internal object DshFileHandle {
    private val pattern = Regex(
        """^\s*\[file\]\s+(.+?)\s+\((\d+)\s+bytes\)\s+sha256:([0-9a-fA-F]+)\s+path:\s+(.+?)\s*$""",
    )

    fun parse(line: String): DshFileAttachment? {
        val match = pattern.matchEntire(line) ?: return null
        val bytes = match.groupValues[2].toLongOrNull() ?: return null
        return DshFileAttachment(
            name = match.groupValues[1].trim(),
            bytes = bytes,
            sha256 = match.groupValues[3],
            path = match.groupValues[4].trim(),
        )
    }

    fun parseAll(text: String): List<DshFileAttachment> =
        text.lineSequence().mapNotNull(::parse).toList()

    /** 去掉 handle 行后的可见正文，供用户气泡展示。 */
    fun strip(text: String): String =
        text.lineSequence().filter { parse(it) == null }.joinToString("\n").trim()

    fun hasHandle(text: String): Boolean = text.lineSequence().any { parse(it) != null }
}
