package com.dev.adblocker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * A circular ring that visualises ad-blocker state:
 *  - OFF: flat red ring, shield-with-slash glyph centred.
 *  - ON : emerald ring with a pulsing outer glow, live blocked-count centred.
 *
 * Public API:
 *   setActive(Boolean)
 *   setBlockedCount(Long)
 */
class StatusRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(28f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val colorOn = ContextCompat.getColor(context, R.color.status_on)
    private val colorOnGlow = ContextCompat.getColor(context, R.color.status_on_glow)
    private val colorOff = ContextCompat.getColor(context, R.color.status_off)
    private val colorText = ContextCompat.getColor(context, R.color.text_primary)

    private val shield: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.ic_shield_slash)

    private var active: Boolean = false
    private var blockedCount: Long = 0L
    // Pre-formatted count string — recomputed only when blockedCount changes,
    // so onDraw (which fires every ~16ms during pulse) allocates no strings.
    private var formattedCount: String = "0"
    private var glowAlpha: Float = 0.25f
    private var glowScale: Float = 1.0f

    private var animator: ValueAnimator? = null

    fun setActive(isActive: Boolean) {
        if (active == isActive) return
        active = isActive
        if (active) startPulse() else stopPulse()
        invalidate()
    }

    fun setBlockedCount(count: Long) {
        if (blockedCount == count) return
        blockedCount = count
        formattedCount = "%,d".format(count)
        if (active) invalidate()
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    private fun startPulse() {
        stopPulse()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                glowAlpha = 0.25f + 0.30f * t
                glowScale = 1.00f + 0.08f * t
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        // Leave enough margin for:
        //   (r * max glowScale 1.08) + half glow stroke (5dp) ≤ half view size.
        // With min/2 = 80dp (160dp view), r must be ≤ (80-5)/1.08 ≈ 69.4dp,
        // so a 14dp inset gives r = 66dp — fits the pulsed glow comfortably.
        val r = (min(width, height) / 2f) - dp(14f)

        if (active) {
            glowPaint.color = colorOnGlow
            glowPaint.alpha = (glowAlpha * 255).toInt().coerceIn(0, 255)
            val rGlow = r * glowScale
            canvas.drawCircle(cx, cy, rGlow, glowPaint)
        }

        ringPaint.color = if (active) colorOn else colorOff
        canvas.drawCircle(cx, cy, r, ringPaint)

        if (active) {
            numberPaint.color = colorText
            // Centre vertically: baseline = cy - (ascent + descent) / 2
            val fm = numberPaint.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(formattedCount, cx, baseline, numberPaint)
        } else {
            shield?.let { d ->
                val size = dp(56f).toInt()
                val left = (cx - size / 2f).toInt()
                val top = (cy - size / 2f).toInt()
                d.setBounds(left, top, left + size, top + size)
                d.draw(canvas)
            }
        }
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun sp(value: Float): Float =
        value * resources.displayMetrics.scaledDensity
}
