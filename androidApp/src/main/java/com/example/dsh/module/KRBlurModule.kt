package com.example.dsh.module

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 毛玻璃背景能力（Android 实现）。
 *
 * 流程：主线程截图当前页面 → 后台线程降采样 4x → 模糊
 * （API 31+ 用 RenderEffect GPU 加速，否则 CPU BoxBlur）→ 保存 JPEG → 返回 URI。
 */
class KRBlurModule : KuiklyRenderBaseModule() {

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "captureBlur" -> captureBlur(params, callback)
            else -> null
        }
    }

    private fun captureBlur(params: String?, callback: KuiklyRenderCallback?) {
        android.util.Log.i("DshBlur", "captureBlur called, params=$params, activity=${activity != null}")
        val radius = JSONObject(params ?: "{}").optInt("radius", 8)
        // 截取页面视图（KuiklyRenderView）而非 decorView：
        // 页面坐标 pageX/pageY 以页面视图为基准，截页面视图才能与菜单卡片偏移对齐，
        // 且不包含状态栏/导航栏，避免模糊图尺寸与页面不一致。
        val view = kuiklyRenderContext?.kuiklyRenderRootView?.view
        if (view == null) {
            android.util.Log.i("DshBlur", "view is null, returning empty")
            callback?.invoke(mapOf("uri" to ""))
            return
        }
        // View 非线程安全，截图必须在主线程
        activity?.runOnUiThread {
            val full = try {
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            } catch (e: Throwable) {
                android.util.Log.i("DshBlur", "createBitmap failed: $e")
                callback?.invoke(mapOf("uri" to ""))
                return@runOnUiThread
            }
            try {
                view.draw(Canvas(full))
            } catch (e: Throwable) {
                android.util.Log.i("DshBlur", "view.draw failed: $e")
                full.recycle()
                callback?.invoke(mapOf("uri" to ""))
                return@runOnUiThread
            }
            // 模糊在后台线程，避免阻塞 UI
            Thread {
                try {
                    val scale = 4
                    val small = Bitmap.createScaledBitmap(
                        full,
                        (full.width / scale).coerceAtLeast(1),
                        (full.height / scale).coerceAtLeast(1),
                        true
                    )
                    full.recycle()
                    val blurred = blur(small, (radius / scale).coerceAtLeast(1))
                    small.recycle()
                    val file = File(context?.cacheDir, "dsh_blur_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        blurred.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    blurred.recycle()
                    android.util.Log.i("DshBlur", "blur file saved: ${file.absolutePath}")
                    val uri = Uri.fromFile(file).toString()
                    activity?.runOnUiThread { callback?.invoke(mapOf("uri" to uri)) }
                } catch (e: Throwable) {
                    android.util.Log.i("DshBlur", "blur thread failed: $e")
                    activity?.runOnUiThread { callback?.invoke(mapOf("uri" to "")) }
                }
            }.start()
        }
    }

    private fun blur(src: Bitmap, radius: Int): Bitmap {
        // Bitmap Canvas 是软件渲染，RenderNode/RenderEffect 需要硬件加速，
        // 因此统一走 CPU BoxBlur（图片已 4x 降采样，性能足够）
        return boxBlur(src, radius)
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val srcPixels = IntArray(w * h)
        val hBlur = IntArray(w * h)
        val dstPixels = IntArray(w * h)
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val div = radius * 2 + 1

        // 水平方向滑动窗口模糊
        for (y in 0 until h) {
            val rowStart = y * w
            var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
            for (x in -radius..radius) {
                val px = srcPixels[rowStart + x.coerceIn(0, w - 1)]
                sumA += (px ushr 24) and 0xFF
                sumR += (px ushr 16) and 0xFF
                sumG += (px ushr 8) and 0xFF
                sumB += px and 0xFF
            }
            for (x in 0 until w) {
                val idx = rowStart + x
                hBlur[idx] = ((sumA / div) shl 24) or ((sumR / div) shl 16) or
                        ((sumG / div) shl 8) or (sumB / div)
                val addPx = srcPixels[rowStart + (x + radius + 1).coerceIn(0, w - 1)]
                val remPx = srcPixels[rowStart + (x - radius).coerceIn(0, w - 1)]
                sumA += ((addPx ushr 24) and 0xFF) - ((remPx ushr 24) and 0xFF)
                sumR += ((addPx ushr 16) and 0xFF) - ((remPx ushr 16) and 0xFF)
                sumG += ((addPx ushr 8) and 0xFF) - ((remPx ushr 8) and 0xFF)
                sumB += (addPx and 0xFF) - (remPx and 0xFF)
            }
        }

        // 垂直方向滑动窗口模糊
        for (x in 0 until w) {
            var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
            for (y in -radius..radius) {
                val px = hBlur[y.coerceIn(0, h - 1) * w + x]
                sumA += (px ushr 24) and 0xFF
                sumR += (px ushr 16) and 0xFF
                sumG += (px ushr 8) and 0xFF
                sumB += px and 0xFF
            }
            for (y in 0 until h) {
                val idx = y * w + x
                dstPixels[idx] = ((sumA / div) shl 24) or ((sumR / div) shl 16) or
                        ((sumG / div) shl 8) or (sumB / div)
                val addPx = hBlur[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                val remPx = hBlur[(y - radius).coerceIn(0, h - 1) * w + x]
                sumA += ((addPx ushr 24) and 0xFF) - ((remPx ushr 24) and 0xFF)
                sumR += ((addPx ushr 16) and 0xFF) - ((remPx ushr 16) and 0xFF)
                sumG += ((addPx ushr 8) and 0xFF) - ((remPx ushr 8) and 0xFF)
                sumB += (addPx and 0xFF) - (remPx and 0xFF)
            }
        }

        return Bitmap.createBitmap(dstPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    companion object {
        const val MODULE_NAME = "DshBlurModule"
    }
}
