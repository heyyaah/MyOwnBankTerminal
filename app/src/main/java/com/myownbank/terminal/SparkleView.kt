package com.myownbank.terminal

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class SparkleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Sparkle(
        var x: Float,
        var y: Float,
        var size: Float,
        var alpha: Int
    )

    private val sparkles = mutableListOf<Sparkle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private val sparkleDuration = 500L
    private val sparkleInterval = 100L
    private var isRunning = false

    private val sparkleRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            addSparkle()
            handler.postDelayed(this, sparkleInterval)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRunning = true
        handler.post(sparkleRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        sparkles.clear()
    }

    private fun addSparkle() {
        if (width == 0 || height == 0) return
        val x = Random.nextFloat() * width
        val y = Random.nextFloat() * height
        val size = 10f + Random.nextFloat() * 20f
        val sparkle = Sparkle(x, y, size, 255)
        sparkles.add(sparkle)

        val startTime = System.currentTimeMillis()
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = elapsed.toFloat() / sparkleDuration
                if (fraction < 1f) {
                    sparkle.alpha = ((1 - fraction) * 255).toInt()
                    invalidate()
                    handler.postDelayed(this, 16)
                } else {
                    sparkles.remove(sparkle)
                    invalidate()
                }
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        sparkles.forEach { sparkle ->
            val gradient = RadialGradient(
                sparkle.x, sparkle.y, sparkle.size,
                Color.argb(sparkle.alpha, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient
            canvas.drawCircle(sparkle.x, sparkle.y, sparkle.size, paint)
            paint.shader = null
        }
    }
}
