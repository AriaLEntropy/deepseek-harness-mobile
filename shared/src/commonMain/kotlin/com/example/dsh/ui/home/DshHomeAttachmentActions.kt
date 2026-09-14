package com.example.dsh.ui.home

import com.example.dsh.ui.chat.DshCommandSheetTile
import com.example.dsh.attachment.DshFileDraftState
import com.example.dsh.attachment.DshImageDraftState
import com.example.dsh.attachment.DshImageLimits
import com.example.dsh.attachment.DshPendingFile
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.attachment.DshAttachmentIntake
import com.example.dsh.attachment.DshFileIntake
import com.example.dsh.attachment.DshImageIntake
import com.example.dsh.attachment.parsePickedFiles
import com.example.dsh.attachment.parsePickedImages

internal fun DshHomePage.onAttachmentTile(tile: DshCommandSheetTile) {
    ui.commandSheetVisible = false
    when (tile) {
        DshCommandSheetTile.CAMERA -> pickImageFrom("camera")
        DshCommandSheetTile.GALLERY -> pickImageFrom("album")
        DshCommandSheetTile.FILE -> pickFileFrom()
    }
}

/** 平台选文件 → 解析 → 大小/数量预检 → 加入输入区草稿；取消静默，失败 toast。 */

internal fun DshHomePage.pickFileFrom() {
    val expectedSession = ui.activeSessionId
    val expectedConnection = activeConnectionId
    bridgeModule.pickFile { raw ->
        if (!pageAlive || expectedSession != ui.activeSessionId || expectedConnection != activeConnectionId) return@pickFile
        val result = runCatching {
            com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw)
        }.getOrNull()
        if (result == null || !result.optBoolean("ok")) {
            if (result?.optBoolean("cancelled") == true) return@pickFile
            val error = result?.optString("error").orEmpty().ifEmpty { "选择文件失败" }
            bridgeModule.toast(error)
            return@pickFile
        }
        val picked = try {
            parsePickedFiles(result)
        } catch (t: Throwable) {
            emptyList()
        }
        if (picked.isEmpty()) {
            bridgeModule.toast("文件解析失败")
            return@pickFile
        }
        DshAttachmentIntake.planFiles(ui.pendingFiles.toList(), picked).forEach { intake ->
            when (intake) {
                is DshFileIntake.Accepted -> ui.pendingFiles.add(intake.file)
                is DshFileIntake.Rejected -> bridgeModule.toast(intake.reason)
            }
        }
        ui.attachmentEpoch += 1
    }
}

internal fun DshHomePage.removePendingFile(clientId: String) {
    ui.pendingFiles.removeAll { it.clientId == clientId }
    ui.attachmentEpoch += 1
}

internal fun DshHomePage.retryPendingFile(clientId: String) {
    val index = ui.pendingFiles.indexOfFirst { it.clientId == clientId }
    if (index < 0) return
    val file = ui.pendingFiles[index]
    if (file.state != DshFileDraftState.FAILED) return
    ui.pendingFiles[index] = file.copy(
        state = DshFileDraftState.SELECTED,
        error = "",
        handle = "",
        path = "",
        sha256 = "",
    )
    ui.attachmentEpoch += 1
}

/**
 * 为用户消息（附件已移出输入区）上传文件草稿；全部成功回调 true 并回传带 handle 的列表，
 * 任一步失败回调 false。上传不改动输入区状态，避免附件在输入框里显示上传中。
 */

internal fun DshHomePage.uploadFilesForMessage(
    sessionId: String,
    files: List<DshPendingFile>,
    onDone: (Boolean, List<DshPendingFile>) -> Unit,
) {
    val hostRepository = remoteRepo
    if (hostRepository == null) {
        onDone(false, files)
        return
    }
    val results = files.toMutableList()
    var index = 0
    fun uploadNext() {
        if (index >= files.size) {
            onDone(true, results.toList())
            return
        }
        val file = files[index]
        hostRepository.uploadAttachment(sessionId, file.name, file.mediaType, file.dataBase64) { uploaded, error ->
            if (!pageAlive || ui.activeSessionId != sessionId) return@uploadAttachment
            if (error != null || uploaded == null) {
                bridgeModule.toast(error?.message ?: "附件上传失败")
                onDone(false, results.toList())
                return@uploadAttachment
            }
            results[index] = file.copy(
                state = DshFileDraftState.SELECTED,
                path = uploaded.path,
                sha256 = uploaded.sha256,
                handle = uploaded.handle,
                error = "",
            )
            index += 1
            uploadNext()
        }
    }
    uploadNext()
}

