package com.ahmali.ufuq.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.CalculationMethod
import com.ahmali.ufuq.core.MethodParams
import com.ahmali.ufuq.core.Twilight
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.data.Schedule
import com.ahmali.ufuq.notify.AlarmScheduler
import java.util.Locale

/** Custom angles (Custom method only) and per-prayer minute offsets (any method). */
class AdjustmentsActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        body = subPage(getString(R.string.adj_title))
        render()
    }

    private fun changed() {
        AlarmScheduler.reschedule(this)
        render()
    }

    private fun render() {
        body.removeAllViews()

        body.addView(sectionLabel(getString(R.string.adj_angles)).top(this, 8))
        if (prefs.method == CalculationMethod.CUSTOM) {
            val p = prefs.customParams
            val c = card()
            c.addView(stepper(getString(R.string.adj_fajr_angle), deg(p.fajrAngle),
                onMinus = { save(p.copy(fajrAngle = (p.fajrAngle - 0.5).coerceAtLeast(10.0))) },
                onPlus = { save(p.copy(fajrAngle = (p.fajrAngle + 0.5).coerceAtMost(22.0))) }))
            c.addView(rowDivider())
            c.addView(twilightEditor(getString(R.string.adj_maghrib), p.maghrib, isIsha = false) { save(p.copy(maghrib = it)) })
            c.addView(rowDivider())
            c.addView(twilightEditor(getString(R.string.adj_isha), p.isha, isIsha = true) { save(p.copy(isha = it)) })
            body.addView(c, matchWrap().apply { topMargin = dp(8) })
        } else {
            body.addView(text(getString(R.string.adj_angles_locked), 13f, Palette.MUTED).lines().apply {
                setPaddingRelative(dp(8), dp(6), dp(8), 0)
            })
        }

        body.addView(sectionLabel(getString(R.string.adj_offsets)).top(this, 24))
        body.addView(text(getString(R.string.adj_offsets_note), 12f, Palette.MUTED).lines().apply {
            setPaddingRelative(dp(8), dp(4), dp(8), 0)
        })
        val offs = card()
        val prayers = Schedule.todayRows(prefs)
        prayers.forEachIndexed { i, prayer ->
            val v = prefs.offset(prayer)
            offs.addView(stepper(prayerName(prayer), getString(R.string.value_min, v),
                onMinus = { prefs.setOffset(prayer, (v - 1).coerceAtLeast(-30)); changed() },
                onPlus = { prefs.setOffset(prayer, (v + 1).coerceAtMost(30)); changed() }))
            if (i < prayers.size - 1) offs.addView(rowDivider())
        }
        body.addView(offs, matchWrap().apply { topMargin = dp(8) })

        if (prefs.changedOffsets > 0) {
            body.addView(textButton(getString(R.string.adj_reset)) {
                prefs.clearOffsets()
                changed()
            }, wrapWrap().apply { topMargin = dp(8) })
        }
    }

    private fun save(p: MethodParams) {
        prefs.customParams = p
        changed()
    }

    private fun deg(v: Double) = getString(R.string.value_deg, num(v))

    private fun num(v: Double): String =
        if (v == Math.floor(v)) v.toInt().toString() else String.format(Locale.getDefault(), "%.1f", v)

    /** Maghrib/Isha: switch between an angle and minutes, then step the value. */
    private fun twilightEditor(title: String, t: Twilight, isIsha: Boolean, onChange: (Twilight) -> Unit): LinearLayout {
        val box = vbox()
        val modes = hbox().apply { setPaddingRelative(dp(16), dp(10), dp(16), 0) }
        modes.addView(text(title, 16f), weight1())
        val isAngle = t is Twilight.Angle
        modes.addView(segment(getString(R.string.adj_use_angle), isAngle) {
            if (!isAngle) onChange(Twilight.Angle(if (isIsha) 14.0 else 4.0))
        })
        modes.addView(segment(getString(R.string.adj_use_minutes), !isAngle) {
            if (isAngle) onChange(Twilight.Minutes(if (isIsha) 90.0 else 15.0))
        }, wrapWrap().apply { marginStart = dp(6) })
        box.addView(modes, matchWrap())
        when (t) {
            is Twilight.Angle -> box.addView(stepper("", deg(t.degrees),
                onMinus = { onChange(Twilight.Angle((t.degrees - 0.5).coerceAtLeast(if (isIsha) 10.0 else 0.0))) },
                onPlus = { onChange(Twilight.Angle((t.degrees + 0.5).coerceAtMost(if (isIsha) 22.0 else 10.0))) }))
            is Twilight.Minutes -> {
                val v = t.minutes.toInt()
                val label = getString(if (isIsha) R.string.value_after_maghrib else R.string.value_after_sunset, v)
                val step = if (isIsha) 5 else 1
                box.addView(stepper("", label,
                    onMinus = { onChange(Twilight.Minutes((v - step).coerceAtLeast(0).toDouble())) },
                    onPlus = { onChange(Twilight.Minutes((v + step).coerceAtMost(if (isIsha) 180 else 45).toDouble())) }))
            }
        }
        return box
    }

    private fun segment(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        text(label, 13f, if (selected) 0xFFFFFFFF.toInt() else Palette.ACCENT, Fonts.medium).apply {
            gravity = Gravity.CENTER
            minHeight = dp(34)
            minWidth = dp(72)
            setPadding(dp(12), 0, dp(12), 0)
            background = ripple(
                rounded(if (selected) Palette.ACCENT else 0, dpf(17), Palette.ACCENT, dp(1)),
                rounded(0xFFFFFFFF.toInt(), dpf(17))
            )
            isClickable = true
            isSelected = selected
            setOnClickListener { onClick() }
        }

    private fun stepper(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit): LinearLayout =
        hbox().apply {
            minimumHeight = dp(56)
            setPaddingRelative(dp(16), dp(4), dp(8), dp(4))
            addView(text(title, 16f), weight1())
            addView(roundButton("−", getString(R.string.minus) + " " + title, onMinus))
            addView(text(value, 15f, Palette.INK, Fonts.medium, tabular = true).apply {
                gravity = Gravity.CENTER
                minWidth = dp(64)
            })
            addView(roundButton("+", getString(R.string.plus) + " " + title, onPlus))
            contentDescription = "$title $value"
        }

    private fun roundButton(symbol: String, description: String, onClick: () -> Unit): TextView =
        text(symbol, 20f, Palette.ACCENT, Fonts.medium).apply {
            gravity = Gravity.CENTER
            background = ripple(rounded(Palette.ACCENT_SOFT, dpf(22)), rounded(0xFFFFFFFF.toInt(), dpf(22)))
            contentDescription = description
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
}
