package com.ahmali.ufuq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.ahmali.ufuq.core.SunPhase
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sun's path above the horizon by day, and a shallower path below it at night.
 * The sun moves along whichever arc is current.
 */
class SunArcView(context: Context) : View(context) {

    var phase: SunPhase? = null
        set(v) {
            field = v
            invalidate()
        }

    private val dayRise = context.dpf(90)
    private val nightDepth = context.dpf(40)
    private val horizonFromTop = context.dpf(112)

    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.5f)
        color = Palette.HAIRLINE_STRONG
        pathEffect = DashPathEffect(floatArrayOf(context.dpf(3), context.dpf(5)), 0f)
    }
    private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2)
        strokeCap = Paint.Cap.ROUND
        color = Palette.ACCENT
    }
    private val horizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = maxOf(1f, context.dpf(1))
        color = Palette.INK
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dayRect = RectF()
    private val nightRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (horizonFromTop + nightDepth + context.dpf(16)).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val pad = w * 20f / 342f
        val cx = w / 2f
        val cy = horizonFromTop
        val rx = cx - pad
        dayRect.set(cx - rx, cy - dayRise, cx + rx, cy + dayRise)
        nightRect.set(cx - rx, cy - nightDepth, cx + rx, cy + nightDepth)
        val p = phase

        if (p != null && !p.isDay) {
            fill.color = Palette.ACCENT
            fill.alpha = 13
            canvas.drawRect(0f, cy, w, height.toFloat(), fill)
        }

        canvas.drawArc(dayRect, 180f, 180f, false, dashed)
        if (p != null && !p.isDay) canvas.drawArc(nightRect, 0f, 180f, false, dashed)

        if (p != null) {
            val sweep = (180.0 * p.fraction).toFloat()
            val angle: Double
            val rect: RectF
            if (p.isDay) {
                progress.alpha = 255
                canvas.drawArc(dayRect, 180f, sweep, false, progress)
                angle = Math.toRadians(180.0 + 180.0 * p.fraction)
                rect = dayRect
            } else {
                progress.alpha = 140
                canvas.drawArc(nightRect, 0f, sweep, false, progress)
                angle = Math.toRadians(180.0 * p.fraction)
                rect = nightRect
            }
            canvas.drawLine(0f, cy, w, cy, horizon)
            val sx = rect.centerX() + (rect.width() / 2f) * cos(angle).toFloat()
            val sy = rect.centerY() + (rect.height() / 2f) * sin(angle).toFloat()
            fill.color = Palette.ACCENT
            fill.alpha = if (p.isDay) 38 else 26
            canvas.drawCircle(sx, sy, context.dpf(13), fill)
            fill.alpha = if (p.isDay) 255 else 153
            canvas.drawCircle(sx, sy, context.dpf(7), fill)
        } else {
            canvas.drawLine(0f, cy, w, cy, horizon)
        }
    }
}
