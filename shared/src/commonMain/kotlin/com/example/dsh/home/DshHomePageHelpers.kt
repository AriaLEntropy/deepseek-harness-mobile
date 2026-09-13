package com.example.dsh.home

import com.example.dsh.protocol.DshConnectionMode
import com.example.dsh.models.DshModelOption
import com.example.dsh.attachment.DshPendingFile
import com.example.dsh.attachment.DshPendingImage
import com.example.dsh.models.DshProviderModel
import com.example.dsh.models.dshCustomRouteError
import com.example.dsh.infrastructure.currentTimeMillis
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.home.DshHomePage

/**
 * [DshHomePage] 的无状态辅助函数：平台文件/图片选择返回的解析，以及若干纯函数。
 * 与页面状态无关，抽到顶层以便独立阅读与复用。
 */

/** 兼容单文件（旧字段）与 `files` 数组两种原生返回。 */
internal fun parsePickedFiles(result: JSONObject): List<DshPendingFile> {
    val array = result.optJSONArray("files")
    if (array != null) {
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                parsePickedFile(item, i)?.let(::add)
            }
        }
    }
    return listOfNotNull(parsePickedFile(result, 0))
}

internal fun parsePickedFile(item: JSONObject, index: Int): DshPendingFile? {
    val dataUrl = item.optString("dataUrl")
    if (dataUrl.isEmpty()) return null
    return DshPendingFile(
        clientId = "file-${currentTimeMillis()}-$index",
        mediaType = item.optString("mediaType").ifEmpty { "application/octet-stream" },
        name = item.optString("name").ifEmpty { "attachment" },
        dataBase64 = dataUrl.substringAfter("base64,"),
        bytes = item.optString("bytes").toLongOrNull() ?: 0L,
    )
}

/** 把已上传文件句柄拼进 prompt 正文（模型据此读取落盘文件）。 */
internal fun composePromptWithFiles(prompt: String, files: List<DshPendingFile>): String {
    val handles = files.filter { it.handle.isNotEmpty() }.joinToString("\n") { it.handle }
    if (handles.isEmpty()) return prompt
    return if (prompt.isEmpty()) handles else "$prompt\n\n$handles"
}

/** 兼容单张（旧字段）与多张（`images` 数组）两种原生返回。 */
internal fun parsePickedImages(result: JSONObject): List<DshPendingImage> {
    val array = result.optJSONArray("images")
    if (array != null) {
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                parsePickedImage(item, i)?.let(::add)
            }
        }
    }
    return listOfNotNull(parsePickedImage(result, 0))
}

internal fun parsePickedImage(item: JSONObject, index: Int): DshPendingImage? {
    val dataUrl = item.optString("dataUrl")
    if (dataUrl.isEmpty()) return null
    return DshPendingImage(
        clientId = "img-${currentTimeMillis()}-$index",
        mediaType = item.optString("mediaType"),
        name = item.optString("name").ifEmpty { "image" },
        dataBase64 = dataUrl.substringAfter("base64,"),
        previewDataUrl = dataUrl,
        bytes = item.optString("bytes").toLongOrNull() ?: 0L,
        width = item.optString("width").toIntOrNull() ?: 0,
        height = item.optString("height").toIntOrNull() ?: 0,
    )
}

internal fun interactionFailureLabel(reason: String): String = when (reason) {
    "not-pending" -> "这个问题已经失效，请等 Agent 重新提问"
    "bad-response" -> "提交未被接受，请再选一次后重试"
    "缺少请求编号" -> "这个问题已失效，请等 Agent 重新提问"
    "连接尚未就绪" -> "连接尚未就绪，请稍后再试"
    else -> reason.ifEmpty { "提交失败，请重试" }
}

internal fun selectedReasoningEffortName(option: DshModelOption): String =
    option.reasoningEfforts.firstOrNull { it.id == option.reasoningEffort }?.name
        ?: option.reasoningEffort
        ?: ""

internal fun messageRowKey(sessionId: String, messageId: String): String = "$sessionId:$messageId"

internal fun dshConnectionModeLabel(mode: DshConnectionMode): String = when (mode) {
    DshConnectionMode.LOCAL -> "本地模式"
    DshConnectionMode.RELAY -> "扫码连接"
    DshConnectionMode.SSH -> "SSH 连接"
}

internal fun dshReconnectLabel(mode: DshConnectionMode): String = when (mode) {
    DshConnectionMode.SSH -> "远程连接重建中"
    DshConnectionMode.RELAY -> "扫码连接重建中"
    DshConnectionMode.LOCAL -> "本地 DSH 连接重建中"
}

internal fun dshSyncBusyLabel(mode: DshConnectionMode): String = when (mode) {
    DshConnectionMode.SSH -> "远程 DSH 正在同步，暂不能发送"
    DshConnectionMode.RELAY -> "扫码连接正在同步，暂不能发送"
    DshConnectionMode.LOCAL -> "本地 DSH 正在同步，暂不能发送"
}

/** 自定义 Provider 表单校验；通过返回 null，否则返回首条可读错误。 */
internal fun dshValidateCustomProvider(
    route: String,
    baseUrl: String,
    protocol: String,
    models: List<DshProviderModel>,
    takenRoutes: List<String>,
): String? {
    val routeError = dshCustomRouteError(route, takenRoutes)
    if (route.isEmpty() || routeError.isNotEmpty()) return routeError.ifEmpty { "请填写 Provider ID。" }
    if (baseUrl.isEmpty()) return "自定义提供方需要填写 API 地址。"
    if (protocol.isEmpty()) return "请选择 API 协议。"
    if (models.isEmpty() || models.any { it.id.trim().isEmpty() }) return "自定义提供方至少需要一个模型，且模型 ID 不能为空。"
    val ids = models.map { it.id.trim() }
    if (ids.size != ids.toSet().size) return "模型 ID 不能重复。"
    return null
}

/** SSH 连接表单校验结果：通过则携带解析后的端口。 */
internal sealed interface DshSshSettingsValidation {
    data class Valid(val sshPort: Int, val dshPort: Int) : DshSshSettingsValidation
    data class Invalid(val message: String) : DshSshSettingsValidation
}

/** SSH 连接设置校验；保持与界面一致的首错顺序。 */
internal fun dshValidateSshSettings(
    host: String,
    user: String,
    sshPort: String,
    dshPort: String,
    keyId: String,
): DshSshSettingsValidation {
    if (host.isBlank()) return DshSshSettingsValidation.Invalid("请输入 SSH 主机地址")
    if (user.isBlank()) return DshSshSettingsValidation.Invalid("请输入 SSH 用户名")
    val ssh = sshPort.toIntOrNull()
    if (ssh == null || ssh !in 1..65535) return DshSshSettingsValidation.Invalid("SSH 端口无效")
    val dsh = dshPort.toIntOrNull()
    if (dsh == null || dsh !in 1..65535) return DshSshSettingsValidation.Invalid("远程 DSH 端口无效")
    if (keyId.isBlank()) return DshSshSettingsValidation.Invalid("请先导入 SSH 私钥")
    return DshSshSettingsValidation.Valid(ssh, dsh)
}

/** Provider 编辑草稿校验；通过返回 null，否则返回首条可读错误。 */
internal fun dshValidateProviderDraft(models: List<DshProviderModel>): String? {
    if (models.any { it.id.trim().isEmpty() }) return "模型 ID 不能为空。"
    val ids = models.map { it.id.trim() }
    if (ids.size != ids.toSet().size) return "模型 ID 不能重复。"
    return null
}
