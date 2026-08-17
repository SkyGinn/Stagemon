package com.example.stagemon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class DualWaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lastSeekTime = 0L

    private var fohPeaks: FloatArray = floatArrayOf()
    private var monPeaks: FloatArray = floatArrayOf()
    private var progress: Float = 0f

    private val paintPlayed = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val paintUnplayed = Paint().apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val paintProgressLine = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val paintBg = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    var onSeek: ((Float) -> Unit)? = null
    var onScratchActive: ((Boolean) -> Unit)? = null
    var onSpeedChange: ((Float) -> Unit)? = null
    var onStopAudio: (() -> Unit)? = null
    var onStartAudio: (() -> Unit)? = null

    private var lastX = 0f
    private var lastTime = 0L
    private var isScratching = false

    private fun calculateProgress(x: Float): Float {
        val gap = 20f
        val halfWidth = (width - gap) / 2f
        val leftEnd = halfWidth
        val rightStart = leftEnd + gap
        return when {
            x < leftEnd -> (x / halfWidth).coerceIn(0f, 1f)
            x > rightStart -> ((x - rightStart) / halfWidth).coerceIn(0f, 1f)
            else -> progress
        }
    }

    fun setPeaks(foh: FloatArray, mon: FloatArray) {
        fohPeaks = foh
        monPeaks = mon
        invalidate()
    }

    fun setProgress(prog: Float) {
        val clamped = prog.coerceIn(0f, 1f)
        if (abs(clamped - progress) > 0.001f) {
            progress = clamped
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f
        val gap = 20f
        val halfWidth = (w - gap) / 2f
        val barGap = 4f
        val cornerRadius = 20f
        val rect = RectF()

        canvas.drawRect(0f, 0f, w, h, paintBg)

        // FOH
        if (fohPeaks.isNotEmpty()) {
            val barWidth = halfWidth / fohPeaks.size.toFloat()
            for (i in fohPeaks.indices) {
                val barHeight = fohPeaks[i] * centerY * 0.95f
                if (barHeight > 0.5f) {
                    val left = i * barWidth
                    val right = left + barWidth - barGap
                    rect.set(left, centerY - barHeight, right, centerY + barHeight)
                    val isPlayed = (i.toFloat() / fohPeaks.size) < progress
                    val paint = if (isPlayed) paintPlayed else paintUnplayed
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                }
            }
            canvas.drawLine(progress * halfWidth, 0f, progress * halfWidth, h, paintProgressLine)
        }

        // MON
        if (monPeaks.isNotEmpty()) {
            val barWidth = halfWidth / monPeaks.size.toFloat()
            val rightStart = halfWidth + gap
            for (i in monPeaks.indices) {
                val barHeight = monPeaks[i] * centerY * 0.95f
                if (barHeight > 0.5f) {
                    val left = rightStart + i * barWidth
                    val right = left + barWidth - barGap
                    rect.set(left, centerY - barHeight, right, centerY + barHeight)
                    val isPlayed = (i.toFloat() / monPeaks.size) < progress
                    val paint = if (isPlayed) paintPlayed else paintUnplayed
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                }
            }
            canvas.drawLine(rightStart + progress * halfWidth, 0f, rightStart + progress * halfWidth, h, paintProgressLine)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isScratching = true
                onScratchActive?.invoke(true)
                onStopAudio?.invoke()  // Остановить звук сразу при нажатии
                lastX = event.x
                lastTime = event.eventTime
                onSpeedChange?.invoke(0.0f)
                val p = calculateProgress(event.x)
                setProgress(p)
                // Не вызываем onSeek здесь, только при движении
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val currentX = event.x
                val currentTime = event.eventTime
                val dx = currentX - lastX
                val dt = (currentTime - lastTime).coerceAtLeast(1L)
                val speed = abs(dx / dt) * 2.0f
                onSpeedChange?.invoke(speed.coerceIn(0.0f, 3.0f))
                lastX = currentX
                lastTime = currentTime

                val now = System.currentTimeMillis()
                if (now - lastSeekTime > 80) {  // Увеличил интервал до 80мс
                    lastSeekTime = now
                    val p = calculateProgress(event.x)
                    setProgress(p)
                    onSeek?.invoke(p)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScratching = false
                onScratchActive?.invoke(false)
                onSpeedChange?.invoke(1.0f)
                // Финальный вызов onSeek для точной позиции
                val finalP = calculateProgress(event.x)
                setProgress(finalP)
                onSeek?.invoke(finalP)
                // Небольшая задержка перед стартом чтобы позиция успела установиться
                postDelayed({
                    onStartAudio?.invoke()
                }, 50)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}