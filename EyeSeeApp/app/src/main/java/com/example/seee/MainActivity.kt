package com.example.seee

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.camera.view.PreviewView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.view.GestureDetector.SimpleOnGestureListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var gestureDetector: GestureDetector
    private lateinit var cloudTTSHelper: CloudTTSHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()
        cloudTTSHelper = CloudTTSHelper(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        val rootLayout = findViewById<FrameLayout>(R.id.rootLayout)
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                Toast.makeText(this@MainActivity, "Double Tap detected", Toast.LENGTH_SHORT).show()
                capturePhoto()
                return true
            }
        })
        // val captureButton = findViewById<ImageButton>(R.id.captureButton)
        // captureButton.setOnClickListener {
        //    capturePhoto()
        // }


        rootLayout.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        startPollingTextFromServer()
    }

    private var isCameraReady = false

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val previewView = findViewById<PreviewView>(R.id.previewView)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                isCameraReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer: ByteBuffer = planes[0].buffer
        val uBuffer: ByteBuffer = planes[1].buffer
        val vBuffer: ByteBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is null")
            return
        }
        if (!isCameraReady) {
            Toast.makeText(this, "Camera not ready yet", Toast.LENGTH_SHORT).show()
            return
        }


        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxy.toBitmap()
                    imageProxy.close()
                    val jpgData = bitmapToJpeg(bitmap)
                    Toast.makeText(this@MainActivity, "Image captured and sending to server...", Toast.LENGTH_SHORT).show()
                    NetworkUtils.sendImageToServer(jpgData)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Capture failed", exception)
                }
            })
    }


    private fun bitmapToJpeg(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        return outputStream.toByteArray()
    }


    private lateinit var sharedPreferences: SharedPreferences
    private var lastSpokenText: String?
        get() = sharedPreferences.getString("last_spoken_text", null)
        set(value) {
            sharedPreferences.edit().putString("last_spoken_text", value).apply()
        }

    private fun startPollingTextFromServer() {
        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        NetworkUtils.startPollingText(scope = lifecycleScope) { text ->
            if (text != lastSpokenText && text.isNotBlank()) {
                lastSpokenText = text
                cloudTTSHelper.speak(text)
                Toast.makeText(this@MainActivity, "Received new text: $text", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MainActivity", "Duplicate text received. Skipping TTS.")
            }
        }
    }


    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )
    }
}
