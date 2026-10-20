package com.ahmali.ufuq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Compass dial that turns with the phone. The Qibla marker sits on the dial at its bearing;
 * the fixed line at the top is where the phone is pointing.
 */
class CompassView(context: Context) : View(context) {

    /** Phone heading, degrees from true north. */
    var heading: Float = 0f
        set(v) {
            field = v
            invalidate()
        }

    /** Qibla bearing, degrees from true north. */
    var bearing: Float = 0f
        set(v) {
            field = v
            invalidate()
        }

    var accent: Int = Palette.ACCENT
        set(v) {
            field = v
            invalidate()
        }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, context.dpf(1))
        color = 0xFFD6C7AC.toInt()
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = maxOf(1f, context.dpf(1))
        color = 0xFFB09D80.toInt()
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = context.dpf(2)
        color = Palette.INK
        strokeCap = Paint.Cap.ROUND
    }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = context.dpf(2)
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.INK
        textAlign = Paint.Align.CENTER
        textSize = context.dpf(14)
        typeface = Fonts.bold
    }
    private val square = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = min(w, context.dp(300))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width.toFloat()
        val c = s / 2f
        val r = s * 124f / 300f
        val tickOuter = s * 120f / 300f

        // Fixed "phone points here" line.
        canvas.drawLine(c, 0f, c, s * 26f / 300f, ink)

        canvas.save()
        canvas.rotate(-heading, c, c)
        canvas.drawCircle(c, c, r, ring)
        for (i in 0 until 72) {
            val long = i % 6 == 0
            canvas.save()
            canvas.rotate(i * 5f, c, c)
            canvas.drawLine(c, c - tickOuter, c, c - tickOuter + context.dpf(if (long) 10 else 5), tick)
            canvas.restore()
        }
        canvas.drawText("N", c, c - s * 92f / 300f + label.textSize / 3f, label)

        canvas.save()
        canvas.rotate(bearing, c, c)
        marker.color = accent
        canvas.drawLine(c, c, c, c - s * 110f / 300f, marker)
        val half = s * 9f / 300f
        square.set(c - half, c - r - half + context.dpf(2), c + half, c - r + half + context.dpf(2))
        marker.style = Paint.Style.FILL
        canvas.drawRoundRect(square, context.dpf(2), context.dpf(2), marker)
        marker.style = Paint.Style.STROKE
        canvas.restore()

        canvas.restore()
        canvas.drawCircle(c, c, context.dpf(5), ink.apply { style = Paint.Style.FILL })
        ink.style = Paint.Style.STROKE
    }
}