/** 平台取图 → 解析 → ui.imageLimits 预检 → 加入输入区草稿；取消静默，失败 toast。 */

internal fun DshHomePage.pickImageFrom(source: String) {
    val expectedSession = ui.activeSessionId
    val expectedConnection = activeConnectionId
    bridgeModule.pickImage(source) { raw ->
        if (!pageAlive || expectedSession != ui.activeSessionId || expectedConnection != activeConnectionId) return@pickImage
        val result = runCatching {
            com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw)
        }.getOrNull()
        if (result == null || !result.optBoolean("ok")) {
            if (result?.optBoolean("cancelled") == true) return@pickImage
            val error = result?.optString("error").orEmpty().ifEmpty { "取图失败" }
            DshStreamLog.log(LogLevel.ERROR, "app.image.failed", "source=$source error='${DshStreamLog.preview(error)}'", expectedSession)
            bridgeModule.toast(error)
            return@pickImage
        }
        val picked = try {
            parsePickedImages(result)
        } catch (t: Throwable) {
            DshStreamLog.log(LogLevel.ERROR, "app.image.failed", "phase=parse error='${DshStreamLog.preview(t.message.orEmpty())}'", expectedSession)
            emptyList()
        }
        if (picked.isEmpty()) {
            bridgeModule.toast("取图解析失败")
            return@pickImage
        }
        val limits = ui.imageLimits ?: DshImageLimits.DEFAULT
        val intakes = try {
            DshAttachmentIntake.planImages(ui.pendingImages.toList(), picked, limits)
        } catch (t: Throwable) {
            DshStreamLog.log(LogLevel.ERROR, "app.image.failed", "phase=validate error='${DshStreamLog.preview(t.message.orEmpty())}'", expectedSession)
            picked.map { DshImageIntake.Accepted(it) }
        }
        intakes.forEach { intake ->
            when (intake) {
                is DshImageIntake.Accepted -> {
                    val pending = intake.image
                    ui.pendingImages.add(pending)
                    DshStreamLog.log(
                        LogLevel.INFO, "app.image.selected",
                        "source=$source mediaType=${pending.mediaType} width=${pending.width} height=${pending.height} bytes=${pending.bytes}",
                        expectedSession,
                    )
                }
                is DshImageIntake.Rejected -> {
                    val pending = intake.image
                    ui.pendingImages.add(pending)
                    bridgeModule.toast(intake.reason)
                    DshStreamLog.log(
                        LogLevel.WARN, "app.image.rejected",
                        "source=$source mediaType=${pending.mediaType} width=${pending.width} height=${pending.height} bytes=${pending.bytes} reason=${intake.reason}",
                        expectedSession,
                    )
                }
            }
        }
        ui.attachmentEpoch += 1
    }
}

/** 保存预览图片到系统相册，成功/失败 toast 反馈。 */

internal fun DshHomePage.saveImageToGallery(dataUrl: String) {
    bridgeModule.saveImage(dataUrl) { raw ->
        val result = runCatching {
            com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw)
        }.getOrNull()
        if (result?.optBoolean("cancelled") == true) return@saveImage
        if (result != null && result.optBoolean("ok")) {
            bridgeModule.toast("已保存到相册")
        } else {
            val error = result?.optString("error").orEmpty().ifEmpty { "保存失败" }
            bridgeModule.toast(error)
        }
    }
}

internal fun DshHomePage.removePendingImage(clientId: String) {
    ui.pendingImages.removeAll { it.clientId == clientId }
    ui.attachmentEpoch += 1
}

internal fun DshHomePage.retryPendingImage(clientId: String) {
    val index = ui.pendingImages.indexOfFirst { it.clientId == clientId }
    if (index < 0) return
    val image = ui.pendingImages[index]
    if (image.state == DshImageDraftState.INVALID) {
        bridgeModule.toast(image.error.ifEmpty { "图片不符合发送要求，请重新选择" })
        return
    }
    if (image.state != DshImageDraftState.FAILED) return
    ui.pendingImages[index] = image.copy(state = DshImageDraftState.SELECTED, error = "")
    ui.attachmentEpoch += 1
}

/** 切换「+」命令半屏面板；打开时收起键盘 */
