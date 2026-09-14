package com.example.dsh.attachment

import com.example.dsh.attachment.DshFileValidator
import com.example.dsh.attachment.DshImageDraftState
import com.example.dsh.attachment.DshImageLimits
import com.example.dsh.attachment.DshImageValidator
import com.example.dsh.attachment.DshPendingFile
import com.example.dsh.attachment.DshPendingImage

/** 文件候选的接入结果，按候选顺序返回，供页面逐条反馈。 */
sealed interface DshFileIntake {
    data class Accepted(val file: DshPendingFile) : DshFileIntake
    data class Rejected(val reason: String) : DshFileIntake
}

/** 图片候选的接入结果；被拒者仍带回待展示的 INVALID 草稿。 */
sealed interface DshImageIntake {
    data class Accepted(val image: DshPendingImage) : DshImageIntake
    data class Rejected(val image: DshPendingImage, val reason: String) : DshImageIntake
}

/**
 * 附件接入预检（纯逻辑，无页面/平台依赖）。
 *
 * 依次对候选做「现有草稿 + 本候选」的整体预检：通过者进入待发送草稿，
 * 未通过者返回可读原因。Host 仍是最终裁决者，这里只做移动端前置拦截。
 */
object DshAttachmentIntake {

    fun planFiles(existing: List<DshPendingFile>, picked: List<DshPendingFile>): List<DshFileIntake> {
        var accepted = existing
        return picked.map { pending ->
            val rejection = DshFileValidator.reject(accepted, pending)
            if (rejection != null) {
                DshFileIntake.Rejected(rejection)
            } else {
                accepted = accepted + pending
                DshFileIntake.Accepted(pending)
            }
        }
    }

    fun planImages(
        existing: List<DshPendingImage>,
        picked: List<DshPendingImage>,
        limits: DshImageLimits,
    ): List<DshImageIntake> {
        var accepted = existing
        return picked.map { pending ->
            val rejection = DshImageValidator.rejectBatch(accepted, listOf(pending), limits)
            if (rejection != null) {
                val reason = DshImageValidator.rejectionText(rejection)
                DshImageIntake.Rejected(
                    image = pending.copy(state = DshImageDraftState.INVALID, error = reason),
                    reason = reason,
                )
            } else {
                accepted = accepted + pending
                DshImageIntake.Accepted(pending)
            }
        }
    }
}
