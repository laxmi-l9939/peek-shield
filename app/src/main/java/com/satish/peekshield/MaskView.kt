package com.satish.peekshield

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

/**
 * Draws the privacy shade: four black bands surrounding [peekRect], leaving the
 * peek rectangle fully transparent. Touches inside the peek rectangle are not
 * consumed here (the clear view on top is FLAG_NOT_TOUCHABLE and lets them fall
 * through to the underlying app); touches on the bands are consumed by this view
 * because it does not set FLAG_NOT_TOUCHABLE, effectively blocking interaction
 * with the masked area.
 */
class MaskView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var peekRect: Rect = Rect()
    private var alpha: Int = 255

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(alpha, 0, 0, 0)
        style = Paint.Style.FILL
    }

    fun setPeekRect(rect: Rect) {
        peekRect = Rect(rect)
        invalidate()
    }

    fun setMaskAlpha(a: Int) {
        alpha = a.coerceIn(0, 255)
        paint.color = Color.argb(alpha, 0, 0, 0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        val pr = peekRect

        // Top band
        canvas.drawRect(0f, 0f, w.toFloat(), pr.top.toFloat(), paint)
        // Bottom band
        canvas.drawRect(0f, pr.bottom.toFloat(), w.toFloat(), h.toFloat(), paint)
        // Left band
        canvas.drawRect(0f, pr.top.toFloat(), pr.left.toFloat(), pr.bottom.toFloat(), paint)
        // Right band
        canvas.drawRect(pr.right.toFloat(), pr.top.toFloat(), w.toFloat(), pr.bottom.toFloat(), paint)
    }
}
