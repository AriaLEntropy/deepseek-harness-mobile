package com.example.dsh.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 毛玻璃背景能力模块（跨平台统一 API）。
 *
 * 截取当前页面视图并模糊，返回模糊图片 URI。任何需要毛玻璃背景的组件
 * （上下文菜单、弹窗、卡片等）都可以复用：
 *
 * ```kotlin
 * blurModule.captureBlur(8) { uri ->
 *     if (uri.isNotEmpty()) { /* 用 uri 作为背景 */ }
 * }
 * ```
 *
 * 各平台实现：
 * - Android：视图截图 → 降采样 4x → 模糊（API 31+ 用 RenderEffect，否则 CPU BoxBlur）
 * - iOS：UIVisualEffectView
 * - 鸿蒙：backdropBlur
 * - H5：CSS backdrop-filter
 */
internal class DshBlurModule : Module() {

    override fun moduleName(): String = MODULE_NAME

    /**
     * 截取当前页面视图并模糊，异步返回模糊图片 URI。
     *
     * @param radius 模糊半径，建议 5-10
     * @param callback 回调模糊图片 URI；失败时返回空字符串
     */
    fun captureBlur(radius: Int, callback: (String) -> Unit) {
        val args = JSONObject().apply { put("radius", radius) }
        callNativeMethod(CAPTURE_BLUR, args) { value ->
            callback(value?.optString("uri").orEmpty())
        }
    }

    private fun callNativeMethod(methodName: String, data: JSONObject?, callbackFn: CallbackFn?) {
        toNative(false, methodName, data?.toString(), callbackFn, false)
    }

    companion object {
        const val MODULE_NAME = "DshBlurModule"
        const val CAPTURE_BLUR = "captureBlur"
    }
}
