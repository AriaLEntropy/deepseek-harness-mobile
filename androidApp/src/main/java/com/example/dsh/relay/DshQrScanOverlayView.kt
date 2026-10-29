package com.example.dsh.relay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * BGA ScanBoxView 的视觉子集：遮罩、紫色圆角四角、来回扫光线和一句提示。
 * 不参与解码，也不限制识别区域。
 */
internal class DshQrScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val box = RectF()
    private val cornerPath = Path()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = MASK_COLOR
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = CORNER_COLOR
        strokeCap = Paint.Cap.BUTT
    }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TIP_COLOR
        textAlign = Paint.Align.CENTER
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = SCAN_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            scanProgress = animation.animatedValue as Float
            invalidate()
        }
    }

    private var scanProgress = 0f
    private var insetTop = 0
    private var insetBottom = 0
    private var scanShader: Shader? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun setSystemInsets(top: Int, bottom: Int) {
        if (insetTop == top && insetBottom == bottom) return
        insetTop = top
        insetBottom = bottom
        layoutBox()
        invalidate()
    }

    fun start() {
        if (!animator.isStarted) animator.start()
    }

    fun stop() {
        animator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBox()
    }

    override fun onDraw(canvas: Canvas) {
        if (box.isEmpty) return
        drawMask(canvas)
        drawCorners(canvas)
        drawScanLine(canvas)
        drawTip(canvas)
    }

    private fun layoutBox() {
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val availW = width.toFloat()
        val availH = (height - insetTop - insetBottom).toFloat()
        val maxSize = 240f * density
        val tipSpace = 52f * density
        val size = min(maxSize, min(availW, availH - tipSpace) * 0.72f)
        val left = (availW - size) / 2f
        val top = insetTop + (availH - size - tipSpace) / 2f
        box.set(left, top, left + size, top + size)
        scanShader = null
        cornerPaint.strokeWidth = 3f * density
        tipPaint.textSize = 13f * resources.displayMetrics.scaledDensity
    }

    private fun drawMask(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, box.top, maskPaint)
        canvas.drawRect(0f, box.top, box.left, box.bottom, maskPaint)
        canvas.drawRect(box.right, box.top, w, box.bottom, maskPaint)
        canvas.drawRect(0f, box.bottom, w, h, maskPaint)
    }

    private fun drawCorners(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val half = cornerPaint.strokeWidth / 2f
        val radius = min(10f * density, box.width() / 4f)
        val length = min(28f * density, box.width() / 3f)
        val left = box.left - half
        val top = box.top - half
        val right = box.right + half
        val bottom = box.bottom + half
        cornerPath.reset()

        // 左上
        cornerPath.moveTo(left, top + length)
        cornerPath.lineTo(left, top + radius)
        cornerPath.quadTo(left, top, left + radius, top)
        cornerPath.lineTo(left + length, top)
        // 右上
        cornerPath.moveTo(right - length, top)
        cornerPath.lineTo(right - radius, top)
        cornerPath.quadTo(right, top, right, top + radius)
        cornerPath.lineTo(right, top + length)
        // 左下
        cornerPath.moveTo(left, bottom - length)
        cornerPath.lineTo(left, bottom - radius)
        cornerPath.quadTo(left, bottom, left + radius, bottom)
        cornerPath.lineTo(left + length, bottom)
        // 右下
        cornerPath.moveTo(right - length, bottom)
        cornerPath.lineTo(right - radius, bottom)
        cornerPath.quadTo(right, bottom, right, bottom - radius)
        cornerPath.lineTo(right, bottom - length)

        canvas.drawPath(cornerPath, cornerPaint)
    }

    private fun drawScanLine(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val inset = 8f * density
        val lineHeight = 2f * density
        val travel = box.height() - inset * 2f - lineHeight
        if (travel <= 0f) return
        val left = box.left + inset
        val right = box.right - inset
        val top = box.top + inset + travel * scanProgress
        if (scanShader == null) {
            scanShader = LinearGradient(
                left,
                0f,
                right,
                0f,
                intArrayOf(0x009B5CFF, SCAN_LINE_COLOR, 0x009B5CFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        scanPaint.shader = scanShader
        canvas.drawRect(left, top, right, top + lineHeight, scanPaint)
        scanPaint.shader = null
    }

    private fun drawTip(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val x = box.centerX()
        val y = box.bottom + 22f * density - tipPaint.ascent()
        canvas.drawText(TIP, x, y, tipPaint)
    }

    companion object {
        private const val MASK_COLOR = 0x88000000.toInt()
        private const val CORNER_COLOR = 0xFF9B5CFF.toInt()
        private const val TIP_COLOR = 0xB8FFFFFF.toInt()
        private const val SCAN_LINE_COLOR = 0xFF9B5CFF.toInt()
        private const val SCAN_MS = 1400L
        private const val TIP = "对准电脑屏幕上的二维码"
    }
}
