package com.example.dsh.relay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dsh.R
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DshQrScanActivity : AppCompatActivity() {
    private val scanner = BarcodeScanning.getClient()
    private val executor = Executors.newSingleThreadExecutor()
    private val done = AtomicBoolean(false)
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: DshQrScanOverlayView
    private lateinit var flashlightRow: LinearLayout
    private lateinit var flashlightButton: FrameLayout
    private lateinit var flashlightOffIcon: ImageView
    private lateinit var flashlightOnIcon: ImageView
    private lateinit var flashlightLabel: TextView
    private var camera: Camera? = null
    private var torchOn = false
    private var previewLuma = -1f
    private var lumaDark = false
    private var lumaDarkSince = 0L
    private var lumaStartAt = 0L
    private val hideFlashlightRunnable = Runnable {
        if (!torchOn) hideFlashlight()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        layoutEdgeToEdge()
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            fitsSystemWindows = false
        }
        overlayView = DshQrScanOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            elevation = 4f * resources.displayMetrics.density
            fitsSystemWindows = false
        }
        val root = FrameLayout(this).apply {
            fitsSystemWindows = false
            clipToPadding = false
            clipChildren = false
            setBackgroundColor(Color.BLACK)
        }
        root.addView(previewView)
        root.addView(overlayView)

        flashlightButton = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            isClickable = true
            contentDescription = "轻触照亮"
            setOnClickListener { toggleTorch() }
        }
        val flashlightIconBox = FrameLayout(this).apply { clipChildren = false }
        flashlightOffIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_flashlight_off)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        flashlightOnIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_flashlight_on)
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        flashlightIconBox.addView(
            flashlightOffIcon,
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER),
        )
        flashlightIconBox.addView(
            flashlightOnIcon,
            FrameLayout.LayoutParams(
                dp(30),
                dpFloat(36.25f).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = -dpFloat(6.25f).toInt() },
        )
        flashlightButton.addView(
            flashlightIconBox,
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER),
        )
        flashlightLabel = TextView(this).apply {
            text = "轻触照亮"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt())
        }
        flashlightRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(flashlightButton, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(
                flashlightLabel,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }
        root.addView(
            flashlightRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = dp(96) },
        )

        flashlightRow.alpha = 0f
        flashlightRow.visibility = View.GONE
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            overlayView.setSystemInsets(bars.top, bars.bottom)
            val flashlightParams = flashlightRow.layoutParams as FrameLayout.LayoutParams
            flashlightParams.bottomMargin = bars.bottom + dp(72)
            flashlightRow.layoutParams = flashlightParams
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            bindCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        overlayView.start()
        flashlightRow.removeCallbacks(hideFlashlightRunnable)
        if (!torchOn) {
            flashlightRow.animate().cancel()
            flashlightRow.alpha = 0f
            flashlightRow.visibility = View.GONE
        }
        lumaDark = false
        lumaDarkSince = 0L
        lumaStartAt = 0L
    }

    override fun onPause() {
        overlayView.stop()
        flashlightRow.removeCallbacks(hideFlashlightRunnable)
        if (torchOn) {
            camera?.cameraControl?.enableTorch(false)
            torchOn = false
            updateFlashlightUi()
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) bindCamera()
        else {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun layoutEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                Toast.makeText(this, "没有可用的后置摄像头", Toast.LENGTH_SHORT).show()
                return@addListener
            }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val luma = averageLuma(proxy)
                if (luma >= 0f) onPreviewLuma(luma)
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val value = barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT || it.rawValue != null }?.rawValue
                        if (!value.isNullOrBlank() && value.contains("#") && done.compareAndSet(false, true)) {
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_QR, value))
                            finish()
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    /** 用相机 Y 平面平均亮度判断环境，不依赖部分机型会恒为 0 的环境光传感器。 */
    private fun averageLuma(proxy: ImageProxy): Float {
        val plane = proxy.planes.firstOrNull() ?: return -1f
        val buffer = plane.buffer
        val width = proxy.width
        val height = proxy.height
        if (width <= 0 || height <= 0 || buffer.limit() <= 0) return -1f
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val step = 8
        var sum = 0L
        var count = 0
        var y = 0
        while (y < height) {
            val rowStart = y * rowStride
            var x = 0
            while (x < width) {
                val index = rowStart + x * pixelStride
                if (index < buffer.limit()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    count++
                }
                x += step
            }
            y += step
        }
        return if (count == 0) -1f else sum.toFloat() / count
    }

    private fun onPreviewLuma(luma: Float) {
        previewLuma = luma
        val now = SystemClock.elapsedRealtime()
        if (lumaStartAt == 0L) lumaStartAt = now
        when {
            luma < LUMA_SHOW -> {
                if (lumaDarkSince == 0L) lumaDarkSince = now
                if (!lumaDark &&
                    now - lumaStartAt >= LUMA_SETTLE_MS &&
                    now - lumaDarkSince >= LUMA_CONFIRM_MS
                ) {
                    lumaDark = true
                    Log.d(TAG, "preview dark luma=$luma -> show")
                    runOnUiThread { if (!isFinishing) showFlashlight() }
                }
            }
            luma > LUMA_HIDE -> {
                lumaDarkSince = 0L
                if (lumaDark) {
                    lumaDark = false
                    Log.d(TAG, "preview bright luma=$luma -> hide later")
                    scheduleHideFlashlight()
                }
            }
            else -> lumaDarkSince = 0L
        }
    }

    private fun scheduleHideFlashlight() {
        flashlightRow.removeCallbacks(hideFlashlightRunnable)
        flashlightRow.postDelayed(hideFlashlightRunnable, HIDE_DELAY_MS)
    }

    private fun showFlashlight() {
        flashlightRow.removeCallbacks(hideFlashlightRunnable)
        if (flashlightRow.visibility == View.VISIBLE && flashlightRow.alpha == 1f) return
        flashlightRow.animate().cancel()
        flashlightRow.visibility = View.VISIBLE
        flashlightRow.animate().alpha(1f).setDuration(FADE_IN_MS).start()
    }

    private fun hideFlashlight() {
        // 手电筒开着时不隐藏：它自己照亮环境后可能把感光值推高。
        if (torchOn) return
        if (flashlightRow.visibility != View.VISIBLE) return
        flashlightRow.animate().cancel()
        flashlightRow.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .withEndAction {
                if (flashlightRow.alpha == 0f) flashlightRow.visibility = View.GONE
            }
            .start()
    }

    private fun toggleTorch() {
        val cam = camera ?: run {
            Toast.makeText(this, "摄像头未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        if (!cam.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "此设备没有闪光灯", Toast.LENGTH_SHORT).show()
            return
        }
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        updateFlashlightUi()
        if (torchOn) {
            flashlightRow.animate().cancel()
            flashlightRow.visibility = View.VISIBLE
            flashlightRow.alpha = 1f
        } else if (previewLuma > LUMA_HIDE) {
            scheduleHideFlashlight()
        }
    }

    private fun updateFlashlightUi() {
        flashlightOffIcon.visibility = if (torchOn) View.GONE else View.VISIBLE
        flashlightOnIcon.visibility = if (torchOn) View.VISIBLE else View.GONE
        flashlightLabel.text = if (torchOn) "轻触关闭" else "轻触照亮"
        flashlightButton.contentDescription = flashlightLabel.text
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dpFloat(value: Float): Float = value * resources.displayMetrics.density

    override fun onDestroy() {
        overlayView.stop()
        executor.shutdown()
        scanner.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QR = "qr"
        private const val TAG = "DshQrScan"
        private const val LUMA_SHOW = 25f
        private const val LUMA_HIDE = 55f
        private const val LUMA_SETTLE_MS = 700L
        private const val LUMA_CONFIRM_MS = 300L
        private const val HIDE_DELAY_MS = 1_800L
        private const val FADE_IN_MS = 200L
        private const val FADE_OUT_MS = 300L
    }
}
