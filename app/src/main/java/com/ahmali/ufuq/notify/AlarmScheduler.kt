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
 * Keeps exactly one alarm set: the next checked prayer. Rescheduled when it fires, on boot,
 * on time or time-zone change, after an update, and whenever location or settings change.
 * Nothing is scheduled (and nothing wakes the phone) while notifications are off.
 */
object AlarmScheduler {
    const val CHANNEL_ID = "prayer_times_quiet"
    private const val EXTRA_PRAYER = "prayer"
    private const val EXTRA_AT = "at"

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun canPostNotifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun alarmIntent(ctx: Context, prayer: Prayer?, at: Long): PendingIntent {
        val i = Intent(ctx, PrayerAlarmReceiver::class.java)
        if (prayer != null) i.putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmIntent(app, null, 0))
        prefs.nextAlarmAt = 0
        if (!prefs.notificationsOn) return
        val place = prefs.currentPlace() ?: return
        val candidates = Schedule.notifiable(prefs).filter { prefs.notifyFor(it) }
        val now = ZonedDateTime.now(place.zone)
        val (prayer, time) = Schedule.next(place, now, candidates, prefs.calcSettings()) ?: return
        val at = time.toInstant().toEpochMilli()
        val pi = alarmIntent(app, prayer, at)
        val exact = canScheduleExact(app)
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        prefs.nextAlarmAt = at
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
    @Suppress("DEPRECATION")
    fun post(ctx: Context, prayer: Prayer, atMillis: Long) {
        if (!canPostNotifications(ctx)) return
        ensureChannel(ctx)
        val prefs = Prefs(ctx)
        val zone = prefs.currentPlace()?.zone ?: java.time.ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(atMillis).atZone(zone)
        val title = ctx.getString(R.string.notif_text, ctx.prayerName(prayer), TimeFmt(ctx, prefs).full(time))
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL_ID) else Notification.Builder(ctx)
        builder.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setWhen(atMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setColor(0xFF6B3E1F.toInt())
            .setCategory(Notification.CATEGORY_REMINDER)
        if (Build.VERSION.SDK_INT < 26) {
            builder.setDefaults(0).setSound(null).setVibrate(null).setPriority(Notification.PRIORITY_DEFAULT)
        }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(100 + prayer.ordinal, builder.build())
    }

    fun handleAlarm(ctx: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_PRAYER)
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val prayer = Prayer.entries.firstOrNull { it.name == name }
        val prefs = Prefs(ctx)
        // Only post if it's still wanted and not badly late (e.g. phone was off for hours).
        if (prayer != null && prefs.notificationsOn && prefs.notifyFor(prayer) &&
            System.currentTimeMillis() - at < 60 * 60_000
        ) {
            post(ctx, prayer, at)
        }
        reschedule(ctx)
    }
}
