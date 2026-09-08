package com.example.dsh

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.example.dsh.adapter.KRColorParserAdapter
import com.example.dsh.adapter.KRFontAdapter
import com.example.dsh.adapter.KRImageAdapter
import com.example.dsh.adapter.KRLogAdapter
import com.example.dsh.adapter.KRRouterAdapter
import com.example.dsh.adapter.KRThreadAdapter
import com.example.dsh.adapter.KRUncaughtExceptionHandlerAdapter
import com.example.dsh.module.KRBridgeModule
import com.example.dsh.module.KRBlurModule
import com.example.dsh.module.KRDshEngineModule
import com.example.dsh.module.KRDshRelayModule
import com.example.dsh.module.KRDshWebSocketModule
import com.example.dsh.module.KRShareModule
import com.tencent.kuiklybase.android.KRWebView
import org.json.JSONObject

class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var hrContainerView: ViewGroup
    private lateinit var loadingView: View
    private lateinit var errorView: View

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)

    private val pageName: String
        get() {
            val pn = intent.getStringExtra(KEY_PAGE_NAME) ?: ""
            return if (pn.isNotEmpty()) {
                return pn
            } else {
                "connection_setup"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_hr)
        setupImmersiveMode()
        hrContainerView = findViewById(R.id.hr_container)
        loadingView = findViewById(R.id.hr_loading)
        errorView = findViewById(R.id.hr_error)
        kuiklyRenderViewDelegator.onAttach(hrContainerView, "", pageName, createPageData())
    }

    override fun softInputMode(): Int? = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

    /**
     * 触摸落在聚焦输入框之外的区域时，清除输入框焦点并收起键盘（移动端标准行为）：
     * 覆盖点击空白、点击列表/筛选控件、以及滑动日志列表等场景，避免输入框持续持有焦点。
     * IME 候选条/键盘属于独立输入法窗口，其触摸事件不会经过本 Activity 分发，不会误伤候选词选择。
     */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is android.widget.EditText) {
                val loc = IntArray(2)
                focused.getLocationOnScreen(loc)
                val hit = ev.rawX >= loc[0] && ev.rawX <= loc[0] + focused.width &&
                    ev.rawY >= loc[1] && ev.rawY <= loc[1] + focused.height
                if (!hit) {
                    focused.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(focused.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        super.onDestroy()
        kuiklyRenderViewDelegator.onDetach()
    }

    override fun onPause() {
        super.onPause()
        kuiklyRenderViewDelegator.onPause()
    }

    @Deprecated("Android dispatches legacy activity results to this host for the current app target")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        KRBridgeModule.dispatchActivityResult(requestCode, resultCode, data)
        KRDshRelayModule.dispatchActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        kuiklyRenderViewDelegator.onResume()
    }

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalModule(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            moduleExport(KRBridgeModule.MODULE_NAME) {
                KRBridgeModule()
            }
            moduleExport(KRBlurModule.MODULE_NAME) {
                KRBlurModule()
            }
            moduleExport(KRShareModule.MODULE_NAME) {
                KRShareModule()
            }
            moduleExport(KRDshEngineModule.MODULE_NAME) {
                KRDshEngineModule()
            }
            moduleExport(KRDshRelayModule.MODULE_NAME) {
                KRDshRelayModule()
            }
            moduleExport(KRDshWebSocketModule.MODULE_NAME) {
                KRDshWebSocketModule()
            }
        }
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            renderViewExport(KRWebView.VIEW_NAME, { context -> KRWebView(context) }, null)
            // 覆盖内置 KRTextFieldView：关闭系统拼写检查，规避模拟器/低端设备输入 ANR
            renderViewExport(
                com.tencent.kuikly.core.render.android.expand.component.KRTextFieldView.VIEW_NAME,
                { context -> DshNoSpellCheckTextField(context, softInputMode()) },
                null
            )
        }
    }

    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        param["embeddedEngine"] = false
        param["databaseDir"] = java.io.File(KRApplication.application.filesDir.parentFile, "databases").apply {
            if (!exists()) mkdirs()
        }.absolutePath
        param["exportDir"] = java.io.File(KRApplication.application.getExternalFilesDir(null), "exports").apply {
            if (!exists()) mkdirs()
        }.absolutePath
        return param
    }

    private fun argsToMap(): MutableMap<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA) ?: return mutableMapOf()
        return JSONObject(jsonStr).toMap()
    }

    private fun setupImmersiveMode() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window?.statusBarColor = Color.TRANSPARENT
            window?.decorView?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

    }

    companion object {

        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"

        init {
            initKuiklyAdapter()
        }

        fun start(context: Context, pageName: String, pageData: JSONObject) {
            val starter = Intent(context, KuiklyRenderActivity::class.java)
            starter.putExtra(KEY_PAGE_NAME, pageName)
            starter.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(starter)
        }

        private fun initKuiklyAdapter() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter(KRApplication.application)
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRUncaughtExceptionHandlerAdapter
                krFontAdapter = KRFontAdapter
                krColorParseAdapter = KRColorParserAdapter(KRApplication.application)
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }
    }
}
