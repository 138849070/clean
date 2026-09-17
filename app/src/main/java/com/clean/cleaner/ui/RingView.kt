package com.clean.cleaner.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.clean.cleaner.R

/** 存储占用环形图 */
class RingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(14)
        color = 0x33FFFFFF.toInt()
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(14)
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val arcRect = RectF()

    fun animateTo(target: Float, duration: Long = 800) {
        val anim = ValueAnimator.ofFloat(progress, target.coerceIn(0f, 1f)).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
            }
            start()
        }
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = dp(14).toFloat()
        val half = stroke / 2
        arcRect.set(half, half, width - half, height - half)
        canvas.drawArc(arcRect, -90f, 360f, false, bgPaint)
        if (progress > 0.001f) {
            canvas.drawArc(arcRect, -90f, 360f * progress, false, fgPaint)
        }
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density
}
