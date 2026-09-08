package com.example.dsh.conversation

/**
 * 图片发送前预检（intake pre-check）。
 *
 * 与官方 `InputBar.intakeFiles` 及 attachment 后端 admission 对齐：
 * 客户端只做前置体验拦截，Host 仍是最终裁决者；imageLimits 缺失时使用
 * [DshImageLimits.DEFAULT] 保守默认，Host 校验仍然权威。
 */
internal sealed class DshImageRejection {
    data class UnsupportedType(val mediaType: String) : DshImageRejection()
    data class TooLarge(val limitBytes: Long) : DshImageRejection()
    data class TooMany(val limitCount: Int) : DshImageRejection()
    data class TotalTooLarge(val limitBytes: Long) : DshImageRejection()
    data class TooManyPixels(val limitPixels: Long) : DshImageRejection()
    data class DimensionTooLarge(val limitDimension: Long) : DshImageRejection()
}

internal object DshImageValidator {

    /** 单张图片的预检：MIME、单张字节、像素、单边。 */
    fun rejectSingle(image: DshPendingImage, limits: DshImageLimits): DshImageRejection? {
        if (image.mediaType !in limits.mediaTypes) {
            return DshImageRejection.UnsupportedType(image.mediaType)
        }
        if (image.bytes > limits.maxImageBytes) {
            return DshImageRejection.TooLarge(limits.maxImageBytes)
        }
        if (image.width > 0 && image.height > 0) {
            val pixels = image.width.toLong() * image.height.toLong()
            if (pixels > limits.maxImagePixels) {
                return DshImageRejection.TooManyPixels(limits.maxImagePixels)
            }
            if (image.width > limits.maxImageDimension || image.height > limits.maxImageDimension) {
                return DshImageRejection.DimensionTooLarge(limits.maxImageDimension)
            }
        }
        return null
    }

    /**
     * 批量预检：现有草稿 + 新增图片，作为整体拒绝。
     * 与官方一致——多选采用全量预检后再发送，避免部分图片进入历史而部分未进入。
     */
    fun rejectBatch(
        existing: List<DshPendingImage>,
        additions: List<DshPendingImage>,
        limits: DshImageLimits,
    ): DshImageRejection? {
        val all = existing + additions
        if (all.size > limits.maxImagesPerMessage) {
            return DshImageRejection.TooMany(limits.maxImagesPerMessage)
        }
        additions.forEach { image ->
            rejectSingle(image, limits)?.let { return it }
        }
        val totalBytes = all.sumOf { it.bytes }
        if (totalBytes > limits.maxMessageImageBytes) {
            return DshImageRejection.TotalTooLarge(limits.maxMessageImageBytes)
        }
        return null
    }

    /** 人类可读文案；错误码语义与 Host attachment rejection reason 对齐。 */
    fun rejectionText(rejection: DshImageRejection): String = when (rejection) {
        is DshImageRejection.UnsupportedType ->
            "不支持的图片类型（${rejection.mediaType}），仅支持 PNG/JPEG/WebP/GIF"
        is DshImageRejection.TooLarge ->
            "单张图片不能超过 ${dshFormatBytes(rejection.limitBytes)}"
        is DshImageRejection.TooMany ->
            "每条消息最多发送 ${rejection.limitCount} 张图片"
        is DshImageRejection.TotalTooLarge ->
            "本条消息图片总量不能超过 ${dshFormatBytes(rejection.limitBytes)}"
        is DshImageRejection.TooManyPixels ->
            "图片像素超过上限（${rejection.limitPixels} 像素）"
        is DshImageRejection.DimensionTooLarge ->
            "图片单边不能超过 ${rejection.limitDimension} 像素"
    }

    internal fun dshFormatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            val text = (mb * 10).toLong() / 10.0
            "${text}MB"
        } else {
            "${bytes / 1024}KB"
        }
    }
}
