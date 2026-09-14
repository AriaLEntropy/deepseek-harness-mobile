package com.example.dsh.attachment

import com.example.dsh.platform.currentTimeMillis
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 平台文件/图片选择返回的解析：兼容单条（旧字段）与数组两种原生返回。 */

/** 兼容单文件（旧字段）与 `files` 数组两种原生返回。 */
fun parsePickedFiles(result: JSONObject): List<DshPendingFile> {
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

fun parsePickedFile(item: JSONObject, index: Int): DshPendingFile? {
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
fun composePromptWithFiles(prompt: String, files: List<DshPendingFile>): String {
    val handles = files.filter { it.handle.isNotEmpty() }.joinToString("\n") { it.handle }
    if (handles.isEmpty()) return prompt
    return if (prompt.isEmpty()) handles else "$prompt\n\n$handles"
}

/** 兼容单张（旧字段）与多张（`images` 数组）两种原生返回。 */
fun parsePickedImages(result: JSONObject): List<DshPendingImage> {
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

fun parsePickedImage(item: JSONObject, index: Int): DshPendingImage? {
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
