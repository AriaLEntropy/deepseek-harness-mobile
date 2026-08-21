package com.example.dsh.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.example.dsh.KRApplication
import com.example.dsh.KuiklyRenderActivity
import com.example.dsh.ssh.DshSshForegroundService
import com.example.dsh.ssh.DshSshKeyStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

class KRBridgeModule : KuiklyRenderBaseModule() {
    private var navigationBarColorBeforeDim: Int? = null
    private var navigationBarContrastBeforeDim: Boolean? = null
    private var sshKeyCallback: KuiklyRenderCallback? = null

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

            "dateFormatter" -> {
                dateFormatter(params)
            }

            "closeKeyboard" -> {
                closeKeyboard()
            }

            "setSystemBarsDimmed" -> {
                setSystemBarsDimmed(params)
            }

            "pickSshKey" -> pickSshKey(callback)
            "importSshKey" -> importSshKey(params, callback)
            "validateSshKey" -> validateSshKey(params, callback)
            "deleteSshKey" -> deleteSshKey(params)
            "startSshKeepAlive" -> startSshKeepAlive()
            "stopSshKeepAlive" -> stopSshKeepAlive()

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
        if (requestCode != REQUEST_SSH_KEY) return
        val uri = if (resultCode == android.app.Activity.RESULT_OK) data?.data?.toString().orEmpty() else ""
        sshKeyCallback?.invoke(mapOf("uri" to uri))
        sshKeyCallback = null
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
        const val REQUEST_SSH_KEY = 4091
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
