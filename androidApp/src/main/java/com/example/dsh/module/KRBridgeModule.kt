package com.example.dsh.module

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.example.dsh.BuildConfig
import com.example.dsh.KRApplication
import com.example.dsh.KuiklyRenderActivity
import com.example.dsh.ssh.DshSshForegroundService
import com.example.dsh.ssh.DshSshKeyStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class KRBridgeModule : KuiklyRenderBaseModule() {
    private var navigationBarColorBeforeDim: Int? = null
    private var navigationBarContrastBeforeDim: Boolean? = null
    private var sshKeyCallback: KuiklyRenderCallback? = null
    private var pickImageCallback: KuiklyRenderCallback? = null
    private var pendingCameraFile: File? = null

    init {
        activeInstance = this
    }

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "ssoRequest" -> {
                ssoRequest(params, callback)
            }

            "showAlert" -> {
                showAlert(params, callback)
            }

            "closePage" -> {
                closePage(params)
            }

            "openPage" -> {
                openPage(params)
            }

            "copyToPasteboard" -> {
                copyToPasteboard(params)
            }

            "toast" -> {
                toast(params)
            }

            "log" -> {
                log(params)
            }

            "reportDT" -> {
                reportDT(params)
            }

            "reportRealtime" -> {
                reportRealtime(params)
            }

            "qqLiveSSORequest" -> {
                qqLiveSSORequest(params, callback)
            }

            "localServeTime" -> {
                localServeTime(params, callback)
            }

            "currentTimestamp" -> {
                currentTimestamp(params)
            }

            "timezoneOffset" -> {
                timezoneOffset()
            }

            "dateFormatter" -> {
                dateFormatter(params)
            }

            "closeKeyboard" -> {
                closeKeyboard()
            }

            "setSystemBarsDimmed" -> {
                setSystemBarsDimmed(params)
            }

            "pickImage" -> pickImage(params, callback)
            "saveImage" -> saveImage(params, callback)
            "pickSshKey" -> pickSshKey(callback)
            "importSshKey" -> importSshKey(params, callback)
            "validateSshKey" -> validateSshKey(params, callback)
            "deleteSshKey" -> deleteSshKey(params)
            "startSshKeepAlive" -> startSshKeepAlive()
            "stopSshKeepAlive" -> stopSshKeepAlive()
            "shareExportFile" -> shareExportFile(params, callback)
            "htmlToPdf" -> htmlToPdf(params, callback)
            "readLastCrash" -> readLastCrash(params)
            "clearLastCrash" -> clearLastCrash(callback)
            "getDeviceInfo" -> getDeviceInfo(params)

            else -> callback?.invoke(
                mapOf(
                    "code" to -1,
                    "message" to "方法不存在"
                )
            )
        }
    }

    private fun reportRealtime(params: String?) {
    }

    private fun reportDT(params: String?) {
    }

    private fun log(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        Log.i("KuiklyRender", paramJSON.optString("content"))
    }

    private fun toast(params: String?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        Toast.makeText(
            KRApplication.application,
            paramJSON.optString("content"),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun shareExportFile(params: String?, callback: KuiklyRenderCallback?) {
        val paramJSON = JSONObject(params ?: "{}")
        val path = paramJSON.optString("path")
        if (path.isEmpty()) { callback?.invoke(mapOf("ok" to false, "message" to "缺少文件路径")); return }
        val ctx = context ?: KRApplication.application
        val file = File(path)
        if (!file.isFile) { callback?.invoke(mapOf("ok" to false, "message" to "导出文件不存在")); return }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val mime = paramJSON.optString("mime").ifEmpty { mimeForFile(file) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "分享导出文件")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
            // ACTION_SEND only confirms that the chooser opened, not that the recipient saved the file.
            callback?.invoke(mapOf("ok" to true, "message" to "分享面板已打开"))
        } catch (e: Exception) {
            // FileProvider 路径未配置（如导出目录变更）时兜底，避免崩溃
            Log.e("KRBridgeModule", "shareExportFile failed", e)
            Toast.makeText(ctx, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            callback?.invoke(mapOf("ok" to false, "message" to "无法打开分享面板"))
        }
    }

    private fun mimeForFile(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "html", "htm" -> "text/html"
        "md" -> "text/markdown"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    /**
     * 走 Android 系统打印框架把 HTML 渲染为 PDF。
     *
     * `PrintDocumentAdapter.LayoutResultCallback`/`WriteResultCallback` 的构造函数是包私有的，
     * 无法在 Kotlin 中静默驱动适配器，因此使用 WebView 自带的打印适配器配合 `PrintManager`：
     * 用户在系统面板选择「另存为 PDF」完成导出（官方支持的 HTML→PDF 路径，自动分页）。
     */
    private fun htmlToPdf(params: String?, callback: KuiklyRenderCallback?) {
        val paramJSON = JSONObject(params ?: "{}")
        val html = paramJSON.optString("html")
        if (html.isEmpty()) {
            callback?.invoke(mapOf("ok" to false, "message" to "缺少 HTML 内容"))
            return
        }
        val ctx = context ?: KRApplication.application
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            fun finish(ok: Boolean, message: String) {
                if (!finished.compareAndSet(false, true)) return
                callback?.invoke(mapOf("ok" to ok, "path" to "", "message" to message))
            }
            try {
                val view = android.webkit.WebView(ctx)
                view.settings.javaScriptEnabled = false
                view.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        try {
                            val adapter = view?.createPrintDocumentAdapter("dsh-session")
                            if (adapter == null) {
                                finish(false, "无法创建打印适配器")
                                return
                            }
                            val printManager = ctx.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                            val attributes = android.print.PrintAttributes.Builder()
                                .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                                .build()
                            printManager.print("DSH 对话", adapter, attributes)
                            finish(true, "已打开系统打印，可选择「另存为 PDF」")
                        } catch (e: Exception) {
                            Log.e("KRBridgeModule", "htmlToPdf print failed", e)
                            finish(false, "生成 PDF 失败：${e.message}")
                        }
                    }

                    override fun onReceivedError(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?,
                    ) {
                        finish(false, "HTML 加载失败")
                    }
                }
                view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                handler.postDelayed({ finish(false, "生成 PDF 超时，请重试") }, 20_000)
            } catch (e: Exception) {
                Log.e("KRBridgeModule", "htmlToPdf failed", e)
                finish(false, "生成 PDF 失败：${e.message}")
            }
        }
    }

    private fun readLastCrash(params: String?): String {
        val file = File(KRApplication.application.filesDir, "last_crash.txt")
        return if (file.exists()) file.readText() else ""
    }

    private fun clearLastCrash(callback: KuiklyRenderCallback?) {
        val ok = runCatching {
            val file = File(KRApplication.application.filesDir, "last_crash.txt")
            !file.exists() || file.delete()
        }.getOrDefault(false)
        callback?.invoke(mapOf("ok" to ok))
    }

    private fun getDeviceInfo(params: String?): String {
        return JSONObject().apply {
            put("version", BuildConfig.VERSION_NAME)
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }.toString()
    }

    private fun copyToPasteboard(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        (context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.setPrimaryClip(ClipData.newPlainText(MODULE_NAME, paramJSON.optString("content")))
        }
    }

    private fun openPage(params: String?) {
        if (params == null) {
            return
        }
        val ctx = context ?: return
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
    }

    private fun closePage(params: String?) {
        activity?.finish()
    }

    private fun showAlert(params: String?, callback: KuiklyRenderCallback?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        val titleText = paramJSON.optString("title")
        val message = paramJSON.optString("message")
        val buttons = paramJSON.optJSONArray("buttons") ?: JSONArray()
    }

    private fun ssoRequest(params: String?, callback: KuiklyRenderCallback?) {}

    private fun qqLiveSSORequest(params: String?, callback: KuiklyRenderCallback?) {
    }

    private fun localServeTime(params: String?, callback: KuiklyRenderCallback?) {
        val time = (System.currentTimeMillis() / 1000.0)
        callback?.invoke(
            mapOf(
                "time" to time
            )
        )
    }

    private fun currentTimestamp(params: String?): String {
        return (System.currentTimeMillis()).toString()
    }

    /** 设备本地时区偏移（毫秒，UTC→本地为正），供 JS 侧时间格式化（QuickJS 无时区数据）。 */
    private fun timezoneOffset(): String {
        return java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toString()
    }

    private fun dateFormatter(params: String?): String {
        val paramJSONObject = JSONObject(params ?: "{}")
        val data = Date(paramJSONObject.optLong("timeStamp"))
        val format = SimpleDateFormat(paramJSONObject.optString("format"))
        return format.format(data)
    }

    private fun closeKeyboard(): String {
        activity?.runOnUiThread {
            val focusedView = activity?.currentFocus
            focusedView?.clearFocus()
            val inputMethodManager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager?.hideSoftInputFromWindow(focusedView?.windowToken, 0)
        }
        return "true"
    }

    private fun setSystemBarsDimmed(params: String?) {
        val dimmed = JSONObject(params ?: "{}").optBoolean("dimmed")
        activity?.runOnUiThread {
            val window = activity?.window ?: return@runOnUiThread
            if (dimmed) {
                if (navigationBarColorBeforeDim == null) {
                    navigationBarColorBeforeDim = window.navigationBarColor
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        navigationBarContrastBeforeDim = window.isNavigationBarContrastEnforced
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                window.navigationBarColor = Color.rgb(153, 153, 153)
            } else {
                restoreNavigationBar()
            }
        }
    }

    private fun pickImage(params: String?, callback: KuiklyRenderCallback?) {
        val source = JSONObject(params ?: "{}").optString("source", "album")
        pickImageCallback = callback
        val intent: Intent
        val requestCode: Int
        when (source) {
            "camera" -> {
                val packageManager = activity?.packageManager
                val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (packageManager != null && captureIntent.resolveActivity(packageManager) == null) {
                    finishPickImage(mapOf("ok" to false, "error" to "未找到可用的相机应用"))
                    return
                }
                val cacheDir = context?.cacheDir ?: run {
                    finishPickImage(mapOf("ok" to false, "error" to "无法访问缓存目录"))
                    return
                }
                val photoDir = File(cacheDir, "camera").apply { mkdirs() }
                val photoFile = File(photoDir, "dsh_capture_${System.currentTimeMillis()}.jpg")
                val uri = try {
                    FileProvider.getUriForFile(
                        requireNotNull(context),
                        requireNotNull(context).packageName + ".fileprovider",
                        photoFile,
                    )
                } catch (e: Exception) {
                    finishPickImage(mapOf("ok" to false, "error" to "相机文件创建失败"))
                    return
                }
                pendingCameraFile = photoFile
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent = captureIntent
                requestCode = REQUEST_CAPTURE_PHOTO
            }

            else -> {
                intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                requestCode = REQUEST_PICK_IMAGE
            }
        }
        try {
            activity?.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            finishPickImage(mapOf("ok" to false, "error" to "无法打开${if (source == "camera") "相机" else "相册"}"))
        }
    }

    private fun finishPickImage(result: Map<String, Any?>) {
        Log.i("HRBridgePick", "finishPickImage ok=${result["ok"]} cancelled=${result["cancelled"]} err=${result["error"]} bytes=${result["bytes"]} mime=${result["mediaType"]} cb=${pickImageCallback != null}")
        try {
            pickImageCallback?.invoke(result)
        } catch (t: Throwable) {
        }
        pickImageCallback = null
        pendingCameraFile = null
    }

    private fun readPickedBytes(uri: Uri?): ByteArray? {
        if (uri == null) return null
        return try {
            context?.contentResolver?.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeImageMeta(bytes: ByteArray): Triple<String?, Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return Triple(options.outMimeType, options.outWidth, options.outHeight)
    }

    private fun deliverPickedImages(images: List<Pair<ByteArray?, String>>) {
        val entries = mutableListOf<Map<String, Any?>>()
        for ((bytes, displayName) in images) {
            if (bytes == null || bytes.isEmpty()) continue
            val (mime, width, height) = decodeImageMeta(bytes)
            if (mime.isNullOrEmpty() || width <= 0 || height <= 0) continue
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            entries.add(
                mapOf(
                    "dataUrl" to "data:$mime;base64,$base64",
                    "mediaType" to mime,
                    "name" to displayName,
                    "bytes" to bytes.size.toString(),
                    "width" to width.toString(),
                    "height" to height.toString(),
                )
            )
        }
        if (entries.isEmpty()) {
            finishPickImage(mapOf("ok" to false, "error" to "无法读取图片数据"))
            return
        }
        Log.i("HRBridgePick", "picked ${entries.size} image(s)")
        finishPickImage(mapOf("ok" to true, "images" to entries))
    }

    private fun deliverPickedImage(bytes: ByteArray?, displayName: String) =
        deliverPickedImages(listOf(bytes to displayName))

    private fun queryDisplayName(uri: Uri): String {
        var name = "image"
        try {
            context?.contentResolver?.query(
                uri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotEmpty() }?.let { name = it }
                }
            }
        } catch (e: Exception) {
            // 保留默认名
        }
        return name
    }

    private fun saveImage(params: String?, callback: KuiklyRenderCallback?) {
        try {
            val dataUrl = JSONObject(params ?: "{}").optString("dataUrl", "")
            if (dataUrl.isEmpty()) {
                callback?.invoke(mapOf("ok" to false, "error" to "空图片数据"))
                return
            }
            // 解析 dataUrl: data:<mime>;base64,<base64>
            val commaIdx = dataUrl.indexOf(',')
            if (commaIdx < 0 || !dataUrl.startsWith("data:")) {
                callback?.invoke(mapOf("ok" to false, "error" to "无效的 dataUrl 格式"))
                return
            }
            val header = dataUrl.substring(5, commaIdx)
            val mime = header.substringBefore(';').ifEmpty { "image/png" }
            val base64 = dataUrl.substring(commaIdx + 1)
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes == null || bytes.isEmpty()) {
                callback?.invoke(mapOf("ok" to false, "error" to "图片解码失败"))
                return
            }
            // 保存到系统相册
            val resolver = context?.contentResolver
            if (resolver == null) {
                callback?.invoke(mapOf("ok" to false, "error" to "Context 不可用"))
                return
            }
            val displayName = "DSH_${System.currentTimeMillis()}"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DSH")
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                callback?.invoke(mapOf("ok" to false, "error" to "创建相册条目失败"))
                return
            }
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            callback?.invoke(mapOf("ok" to true))
        } catch (e: Exception) {
            val errMsg = e.message ?: "未知错误"
            callback?.invoke(mapOf("ok" to false, "error" to errMsg))
        }
    }

    private fun pickSshKey(callback: KuiklyRenderCallback?) {
        sshKeyCallback = callback
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }
        activity?.startActivityForResult(intent, REQUEST_SSH_KEY)
    }

    private fun importSshKey(params: String?, callback: KuiklyRenderCallback?) {
        val uri = Uri.parse(JSONObject(params ?: "{}").optString("uri"))
        val bytes = context?.contentResolver?.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            callback?.invoke(mapOf("ok" to false, "message" to "无法读取 SSH 私钥"))
            return
        }
        val keyId = DshSshKeyStore(requireNotNull(context)).importBytes("ssh-key", bytes)
        bytes.fill(0)
        callback?.invoke(mapOf("ok" to true, "keyId" to keyId))
    }

    private fun deleteSshKey(params: String?) {
        DshSshKeyStore(requireNotNull(context)).delete(JSONObject(params ?: "{}").optString("keyId"))
    }

    private fun validateSshKey(params: String?, callback: KuiklyRenderCallback?) {
        val keyId = JSONObject(params ?: "{}").optString("keyId")
        val valid = runCatching { DshSshKeyStore(requireNotNull(context)).validateKey(keyId) }.getOrDefault(false)
        callback?.invoke(mapOf("valid" to valid))
    }

    private fun startSshKeepAlive() {
        val intent = Intent(context, DshSshForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context?.startForegroundService(intent) else context?.startService(intent)
    }

    private fun stopSshKeepAlive() {
        context?.stopService(Intent(context, DshSshForegroundService::class.java))
    }

    private fun restoreNavigationBar() {
        val window = activity?.window ?: return
        navigationBarColorBeforeDim?.let { window.navigationBarColor = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            navigationBarContrastBeforeDim?.let { window.isNavigationBarContrastEnforced = it }
        }
        navigationBarColorBeforeDim = null
        navigationBarContrastBeforeDim = null
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        restoreNavigationBar()
        super.onDestroy()
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i("HRBridgePick", "onActivityResult rc=$requestCode result=$resultCode data=${data?.data}")
        when (requestCode) {
            REQUEST_SSH_KEY -> {
                val uri = if (resultCode == android.app.Activity.RESULT_OK) data?.data?.toString().orEmpty() else ""
                sshKeyCallback?.invoke(mapOf("uri" to uri))
                sshKeyCallback = null
            }

            REQUEST_PICK_IMAGE -> {
                if (resultCode != android.app.Activity.RESULT_OK) {
                    finishPickImage(mapOf("ok" to false, "cancelled" to true))
                    return
                }
                val uris = mutableListOf<Uri>()
                val clip = data?.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) clip.getItemAt(i)?.uri?.let(uris::add)
                }
                data?.data?.let { if (it !in uris) uris.add(it) }
                if (uris.isEmpty()) {
                    finishPickImage(mapOf("ok" to false, "cancelled" to true))
                    return
                }
                deliverPickedImages(uris.map { uri -> readPickedBytes(uri) to queryDisplayName(uri) })
            }

            REQUEST_CAPTURE_PHOTO -> {
                if (resultCode != android.app.Activity.RESULT_OK) {
                    finishPickImage(mapOf("ok" to false, "cancelled" to true))
                    return
                }
                val file = pendingCameraFile
                if (file == null || !file.exists()) {
                    finishPickImage(mapOf("ok" to false, "error" to "相机照片读取失败"))
                    return
                }
                val bytes = try { file.readBytes() } catch (e: Exception) { null }
                val name = file.name
                file.delete()
                pendingCameraFile = null
                deliverPickedImage(bytes, name)
            }
        }
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
        const val REQUEST_SSH_KEY = 4091
        const val REQUEST_PICK_IMAGE = 4092
        const val REQUEST_CAPTURE_PHOTO = 4093
        private var activeInstance: KRBridgeModule? = null

        fun dispatchActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            activeInstance?.onActivityResult(requestCode, resultCode, data)
        }
    }
}

private fun JSONObject.toMap(): Map<Any, Any> {
    val map = mutableMapOf<Any, Any>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        when (val v = opt(key)) {
            is JSONObject -> {
                map[key] = v.toMap()
            }

            else -> {
                v?.also {
                    map[key] = it
                }
            }
        }
    }
    return map
}
