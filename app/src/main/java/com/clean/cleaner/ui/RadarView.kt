package com.clean.cleaner.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/** 扫描雷达动画：旋转扇区 + 脉冲波纹 */
class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var rotation = 0f
    private var pulse = 0f

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC44D.toInt() // 琥珀色扇区
        style = Paint.Style.FILL
    }
    private val sweepShader = android.graphics.SweepGradient(0f, 0f, intArrayOf(
        Color.TRANSPARENT, 0x55FFC44D.toInt(), 0xFFFFC44D.toInt()), floatArrayOf(0f, 0.7f, 1f))

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1)
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1)
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotation = (rotation + 2.2f) % 360f
            pulse = (pulse + 0.012f) % 1f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    fun start() {
        if (!animator.isStarted) animator.start()
    }

    fun stop() {
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - dp(8)

        // 外圈
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.66f, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.33f, ringPaint)
        // 十字线
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crossPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crossPaint)

        // 脉冲波纹
        val pr = radius * (0.33f + pulse * 0.67f)
        pulsePaint.alpha = ((1f - pulse) * 80).toInt()
        canvas.drawCircle(cx, cy, pr, pulsePaint)

        // 旋转扇区
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation)
        val sweep = RectF(-radius, -radius, radius, radius)
        sweepPaint.shader = android.graphics.SweepGradient(0f, 0f,
            intArrayOf(Color.TRANSPARENT, 0x22FFC44D.toInt(), 0x99FFC44D.toInt()),
            floatArrayOf(0f, 0.6f, 1f))
        canvas.drawArc(sweep, 0f, 78f, true, sweepPaint)
        canvas.restore()
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density
}
