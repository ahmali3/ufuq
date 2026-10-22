package com.ahmali.ufuq.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.Prayer
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.data.Schedule
import com.ahmali.ufuq.notify.AlarmScheduler
import java.util.Locale

/**
 * Quiet notifications, off by default. Permissions are asked only when switched on:
 * notifications (Android 13+), then exact alarms (Android 12+, optional — falls back to
 * slightly-late inexact alarms and says so).
 */
class NotificationsActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        body = subPage(getString(R.string.notif_title))
    }

    override fun onResume() {
        super.onResume()
        // Permissions may have changed in system settings.
        if (prefs.notificationsOn && !AlarmScheduler.canPostNotifications(this)) prefs.notificationsOn = false
        AlarmScheduler.reschedule(this)
        render()
    }

    private fun render() {
        body.removeAllViews()
        val on = prefs.notificationsOn

        val master = card()
        master.addView(switchRow(getString(R.string.notif_master), getString(R.string.notif_master_sub), on) { checked ->
            if (checked) turnOn() else {
                prefs.notificationsOn = false
                AlarmScheduler.reschedule(this)
                render()
            }
        })
        body.addView(master, matchWrap().apply { topMargin = dp(8) })

        if (on) {
            nextLine()?.let { line ->
                body.addView(text(line, 13f, Palette.MUTED).lines(1.35f).apply {
                    setPaddingRelative(dp(8), dp(8), dp(8), 0)
                })
            }
        }

        // The permission can be granted while the phone still hides the notifications.
        if (on && AlarmScheduler.canPostNotifications(this) && AlarmScheduler.blockedBySystem(this)) {
            body.addView(noteCard(getString(R.string.notif_blocked_system), getString(R.string.open_settings)) { openAppNotificationSettings() })
        }

        if (Build.VERSION.SDK_INT >= 33 && !AlarmScheduler.canPostNotifications(this) &&
            shouldShowBlockedNote
        ) {
            body.addView(noteCard(getString(R.string.notif_blocked), getString(R.string.open_settings)) { openAppNotificationSettings() })
        }

        if (on && !AlarmScheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= 31) {
            body.addView(noteCard(getString(R.string.exact_off), getString(R.string.allow_exact)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    openAppSettings()
                }
            })
        }

        body.addView(sectionLabel(getString(R.string.notify_me_for)).top(this, 20))
        val list = card().apply { alpha = if (on) 1f else 0.5f }
        val prayers = Schedule.notifiable(prefs)
        prayers.forEachIndexed { i, p ->
            val box = CheckBox(this).apply {
                isChecked = prefs.notifyFor(p)
                isEnabled = on
                buttonTintList = android.content.res.ColorStateList.valueOf(Palette.ACCENT)
                contentDescription = prayerName(p)
                setOnCheckedChangeListener { _, c ->
                    prefs.setNotifyFor(p, c)
                    AlarmScheduler.reschedule(this@NotificationsActivity)
                }
            }
            val row = hbox().apply {
                minimumHeight = dp(52)
                setPaddingRelative(dp(16), 0, dp(8), 0)
                isClickable = on
                background = ripple(null)
                setOnClickListener { if (on) box.toggle() }
            }
            row.addView(text(prayerName(p), 16f), weight1())
            row.addView(box, wrapWrap())
            list.addView(row, matchWrap())
            if (i < prayers.size - 1) list.addView(rowDivider())
        }
        body.addView(list, matchWrap().apply { topMargin = dp(8) })
        if (!prefs.showAsrIsha) {
            body.addView(text(getString(R.string.notif_hidden_note), 12f, Palette.MUTED).lines(1.35f).apply {
                setPaddingRelative(dp(8), dp(8), dp(8), 0)
            })
        }

        // Preview
        body.addView(sectionLabel(getString(R.string.preview)).top(this, 20))
        val preview = hbox().apply {
            background = rounded(0xFFFFFFFF.toInt(), dpf(18))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dpf(2)
        }
        val badge = FrameLayout(this).apply { background = rounded(0xFFE3D3B4.toInt(), dpf(18)) }
        badge.addView(icon(R.drawable.ic_notification, Palette.ACCENT, 20), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        preview.addView(badge, LinearLayout.LayoutParams(dp(36), dp(36)))
        val pt = vbox()
        pt.addView(text(getString(R.string.preview_now), 12f, Palette.MUTED))
        pt.addView(text(previewText(), 15f, Palette.INK, Fonts.medium))
        preview.addView(pt, weight1().apply { marginStart = dp(12) })
        body.addView(preview, matchWrap().apply { topMargin = dp(8); bottomMargin = dp(4) })

        if (on) {
            body.addView(outlineButton(getString(R.string.test_notif)) {
                val shown = AlarmScheduler.postTest(this)
                testResult = getString(if (shown) R.string.test_notif_sent else R.string.notif_blocked_system)
                render()
            }, wrapWrap().apply { topMargin = dp(16) })
            testResult?.let {
                body.addView(text(it, 13f, Palette.BODY).lines(1.4f).apply {
                    setPaddingRelative(dp(8), dp(8), dp(8), 0)
                })
            }
        }

        // One-time battery tip for phones that pause background apps.
        if (on && aggressiveOem() && !prefs.batteryTipDismissed) {
            val tip = vbox().apply {
                background = rounded(Palette.NAV_BG, dpf(16))
                setPadding(dp(16), dp(14), dp(16), dp(10))
            }
            tip.addView(text(getString(R.string.battery_title), 14f, Palette.INK, Fonts.bold))
            tip.addView(text(getString(R.string.battery_body), 13f, Palette.BODY).lines(1.4f).top(this, 6))
            val buttons = hbox()
            buttons.addView(outlineButton(getString(R.string.battery_button)) { openBatterySettings() })
            buttons.addView(textButton(getString(R.string.got_it)) {
                prefs.batteryTipDismissed = true
                render()
            }, wrapWrap().apply { marginStart = dp(8) })
            tip.addView(buttons, matchWrap().apply { topMargin = dp(10) })
            body.addView(tip, matchWrap().apply { topMargin = dp(20) })
        }
    }

    private var shouldShowBlockedNote = false
    private var testResult: String? = null

    /** "Next: Maghrib at 6:12 PM", noting when alarms may be a little late. */
    private fun nextLine(): String? {
        val at = prefs.nextAlarmAt
        if (at <= System.currentTimeMillis()) return null
        val prayer = Prayer.entries.firstOrNull { it.name == prefs.nextAlarmPrayer } ?: return null
        val zone = prefs.currentPlace()?.zone ?: java.time.ZoneId.systemDefault()
        val time = java.time.Instant.ofEpochMilli(at).atZone(zone)
        val line = getString(R.string.next_notif, prayerName(prayer), TimeFmt(this, prefs).full(time))
        return if (prefs.nextAlarmExact) line else line + " " + getString(R.string.next_notif_inexact)
    }

    /** Battery-optimisation list (no extra permission needed); falls back to app settings. */
    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun previewText(): String {
        val place = prefs.currentPlace()
        val fmt = TimeFmt(this, prefs)
        if (place != null) {
            val day = Schedule.day(place, java.time.LocalDate.now(place.zone), prefs.calcSettings())
            day[Prayer.MAGHRIB]?.let { return getString(R.string.notif_text, prayerName(Prayer.MAGHRIB), fmt.full(it)) }
        }
        return getString(R.string.notif_text, prayerName(Prayer.MAGHRIB), "6:42 PM")
    }

    private fun turnOn() {
        if (Build.VERSION.SDK_INT >= 33 && !AlarmScheduler.canPostNotifications(this)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ)
            return
        }
        prefs.notificationsOn = true
        AlarmScheduler.reschedule(this)
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            shouldShowBlockedNote = false
            turnOn()
        } else {
            shouldShowBlockedNote = true
            prefs.notificationsOn = false
            render()
        }
    }

    private fun noteCard(message: String, action: String, onAction: () -> Unit): LinearLayout =
        vbox().apply {
            background = rounded(Palette.NAV_BG, dpf(16))
            setPadding(dp(16), dp(12), dp(16), dp(10))
            addView(text(message, 13f, Palette.BODY).lines(1.4f))
            addView(outlineButton(action, onAction), wrapWrap().apply { topMargin = dp(10) })
            layoutParams = matchWrap().apply { topMargin = dp(12) }
        }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openAppNotificationSettings() {
        val i = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        try { startActivity(i) } catch (e: Exception) { openAppSettings() }
    }

    private fun aggressiveOem(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return listOf("vivo", "iqoo", "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "oneplus", "meizu").any { m.contains(it) }
    }

    companion object {
        private const val REQ = 30
    }
}
