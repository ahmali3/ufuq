package com.ahmali.ufuq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Filled compass disc that turns with the phone (ticks and N, E, S, W), a two-tone needle
 * pointing at the Qibla, and a small 3D Kaaba on the rim at its bearing. The fixed marker at
 * the top is where the phone points.
 * When [facing], a soft brown glow rings the dial.
 *
 * Drawn on a 260-unit square (the design's coordinates), scaled to the view.
 */
class CompassView(context: Context) : View(context) {

    /**
     * Phone heading, degrees from true north. The dial glides to it on every display frame
     * rather than jumping with each sensor reading, so it turns smoothly.
     */
    var heading: Float = 0f
        set(v) {
            field = v
            if (shownTime == 0L) shown = v
            postInvalidateOnAnimation()
        }

    /** Heading currently drawn, easing toward [heading]. */
    private var shown = 0f
    private var shownTime = 0L

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = min(w, context.dp(300))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        // Ease toward the latest heading along the shorter way round (time-based, so it feels the
        // same at 60 or 120 Hz). Tiny differences are ignored so the dial rests instead of trembling.
        val now = android.os.SystemClock.uptimeMillis()
        val dt = if (shownTime == 0L) 16f else (now - shownTime).coerceIn(1L, 100L).toFloat()
        shownTime = now
        val diff = ((heading - shown + 540f) % 360f) - 180f
        if (kotlin.math.abs(diff) > 0.15f) {
            shown = (shown + diff * (1f - kotlin.math.exp(-dt / 90f)) + 360f) % 360f
            postInvalidateOnAnimation()
        }
        val heading = shown

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
            canvas.drawCircle(c, c, 114f, stroke)
        }

        // Fixed "phone points here" marker.
        fill.color = Palette.INK
        tip.reset()
        tip.moveTo(124f, 0f); tip.lineTo(136f, 0f); tip.lineTo(c, 11f); tip.close()
        canvas.drawPath(tip, fill)

        // Filled disc with a lighter centre.
        fill.color = 0xFFEADFCB.toInt()
        canvas.drawCircle(c, c, 112f, fill)
        stroke.color = 0xFFD6C7AC.toInt()
        stroke.strokeWidth = hair
        canvas.drawCircle(c, c, 112f, stroke)
        fill.color = (Palette.BG and 0x00FFFFFF) or (0x8C shl 24)
        canvas.drawCircle(c, c, 64f, fill)

        // Dial: ticks every 5°, then N, E, S, W. Turns against the heading.
        canvas.save()
        canvas.rotate(-heading, c, c)
        stroke.color = 0xFFA8957A.toInt()
        stroke.strokeCap = Paint.Cap.BUTT
        for (i in 0 until 72) {
            val major = i % 6 == 0
            stroke.strokeWidth = maxOf(if (major) 1.6f else 1f, hair)
            canvas.save()
            canvas.rotate(i * 5f, c, c)
            canvas.drawLine(c, 22f, c, if (major) 34f else if (i % 2 == 0) 30f else 27f, stroke)
            canvas.restore()
        }
        stroke.strokeCap = Paint.Cap.ROUND
        for ((i, letter) in listOf("N", "E", "S", "W").withIndex()) {
            canvas.save()
            canvas.rotate(i * 90f, c, c)
            label.color = if (i == 0) accent else 0xFF8C7C64.toInt()
            label.textSize = if (i == 0) 16f else 12f
            canvas.drawText(letter, c, 56f, label)
            canvas.restore()
        }
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

        // 3D Kaaba on the rim at the Qibla bearing, kept upright.
        val rad = Math.toRadians(rel.toDouble())
        canvas.save()
        canvas.translate(c + 112f * sin(rad).toFloat(), c - 112f * cos(rad).toFloat())
        face(canvas, 0xFF45443D.toInt(), 0f, -14f, 13f, -8f, 0f, -2f, -13f, -8f)   // top
        face(canvas, 0xFF1A1A17.toInt(), -13f, -8f, 0f, -2f, 0f, 14f, -13f, 8f)    // left
        face(canvas, 0xFF2C2B26.toInt(), 13f, -8f, 0f, -2f, 0f, 14f, 13f, 8f)      // right
        face(canvas, 0xFFC9A35A.toInt(), -13f, -4f, 0f, 2f, 0f, 5.5f, -13f, -0.5f) // gold band, left
        face(canvas, 0xFFB08A40.toInt(), 13f, -4f, 0f, 2f, 0f, 5.5f, 13f, -0.5f)   // gold band, right
        canvas.restore()

        canvas.restore()
    }

    private fun face(canvas: Canvas, color: Int, vararg p: Float) {
        tail.reset()
        tail.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) { tail.lineTo(p[i], p[i + 1]); i += 2 }
        tail.close()
        fill.color = color
        canvas.drawPath(tail, fill)
    }
}
