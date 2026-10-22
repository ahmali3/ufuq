package com.ahmali.ufuq.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.Prayer
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.data.Schedule
import com.ahmali.ufuq.ui.MainActivity
import com.ahmali.ufuq.ui.TimeFmt
import com.ahmali.ufuq.ui.prayerName
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Keeps alarms set for the next few checked prayers (not just one, so a single alarm the
 * phone drops doesn't stop the rest). Rescheduled when one fires, on boot, on time or
 * time-zone change, after an update, whenever the app opens, and when settings change.
 * Nothing is scheduled (and nothing wakes the phone) while notifications are off.
 */
object AlarmScheduler {
    const val CHANNEL_ID = "prayer_times_quiet"
    private const val EXTRA_PRAYER = "prayer"
    private const val EXTRA_AT = "at"
    private const val AHEAD = 4
    private const val FIRST_CODE = 10
    private const val LEGACY_CODE = 1

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun canPostNotifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * True when the phone itself blocks Ufuq's notifications, even though the permission is
     * granted: the app's notifications or its channel are switched off in system settings.
     * Some phones (e.g. vivo, Xiaomi) do this by default for apps not installed from their store.
     */
    fun blockedBySystem(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled()) return true
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = nm.getNotificationChannel(CHANNEL_ID)
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return true
        }
        return false
    }

    private fun alarmIntent(ctx: Context, code: Int, prayer: Prayer?, at: Long): PendingIntent {
        val i = Intent(ctx, PrayerAlarmReceiver::class.java)
        if (prayer != null) i.putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The next [count] checked prayer times after [now], in order. */
    private fun upcoming(prefs: Prefs, now: ZonedDateTime, count: Int): List<Pair<Prayer, ZonedDateTime>> {
        val place = prefs.currentPlace() ?: return emptyList()
        val candidates = Schedule.notifiable(prefs).filter { prefs.notifyFor(it) }
        if (candidates.isEmpty()) return emptyList()
        val settings = prefs.calcSettings()
        val today = now.withZoneSameInstant(place.zone).toLocalDate()
        val out = ArrayList<Pair<Prayer, ZonedDateTime>>()
        for (offset in 0L..3L) {
            val day = Schedule.day(place, today.plusDays(offset), settings)
            candidates.mapNotNull { p -> day[p]?.let { p to it } }
                .filter { it.second.isAfter(now) }
                .sortedBy { it.second }
                .forEach { out += it }
            if (out.size >= count) break
        }
        return out.take(count)
    }

    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmIntent(app, LEGACY_CODE, null, 0))
        for (k in 0 until AHEAD) am.cancel(alarmIntent(app, FIRST_CODE + k, null, 0))
        prefs.nextAlarmAt = 0
        if (!prefs.notificationsOn) return
        val zone = prefs.currentPlace()?.zone ?: return
        val next = upcoming(prefs, ZonedDateTime.now(zone), AHEAD)
        if (next.isEmpty()) return
        val exact = canScheduleExact(app)
        next.forEachIndexed { k, (prayer, time) ->
            val at = time.toInstant().toEpochMilli()
            val pi = alarmIntent(app, FIRST_CODE + k, prayer, at)
            try {
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        prefs.nextAlarmAt = next.first().second.toInstant().toEpochMilli()
        prefs.nextAlarmPrayer = next.first().first.name
        prefs.nextAlarmExact = exact
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = ctx.getString(R.string.channel_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    /** Builds the quiet "Maghrib — 6:42 PM" notification. */
    fun post(ctx: Context, prayer: Prayer, atMillis: Long) {
        val prefs = Prefs(ctx)
        val zone = prefs.currentPlace()?.zone ?: java.time.ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(atMillis).atZone(zone)
        val title = ctx.getString(R.string.notif_text, ctx.prayerName(prayer), TimeFmt(ctx, prefs).full(time))
        show(ctx, 100 + prayer.ordinal, title, null, atMillis)
    }

    /** A one-off notification so the user can check their phone lets Ufuq's through. */
    fun postTest(ctx: Context): Boolean {
        if (!canPostNotifications(ctx)) return false
        show(ctx, 99, ctx.getString(R.string.test_notif_title), ctx.getString(R.string.test_notif_body), System.currentTimeMillis())
        return !blockedBySystem(ctx)
    }

    @Suppress("DEPRECATION")
    private fun show(ctx: Context, id: Int, title: String, text: String?, whenMillis: Long) {
        if (!canPostNotifications(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL_ID) else Notification.Builder(ctx)
        builder.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setWhen(whenMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setColor(0xFF6B3E1F.toInt())
            .setCategory(Notification.CATEGORY_REMINDER)
        if (text != null) builder.setContentText(text)
        if (Build.VERSION.SDK_INT < 26) {
            builder.setDefaults(0).setSound(null).setVibrate(null).setPriority(Notification.PRIORITY_DEFAULT)
        }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(id, builder.build())
    }

    fun handleAlarm(ctx: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_PRAYER)
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val prayer = Prayer.entries.firstOrNull { it.name == name }
        val prefs = Prefs(ctx)
        // Only post if it's still wanted, not badly late (phone off for hours), and not already shown.
        if (prayer != null && prefs.notificationsOn && prefs.notifyFor(prayer) &&
            System.currentTimeMillis() - at < 60 * 60_000 && prefs.lastPostedAt != at
        ) {
            prefs.lastPostedAt = at
            post(ctx, prayer, at)
        }
        reschedule(ctx)
    }
}
