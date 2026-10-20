package com.ahmali.ufuq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Compass dial that turns with the phone, with a two-tone needle pointing at the Qibla and a
 * small Kaaba on the ring at its bearing. The fixed line at the top is where the phone points.
 * When [facing], a soft brown glow rings the dial.
 *
 * Drawn on a 260-unit square (the design's coordinates), scaled to the view.
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

    /** True when the phone points at the Qibla (within a few degrees). */
    var facing: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    var accent: Int = Palette.ACCENT
        set(v) {
            field = v
            invalidate()
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.MUTED
        textAlign = Paint.Align.CENTER
        typeface = Fonts.bold
    }
    private val tip = Path()
    private val tail = Path()
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = min(w, context.dp(300))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val k = width / 260f
        canvas.save()
        canvas.scale(k, k)
        val c = 130f
        // Keep hairlines at least one physical pixel.
        val hair = maxOf(1f / k, context.dpf(1) / k)

        // Glow when facing the Qibla.
        if (facing) {
            stroke.color = (accent and 0x00FFFFFF) or (0x40 shl 24)
            stroke.strokeWidth = 10f
            canvas.drawCircle(c, c, 105f, stroke)
        }

        // Fixed "phone points here" line.
        stroke.color = Palette.INK
        stroke.strokeWidth = 2f
        canvas.drawLine(c, 4f, c, 20f, stroke)

        // Ring.
        stroke.color = 0xFFD6C7AC.toInt()
        stroke.strokeWidth = hair
        canvas.drawCircle(c, c, 105f, stroke)

        // Dial: ticks every 5°, longer every 30°, and N. Turns against the heading.
        canvas.save()
        canvas.rotate(-heading, c, c)
        stroke.color = 0xFFB09D80.toInt()
        stroke.strokeCap = Paint.Cap.BUTT
        for (i in 0 until 72) {
            canvas.save()
            canvas.rotate(i * 5f, c, c)
            canvas.drawLine(c, 25f, c, if (i % 6 == 0) 37f else 32f, stroke)
            canvas.restore()
        }
        stroke.strokeCap = Paint.Cap.ROUND
        label.textSize = 13f
        canvas.drawText("N", c, 56f, label)
        canvas.restore()

        // Needle toward the Qibla: brown tip, sand tail.
        val rel = bearing - heading
        canvas.save()
        canvas.rotate(rel, c, c)
        tip.reset()
        tip.moveTo(c, 58f); tip.lineTo(140f, c); tip.lineTo(120f, c); tip.close()
        tail.reset()
        tail.moveTo(120f, c); tail.lineTo(140f, c); tail.lineTo(c, 196f); tail.close()
        fill.color = accent
        canvas.drawPath(tip, fill)
        fill.color = 0xFFCDBB9C.toInt()
        canvas.drawPath(tail, fill)
        canvas.restore()

        // Hub.
        fill.color = Palette.BG
        canvas.drawCircle(c, c, 7f, fill)
        stroke.color = Palette.INK
        stroke.strokeWidth = 2f
        canvas.drawCircle(c, c, 7f, stroke)

        // Kaaba on the ring at the Qibla bearing, kept upright.
        val rad = Math.toRadians(rel.toDouble())
        val kx = c + 105f * sin(rad).toFloat()
        val ky = c - 105f * cos(rad).toFloat()
        fill.color = Palette.INK
        rect.set(kx - 12f, ky - 12f, kx + 12f, ky + 12f)
        canvas.drawRoundRect(rect, 3f, 3f, fill)
        fill.color = 0xFFC9A35A.toInt()
        rect.set(kx - 12f, ky - 6f, kx + 12f, ky - 2.5f)
        canvas.drawRect(rect, fill)

        canvas.restore()
    }
}
