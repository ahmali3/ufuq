package com.ahmali.ufuq.ui

import android.app.Activity
import android.hardware.SensorManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.Qibla
import com.ahmali.ufuq.data.Prefs
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class QiblaPage(
    private val activity: Activity,
    private val prefs: Prefs,
    private val onUseLocation: () -> Unit
) {
    val view = ScrollView(activity).apply { isFillViewport = true }
    private val body = activity.vbox().apply {
        setPadding(activity.dp(24), activity.dp(24), activity.dp(24), activity.dp(24))
    }
    private var compass: CompassView? = null
    private var instruction: TextView? = null
    private var accuracyText: TextView? = null
    private var accuracyDot: android.view.View? = null
    private var calibrate: TextView? = null
    private var bearing = 0.0
    private var lastAnnounced = Int.MIN_VALUE
    private val sensor = CompassSensor(activity) { heading, accuracy -> onHeading(heading, accuracy) }
    private var visible = false

    init {
        view.addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun refresh() = with(activity) {
        body.removeAllViews()
        compass = null
        val place = prefs.currentPlace()
        if (place == null) {
            body.addView(placeHeader(null, getString(R.string.tab_qibla), prefs))
            body.addView(needPlaceCard(onUseLocation).top(this, 32))
            return@with
        }
        bearing = Qibla.bearing(place.latitude, place.longitude)
        val km = NumberFormat.getIntegerInstance(Locale.getDefault()).format(Qibla.distanceKm(place.latitude, place.longitude).roundToInt())
        val bearingText = String.format(Locale.getDefault(), "%.1f", bearing)
        sensor.setLocation(place.latitude, place.longitude)

        body.addView(placeHeader(place, getString(R.string.qibla_info, bearingText, km), prefs))

        if (!sensor.available) {
            body.addView(text(getString(R.string.no_compass, bearingText), 18f, Palette.INK, Fonts.medium).lines().apply {
                gravity = Gravity.CENTER
            }, matchWrap().apply { topMargin = dp(48) })
            body.addView(text(getString(R.string.keep_away), 12f, Palette.FAINT).apply { gravity = Gravity.CENTER }, matchWrap().apply { topMargin = dp(16) })
            return@with
        }

        instruction = text(getString(R.string.hold_flat), 32f, Palette.INK, Fonts.medium).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(instruction, matchWrap().apply { topMargin = dp(36) })
        body.addView(text(getString(R.string.hold_flat), 14f, Palette.MUTED).apply { gravity = Gravity.CENTER }, matchWrap().apply { topMargin = dp(4) })

        compass = CompassView(this).apply {
            this.bearing = this@QiblaPage.bearing.toFloat()
            contentDescription = getString(R.string.compass_desc)
        }
        body.addView(compass, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(28)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        val acc = hbox().apply { gravity = Gravity.CENTER }
        accuracyDot = android.view.View(this).apply { background = rounded(Palette.OK, dpf(4)) }
        acc.addView(accuracyDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
        accuracyText = text(getString(R.string.accuracy_high), 13f, Palette.MUTED)
        acc.addView(accuracyText)
        body.addView(acc, matchWrap().apply { topMargin = dp(28) })
        calibrate = text(getString(R.string.calibrate), 13f, Palette.ACCENT, Fonts.medium).apply {
            gravity = Gravity.CENTER
            visibility = android.view.View.GONE
        }
        body.addView(calibrate, matchWrap().apply { topMargin = dp(6) })
        body.addView(text(getString(R.string.keep_away), 12f, Palette.FAINT).apply { gravity = Gravity.CENTER }, matchWrap().apply { topMargin = dp(8) })
    }

    fun onShow() {
        visible = true
        sensor.start()
    }

    fun onHide() {
        visible = false
        sensor.stop()
    }

    private fun onHeading(heading: Float, accuracy: Int) {
        val c = compass ?: return
        c.heading = heading
        val turn = Qibla.turn(heading.toDouble(), bearing)
        val deg = abs(turn).roundToInt()
        val facing = abs(turn) <= 3.0
        c.facing = facing
        instruction?.let {
            val textNow = when {
                facing -> activity.getString(R.string.facing_qibla)
                turn > 0 -> activity.getString(R.string.turn_right, deg)
                else -> activity.getString(R.string.turn_left, deg)
            }
            if (it.text != textNow) it.text = textNow
            it.setTextColor(if (facing) Palette.ACCENT else Palette.INK)
        }
        if (facing && lastAnnounced != 0) {
            lastAnnounced = 0
            c.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (!facing) {
            lastAnnounced = deg
        }
        val (label, color) = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.accuracy_high to Palette.OK
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> R.string.accuracy_medium to Palette.WARN
            else -> R.string.accuracy_low to 0xFFB3412C.toInt()
        }
        accuracyText?.text = activity.getString(label)
        accuracyDot?.background = rounded(color, activity.dpf(4))
        calibrate?.visibility = if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) android.view.View.VISIBLE else android.view.View.GONE
    }
}
