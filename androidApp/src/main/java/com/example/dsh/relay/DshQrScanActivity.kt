package com.example.dsh.relay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dsh.R
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
    private lateinit var flashlightButton: ImageButton
    private lateinit var flashlightLabel: TextView
    private var camera: Camera? = null
    private var torchOn = false
    private var lastLux = -1f
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

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

        flashlightButton = ImageButton(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
            setImageResource(R.drawable.ic_flashlight_off)
            contentDescription = "轻触照亮"
            setOnClickListener { toggleTorch() }
        }
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
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            // 没有光线传感器时无法判断明暗，退化为一直显示。
            flashlightRow.alpha = 1f
            flashlightRow.visibility = View.VISIBLE
        }
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
        lightSensor?.let {
            sensorManager.registerListener(lightListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        overlayView.stop()
        sensorManager.unregisterListener(lightListener)
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

    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_LIGHT) return
            val lux = event.values.firstOrNull() ?: return
            lastLux = lux
            when {
                lux < LIGHT_SHOW_LUX -> showFlashlight()
                lux > LIGHT_HIDE_LUX -> hideFlashlight()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun showFlashlight() {
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
        if (!torchOn && lastLux > LIGHT_HIDE_LUX) hideFlashlight()
    }

    private fun updateFlashlightUi() {
        flashlightButton.setImageResource(
            if (torchOn) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off,
        )
        flashlightLabel.text = if (torchOn) "轻触关闭" else "轻触照亮"
        flashlightButton.contentDescription = flashlightLabel.text
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        overlayView.stop()
        executor.shutdown()
        scanner.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QR = "qr"
        private const val LIGHT_SHOW_LUX = 20f
        private const val LIGHT_HIDE_LUX = 50f
        private const val FADE_IN_MS = 200L
        private const val FADE_OUT_MS = 300L
    }
}
