package com.example.dsh.module

import android.app.Activity
import android.content.Intent
import com.example.dsh.relay.DshQrScanActivity
import com.example.dsh.relay.DshRelayManager
import com.example.dsh.relay.DshRelayNativeState
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import kotlin.concurrent.thread

internal class KRDshRelayModule : KuiklyRenderBaseModule() {
    private var stateListener: ((DshRelayNativeState) -> Unit)? = null
    private var pairCallback: KuiklyRenderCallback? = null

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? = when (method) {
        "scanAndPair" -> {
            pairCallback = callback
            val intent = Intent(activity, DshQrScanActivity::class.java)
            activity?.startActivityForResult(intent, REQUEST_QR)
            null
        }
        "connect" -> {
            DshRelayManager.connect(requireNotNull(context))
            listen(callback)
            null
        }
        "disconnect" -> {
            DshRelayManager.disconnect()
            null
        }
        "forget" -> {
            DshRelayManager.forget()
            callback?.invoke(mapOf("ok" to true))
            null
        }
        "status" -> DshRelayManager.current().toMap()
        "subscribe" -> {
            listen(callback)
            null
        }
        else -> null
    }

    private fun listen(callback: KuiklyRenderCallback?) {
        stateListener?.let(DshRelayManager::removeListener)
        val listener: (DshRelayNativeState) -> Unit = { state ->
            activity?.runOnUiThread { callback?.invoke(state.toMap()) }
        }
        stateListener = listener
        DshRelayManager.addListener(listener)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_QR) return
        val qr = data?.getStringExtra(DshQrScanActivity.EXTRA_QR)
            ?: data?.getStringExtra("qr").orEmpty()
        if (resultCode != Activity.RESULT_OK || qr.isBlank()) {
            pairCallback?.invoke(mapOf("ok" to false, "message" to "已取消扫码"))
            pairCallback = null
            return
        }
        val callback = pairCallback
        pairCallback = null
        val appContext = requireNotNull(context)
        thread(name = "dsh-relay-pair") {
            val result = DshRelayManager.pairFromQr(appContext, qr)
            activity?.runOnUiThread { callback?.invoke(result) }
        }
    }

    private fun DshRelayNativeState.toMap(): Map<String, Any> = mapOf(
        "phase" to phase,
        "message" to message,
        "localPort" to localPort,
        "localToken" to localToken,
        "hostId" to hostId,
        "hostName" to hostName,
        "relayOrigin" to relayOrigin,
        "paired" to paired,
        "generation" to generation,
    )

    companion object {
        const val MODULE_NAME = "DshRelayModule"
        const val REQUEST_QR = 4102
        private var active: KRDshRelayModule? = null

        fun dispatchActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            active?.onActivityResult(requestCode, resultCode, data)
        }
    }

    init {
        active = this
    }
}
