package com.myownbank.terminal

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.util.concurrent.Executors
import kotlin.math.pow

@OptIn(ExperimentalGetImage::class)
class FaceScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var circleOverlay: CircleOverlay
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this)

        previewView = PreviewView(this)
        previewView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        circleOverlay = CircleOverlay(this)
        circleOverlay.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        container.addView(previewView)
        container.addView(circleOverlay)

        setContentView(container)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                finish()
            }
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
            )

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->

                val mediaImage = imageProxy.image
                if (mediaImage != null) {

                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    detector.process(image)
                        .addOnSuccessListener { faces ->

                            if (faces.isNotEmpty()) {

                                val face = faces[0]
                                val boundingBox = face.boundingBox

                                val imageWidth = image.width.toFloat()
                                val imageHeight = image.height.toFloat()

                                val viewWidth = previewView.width.toFloat()
                                val viewHeight = previewView.height.toFloat()

                                // Масштабирование координат
                                val scaleX = viewWidth / imageWidth
                                val scaleY = viewHeight / imageHeight

                                val faceCenterX =
                                    (boundingBox.centerX() * scaleX)

                                val faceCenterY =
                                    (boundingBox.centerY() * scaleY)

                                val circleCenterX = viewWidth / 2f
                                val circleCenterY = viewHeight / 2f
                                val circleRadius = viewWidth * 0.35f

                                val dx = faceCenterX - circleCenterX
                                val dy = faceCenterY - circleCenterY

                                val isInsideCircle =
                                    dx * dx + dy * dy <= circleRadius * circleRadius

                                runOnUiThread {
                                    circleOverlay.setFaceDetected(isInsideCircle)
                                }

                                if (isInsideCircle) {
                                    setResult(Activity.RESULT_OK)
                                    finish()
                                }

                            } else {
                                runOnUiThread {
                                    circleOverlay.setFaceDetected(false)
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }

                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("FaceScanActivity", "Camera bind failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

class CircleOverlay(context: Context) : View(context) {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
        color = Color.WHITE
    }

    private var faceDetected = false

    fun setFaceDetected(detected: Boolean) {
        faceDetected = detected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width * 0.35f

        paint.color = if (faceDetected) Color.GREEN else Color.WHITE

        canvas.drawCircle(centerX, centerY, radius, paint)
    }
}