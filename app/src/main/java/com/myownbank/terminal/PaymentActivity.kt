package com.myownbank.terminal

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import com.myownbank.terminal.databinding.ActivityPaymentBinding
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.random.Random

class PaymentActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private lateinit var binding: ActivityPaymentBinding
    private lateinit var terminalId: String
    private var price: Long = 0
    private var paymentDone = false
    private var countDownTimer: CountDownTimer? = null
    private val transactionId: String = UUID.randomUUID().toString().take(12).uppercase()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var darkFrameCount = 0
    private val REQUIRED_DARK_FRAMES = 5 // Нужно 5 тёмных кадров подряд
    private val CAMERA_PERMISSION_CODE = 100
    private var cameraProvider: ProcessCameraProvider? = null
    private var brightnessDetectionEnabled = true

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        price = intent.getLongExtra("price", 0L)
        terminalId = intent.getStringExtra("terminalId") ?: ""
        val paymentTimeout = intent.getIntExtra("paymentTimeout", 60)

        binding.priceTop.text = formatPrice(price)
        binding.successAmount.visibility = View.GONE

        // Запуск анимации NFC иконки
        (binding.nfcIcon.drawable as? AnimatedVectorDrawable)?.start()

        // NFC setup
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        intentFiltersArray = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))

        // Запуск камеры для определения яркости
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraBrightnessDetection()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }

        // Таймаут
        startCountdown(paymentTimeout)

        // Загружаем настройки из Firebase
        val ref = FirebaseDatabase.getInstance().getReference("terminals/$terminalId")
        ref.get().addOnSuccessListener { snapshot ->
            val disabledPayment = snapshot.child("disabledPayment").getValue(String::class.java) ?: ""
            if (disabledPayment == "face") {
                binding.faceButton.isEnabled = false
                binding.faceButton.alpha = 0.5f
            }
        }

        binding.faceButton.setOnClickListener {
            if (!binding.faceButton.isEnabled) return@setOnClickListener
            brightnessDetectionEnabled = false // Отключаем детектор яркости
            cameraProvider?.unbindAll() // Останавливаем камеру
            startActivityForResult(Intent(this, FaceScanActivity::class.java), 101)
        }

        binding.cancelButton.setOnClickListener {
            sendCancel()
            finish()
        }
    }

    private fun startCountdown(seconds: Int) {
        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.timerText.text = "Осталось: ${millisUntilFinished / 1000} сек"
            }
            override fun onFinish() {
                if (!paymentDone) {
                    sendCancel()
                    finish()
                }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraBrightnessDetection()
            }
        }
    }

    @ExperimentalGetImage
    private fun startCameraBrightnessDetection() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (paymentDone || !brightnessDetectionEnabled) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val brightness = calculateBrightness(imageProxy)
                val isDark = brightness < 25 // Порог затемнения

                if (isDark) {
                    darkFrameCount++
                    if (darkFrameCount >= REQUIRED_DARK_FRAMES) {
                        runOnUiThread {
                            processPayment(method = "card")
                        }
                    }
                } else {
                    darkFrameCount = 0
                }

                imageProxy.close()
            }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA, // Фронтальная камера
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("PaymentActivity", "Camera bind failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun calculateBrightness(imageProxy: ImageProxy): Int {
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        var sum = 0L
        for (byte in data) {
            sum += byte.toInt() and 0xFF
        }

        return (sum / data.size).toInt()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == Activity.RESULT_OK) {
            processPayment(method = "face")
        }
    }

    private fun processPayment(method: String) {
        if (paymentDone) return
        paymentDone = true
        countDownTimer?.cancel()

        binding.priceTop.visibility = View.GONE
        binding.timerText.visibility = View.GONE
        val flash = binding.successFlash
        val amountText = binding.successAmount
        flash.visibility = View.VISIBLE
        amountText.visibility = View.GONE

        val ref = FirebaseDatabase.getInstance().getReference("terminals/$terminalId")
        ref.get().addOnSuccessListener { snapshot ->
            val chance = snapshot.child("failureChance").getValue(Int::class.java) ?: 10
            val failMsg = snapshot.child("failMessage").getValue(String::class.java) ?: ""
            val paymentSuccess = Random.nextInt(100) >= chance

            val endColor = if (paymentSuccess) Color.parseColor("#2ECC71") else Color.parseColor("#E74C3C")

            val flashAnim = android.animation.ObjectAnimator.ofFloat(flash, "alpha", 0f, 1f).apply { duration = 200 }
            val colorAnim = android.animation.ValueAnimator.ofArgb(Color.WHITE, endColor).apply {
                duration = 500
                addUpdateListener { flash.setBackgroundColor(it.animatedValue as Int) }
            }
            val showText = android.animation.ObjectAnimator.ofFloat(amountText, "alpha", 0f, 1f).apply { duration = 400 }

            flashAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { colorAnim.start() }
            })

            colorAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val statusValue: String
                    val shouldFinish: Boolean
                    if (paymentSuccess) {
                        amountText.text = formatPrice(price)
                        statusValue = "success"
                        shouldFinish = true
                        playTone(ToneGenerator.TONE_PROP_ACK)
                    } else {
                        val failMessages = listOf(
                            "Операция отклонена", "Недостаточно средств",
                            "Карта заблокирована", "Попробуйте снова",
                            "Ошибка соединения", "Транзакция не прошла",
                            "Банк отклонил операцию"
                        )
                        amountText.text = if (failMsg.isNotEmpty()) failMsg else failMessages.random()
                        statusValue = "failed"
                        shouldFinish = false
                        playTone(ToneGenerator.TONE_PROP_NACK)
                    }
                    amountText.visibility = View.VISIBLE
                    showText.start()

                    // Обновляем статус терминала
                    ref.child("status").setValue(statusValue)
                    ref.child("command").setValue("idle")

                    // Сохраняем транзакцию в историю
                    saveTransaction(statusValue, method)

                    // Если успешно - закрываем, если нет - возвращаемся к экрану оплаты
                    if (shouldFinish) {
                        binding.root.postDelayed({ finish() }, 2200)
                    } else {
                        binding.root.postDelayed({ resetPaymentUI() }, 2200)
                    }
                }
            })

            flashAnim.start()
        }
    }

    private fun resetPaymentUI() {
        // Сбрасываем UI для повторной попытки
        paymentDone = false
        darkFrameCount = 0
        brightnessDetectionEnabled = true

        binding.priceTop.visibility = View.VISIBLE
        binding.timerText.visibility = View.VISIBLE
        binding.successFlash.visibility = View.GONE
        binding.successAmount.visibility = View.GONE
        binding.cancelButton.isEnabled = true
        binding.faceButton.isEnabled = true

        // Перезапускаем камеру для детекции яркости
        startCameraBrightnessDetection()

        // Перезапускаем таймер
        val paymentTimeout = intent.getIntExtra("paymentTimeout", 60)
        startCountdown(paymentTimeout)

        // Обновляем статус в Firebase на idle для готовности к новой попытке
        val ref = FirebaseDatabase.getInstance().getReference("terminals/$terminalId")
        ref.child("status").setValue("idle")
    }

    private fun saveTransaction(status: String, method: String) {
        val historyRef = FirebaseDatabase.getInstance()
            .getReference("terminals/$terminalId/history/$transactionId")
        historyRef.setValue(
            mapOf(
                "transactionId" to transactionId,
                "price" to price,
                "status" to status,
                "method" to method,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    private fun playTone(tone: Int) {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(tone, 300)
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cameraExecutor.shutdown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (paymentDone) return
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val ref = FirebaseDatabase.getInstance().getReference("terminals/$terminalId")
                ref.get().addOnSuccessListener { snapshot ->
                    val disabledPayment = snapshot.child("disabledPayment").getValue(String::class.java) ?: ""
                    if (disabledPayment == "nfc") return@addOnSuccessListener
                    paymentDone = true
                    binding.cancelButton.isEnabled = false
                    binding.faceButton.isEnabled = false
                    processPayment(method = "nfc")
                }
            }
        }
    }

    private fun sendCancel() {
        val ref = FirebaseDatabase.getInstance().getReference("terminals/$terminalId")
        ref.child("status").setValue("cancel")
        ref.child("command").setValue("idle")
    }
}
