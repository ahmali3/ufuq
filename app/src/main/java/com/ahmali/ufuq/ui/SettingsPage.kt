package com.ahmali.ufuq.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.text.format.DateFormat
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import com.ahmali.ufuq.BuildConfig
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.AsrMethod
import com.ahmali.ufuq.core.HighLatitudeRule
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.data.TimeFormat
import com.ahmali.ufuq.notify.AlarmScheduler

class SettingsPage(private val activity: Activity, private val prefs: Prefs) {
    val view = ScrollView(activity).apply { isFillViewport = true }
    private val body = activity.vbox().apply {
        setPadding(activity.dp(24), activity.dp(24), activity.dp(24), activity.dp(24))
    }

    init {
        view.addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun refresh() = with(activity) {
        body.removeAllViews()
        body.addView(text(getString(R.string.settings_title), 28f, Palette.INK, Fonts.bold))

        // Location
        body.addView(sectionLabel(getString(R.string.section_location)).top(this, 20))
        val loc = card()
        val cityValue = if (prefs.autoLocation) {
            prefs.autoLabel?.substringBefore(",")?.let { getString(R.string.city_auto_value, it) }
                ?: getString(R.string.city_auto_unknown)
        } else {
            prefs.manualCity?.name ?: "—"
        }
        loc.addView(valueRow(getString(R.string.row_city), cityValue) { open(CityActivity::class.java) })
        body.addView(loc, matchWrap().apply { topMargin = dp(8) })

        // Prayer times
        body.addView(sectionLabel(getString(R.string.section_prayer_times)).top(this, 20))
        val pt = card()
        val method = prefs.method
        pt.addView(valueRow(getString(R.string.row_method), methodShort(method)) { open(MethodActivity::class.java) })
        pt.addView(rowDivider())
        if (method.asrLocked) {
            pt.addView(valueRow(getString(R.string.row_asr), getString(R.string.asr_locked), chevron = false))
        } else {
            val asrValue = getString(if (prefs.asr == AsrMethod.HANAFI) R.string.asr_hanafi else R.string.asr_standard)
            pt.addView(valueRow(getString(R.string.row_asr), asrValue) { chooseAsr() })
        }
        pt.addView(rowDivider())
        val hl = getString(
            when (prefs.highLatitude) {
                HighLatitudeRule.ANGLE_BASED -> R.string.hl_angle
                HighLatitudeRule.MIDDLE_OF_NIGHT -> R.string.hl_middle
                HighLatitudeRule.ONE_SEVENTH -> R.string.hl_seventh
            }
        )
        pt.addView(valueRow(getString(R.string.row_high_lat), hl) { chooseHighLat() })
        pt.addView(rowDivider())
        pt.addView(switchRow(getString(R.string.row_show_asr_isha), getString(R.string.show_asr_isha_sub), prefs.showAsrIsha) { on ->
            prefs.showAsrIshaOverride = on
            AlarmScheduler.reschedule(this)
        })
        pt.addView(rowDivider())
        val changed = prefs.changedOffsets
        val adj = if (changed == 0) getString(R.string.adjustments_none) else getString(R.string.adjustments_some, changed)
        pt.addView(valueRow(getString(R.string.row_adjustments), adj) { open(AdjustmentsActivity::class.java) })
        body.addView(pt, matchWrap().apply { topMargin = dp(8) })

        // General
        body.addView(sectionLabel(getString(R.string.section_general)).top(this, 20))
        val gen = card()
        gen.addView(valueRow(getString(R.string.row_notifications), getString(if (prefs.notificationsOn) R.string.on else R.string.off)) {
            open(NotificationsActivity::class.java)
        })
        gen.addView(rowDivider())
        gen.addView(valueRow(getString(R.string.row_time_format), timeFormatLabel()) { chooseTimeFormat() })
        gen.addView(rowDivider())
        gen.addView(valueRow(getString(R.string.row_about), "v${BuildConfig.VERSION_NAME}") { open(AboutActivity::class.java) })
        body.addView(gen, matchWrap().apply { topMargin = dp(8) })
    }

    private fun open(cls: Class<*>) = activity.startActivity(Intent(activity, cls))

    private fun timeFormatLabel(): String = with(activity) {
        when (prefs.timeFormat) {
            TimeFormat.H12 -> getString(R.string.tf_12)
            TimeFormat.H24 -> getString(R.string.tf_24)
            TimeFormat.SYSTEM -> getString(
                R.string.tf_system,
                getString(if (DateFormat.is24HourFormat(this)) R.string.tf_24 else R.string.tf_12)
            )
        }
    }

    private fun chooseAsr() = with(activity) {
        val items = arrayOf(getString(R.string.asr_standard_desc), getString(R.string.asr_hanafi_desc))
        AlertDialog.Builder(this)
            .setTitle(R.string.row_asr)
            .setSingleChoiceItems(items, if (prefs.asr == AsrMethod.HANAFI) 1 else 0) { d, which ->
                prefs.asr = if (which == 1) AsrMethod.HANAFI else AsrMethod.STANDARD
                d.dismiss()
                AlarmScheduler.reschedule(this)
                refresh()
            }
            .show()
    }

    private fun chooseHighLat() = with(activity) {
        val items = arrayOf(getString(R.string.hl_angle), getString(R.string.hl_middle), getString(R.string.hl_seventh))
        val order = listOf(HighLatitudeRule.ANGLE_BASED, HighLatitudeRule.MIDDLE_OF_NIGHT, HighLatitudeRule.ONE_SEVENTH)
        AlertDialog.Builder(this)
            .setTitle(R.string.row_high_lat)
            .setSingleChoiceItems(items, order.indexOf(prefs.highLatitude)) { d, which ->
                prefs.highLatitude = order[which]
                d.dismiss()
                AlarmScheduler.reschedule(this)
                refresh()
            }
            .show()
    }

    private fun chooseTimeFormat() = with(activity) {
        val order = listOf(TimeFormat.SYSTEM, TimeFormat.H12, TimeFormat.H24)
        val items = arrayOf(
            getString(R.string.tf_system, getString(if (DateFormat.is24HourFormat(this)) R.string.tf_24 else R.string.tf_12)),
            getString(R.string.tf_12),
            getString(R.string.tf_24)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.row_time_format)
            .setSingleChoiceItems(items, order.indexOf(prefs.timeFormat)) { d, which ->
                prefs.timeFormat = order[which]
                d.dismiss()
                refresh()
            }
            .show()
    }
}

/** A row with a title, optional subtitle and a switch. The whole row toggles it. */
fun Activity.switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout =
    hbox().apply {
        minimumHeight = dp(60)
        setPaddingRelative(dp(16), dp(8), dp(12), dp(8))
        val texts = vbox()
        texts.addView(text(title, 16f))
        if (subtitle != null) texts.addView(text(subtitle, 12f, Palette.MUTED).top(this@switchRow, 2))
        addView(texts, weight1())
        val sw = Switch(this@switchRow).apply {
            isChecked = checked
            contentDescription = title
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        addView(sw, wrapWrap().apply { marginStart = dp(12) })
        isClickable = true
        background = ripple(null)
        setOnClickListener { sw.toggle() }
        gravity = Gravity.CENTER_VERTICAL
    }
