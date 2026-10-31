package com.ahmali.ufuq.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.Prayer
import com.ahmali.ufuq.data.AlertStyle
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.data.Schedule
import com.ahmali.ufuq.ui.MainActivity
import com.ahmali.ufuq.ui.TimeFmt
import com.ahmali.ufuq.ui.prayerName
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Keeps alarms set for the next few checked prayers (not just one, so a single alarm the
 * phone drops doesn't stop the rest). Rescheduled when one fires, on boot, on time or
 * time-zone change, after an update, whenever the app opens, when settings change, and by an
 * hourly watchdog job. When an alarm was dropped or held back, the watchdog posts the reminder
 * late (within an hour) instead of losing it, and the Notifications screen explains why.
 * Nothing is scheduled (and nothing wakes the phone) while notifications are off.
 */
object AlarmScheduler {
    /** One channel per alert style; Android fixes a channel's sound once it is created. */
    private const val CHANNEL_SILENT = "prayer_times_quiet"
    private const val CHANNEL_VIBRATE = "prayer_times_vibrate"
    private const val CHANNEL_SOUND = "prayer_times_sound"
    private val ALL_CHANNELS = listOf(CHANNEL_SILENT, CHANNEL_VIBRATE, CHANNEL_SOUND)

    fun channelId(style: AlertStyle): String = when (style) {
        AlertStyle.SILENT -> CHANNEL_SILENT
        AlertStyle.VIBRATE -> CHANNEL_VIBRATE
        AlertStyle.SOUND -> CHANNEL_SOUND
    }
    private const val EXTRA_PRAYER = "prayer"
    private const val EXTRA_AT = "at"
    private const val AHEAD = 4
    private const val FIRST_CODE = 10
    private const val LEGACY_CODE = 1
    private const val WATCHDOG_JOB = 7001
    private const val LATE_LIMIT_MS = 60 * 60_000L
    private const val MISSED_AFTER_MS = 10 * 60_000L

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
            val ch = nm.getNotificationChannel(channelId(Prefs(ctx).alertStyle))
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return true
        }
        return false
    }

    /** True when a reminder in the last three days never fired and none has fired since. */
    fun recentlyMissed(prefs: Prefs): Boolean {
        val m = prefs.missedAt
        return m > 0 && System.currentTimeMillis() - m < 3 * 24 * 3600_000L && prefs.lastFiredAt < m
    }

    private fun alarmIntent(ctx: Context, code: Int, prayer: Prayer?, at: Long): PendingIntent {
        val i = Intent(ctx, PrayerAlarmReceiver::class.java)
        if (prayer != null) i.putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Checked prayer times from [from] (exclusive) onward, in order, at most [count]. */
    private fun timesAfter(prefs: Prefs, from: ZonedDateTime, count: Int): List<Pair<Prayer, ZonedDateTime>> {
        val place = prefs.currentPlace() ?: return emptyList()
        val candidates = Schedule.notifiable(prefs).filter { prefs.notifyFor(it) }
        if (candidates.isEmpty()) return emptyList()
        val settings = prefs.calcSettings()
        val first = from.withZoneSameInstant(place.zone).toLocalDate()
        val out = ArrayList<Pair<Prayer, ZonedDateTime>>()
        for (offset in 0L..3L) {
            val day = Schedule.day(place, first.plusDays(offset), settings)
            candidates.mapNotNull { p -> day[p]?.let { p to it } }
                .filter { it.second.isAfter(from) }
                .sortedBy { it.second }
                .forEach { out += it }
            if (out.size >= count) break
        }
        return out.take(count)
    }

    /**
     * Sets the next alarms. [reason] goes in the log. With [catchUp], a checked prayer from the
     * last hour whose reminder never appeared is posted now (used by the watchdog and on boot,
     * not when the user opens the app).
     */
    fun reschedule(ctx: Context, reason: String = "app", catchUp: Boolean = false) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()

        // Did the phone swallow the last alarm we set? (Its time passed, but it never fired.)
        val expected = prefs.nextAlarmAt
        if (prefs.notificationsOn && expected > 0 && now - expected > MISSED_AFTER_MS &&
            prefs.lastFiredAt < expected && prefs.missedAt != expected
        ) {
            prefs.missedAt = expected
            prefs.missedPrayer = prefs.nextAlarmPrayer
            val missed = Prayer.entries.firstOrNull { it.name == prefs.nextAlarmPrayer }
            log(app, "Missed: ${missed?.let { app.prayerName(it) } ?: "reminder"} at ${clock(expected)} never fired (phone held or cleared the alarm)")
        }

        if (catchUp && prefs.notificationsOn) catchUpRecent(app, prefs, now)

        am.cancel(alarmIntent(app, LEGACY_CODE, null, 0))
        for (k in 0 until AHEAD) am.cancel(alarmIntent(app, FIRST_CODE + k, null, 0))
        if (!prefs.notificationsOn) {
            prefs.nextAlarmAt = 0
            setWatchdog(app, false)
            return
        }
        val zone = prefs.currentPlace()?.zone
        if (zone == null) {
            prefs.nextAlarmAt = 0
            log(app, "Nothing scheduled: no location yet")
            return
        }
        val next = timesAfter(prefs, ZonedDateTime.now(zone), AHEAD)
        if (next.isEmpty()) {
            prefs.nextAlarmAt = 0
            return
        }
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
        val firstAt = next.first().second.toInstant().toEpochMilli()
        val changed = firstAt != prefs.nextAlarmAt || exact != prefs.nextAlarmExact
        prefs.nextAlarmAt = firstAt
        prefs.nextAlarmPrayer = next.first().first.name
        prefs.nextAlarmExact = exact
        if (changed || (reason != "app" && reason != "watchdog")) {
            log(app, "Set ${next.size} alarms (${if (exact) "exact" else "approximate"}), next ${app.prayerName(next.first().first)} ${clock(firstAt)} [$reason]")
        }
        setWatchdog(app, true)
    }

    /** Post the latest checked prayer of the last hour if its reminder never appeared. */
    private fun catchUpRecent(ctx: Context, prefs: Prefs, now: Long) {
        val zone = prefs.currentPlace()?.zone ?: return
        val from = Instant.ofEpochMilli(now - LATE_LIMIT_MS).atZone(zone)
        val due = timesAfter(prefs, from, 8).filter { !it.second.toInstant().isAfter(Instant.ofEpochMilli(now)) }
        val last = due.lastOrNull() ?: return
        val at = last.second.toInstant().toEpochMilli()
        if (prefs.lastPostedAt >= at) return
        prefs.lastPostedAt = at
        post(ctx, last.first, at)
        log(ctx, "Posted ${ctx.prayerName(last.first)} late by ${(now - at) / 60_000} min (catch-up)")
    }

    /** Hourly job that re-sets the alarms and catches up a missed reminder. */
    private fun setWatchdog(ctx: Context, on: Boolean) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        try {
            if (!on) {
                js.cancel(WATCHDOG_JOB)
                return
            }
            if (js.allPendingJobs.any { it.id == WATCHDOG_JOB }) return
            val job = JobInfo.Builder(WATCHDOG_JOB, ComponentName(ctx, WatchdogJob::class.java))
                .setPeriodic(60 * 60_000L)
                .setPersisted(true)
                .build()
            js.schedule(job)
        } catch (e: Exception) {
            log(ctx, "Watchdog not available: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Makes sure the channel for the chosen alert style exists, and removes the other styles'
     * channels so system settings only ever lists one "Prayer times" category.
     */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val style = Prefs(ctx).alertStyle
        val id = channelId(style)
        for (other in ALL_CHANNELS) {
            if (other != id && nm.getNotificationChannel(other) != null) nm.deleteNotificationChannel(other)
        }
        if (nm.getNotificationChannel(id) != null) return
        val ch = NotificationChannel(id, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = ctx.getString(R.string.channel_desc)
            // SOUND keeps the channel's default: the phone's notification sound.
            if (style != AlertStyle.SOUND) setSound(null, null)
            enableVibration(style == AlertStyle.VIBRATE)
            enableLights(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    /** Builds the "Maghrib at 6:42 PM" notification. */
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
        log(ctx, "Test notification sent")
        return !blockedBySystem(ctx)
    }

    @Suppress("DEPRECATION")
    private fun show(ctx: Context, id: Int, title: String, text: String?, whenMillis: Long) {
        if (!canPostNotifications(ctx)) {
            log(ctx, "Not shown: notification permission is off")
            return
        }
        if (blockedBySystem(ctx)) log(ctx, "Warning: the phone has Ufuq's notifications switched off")
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = Prefs(ctx).alertStyle
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, channelId(style)) else Notification.Builder(ctx)
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
            // Before Android 8 there are no channels: the alert is set on each notification.
            when (style) {
                AlertStyle.SILENT -> builder.setDefaults(0).setSound(null).setVibrate(null)
                AlertStyle.VIBRATE -> builder.setDefaults(Notification.DEFAULT_VIBRATE).setSound(null)
                AlertStyle.SOUND -> builder.setDefaults(Notification.DEFAULT_SOUND).setVibrate(null)
            }
            builder.setPriority(Notification.PRIORITY_DEFAULT)
        }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(id, builder.build())
    }

    fun handleAlarm(ctx: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_PRAYER)
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val prayer = Prayer.entries.firstOrNull { it.name == name }
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()
        if (prayer != null && at > prefs.lastFiredAt) prefs.lastFiredAt = at
        val lateMin = (now - at) / 60_000
        when {
            prayer == null -> log(ctx, "Alarm fired without a prayer")
            !prefs.notificationsOn || !prefs.notifyFor(prayer) -> log(ctx, "${ctx.prayerName(prayer)} alarm fired; reminder now switched off")
            prefs.lastPostedAt == at -> {}
            now - at >= LATE_LIMIT_MS -> log(ctx, "${ctx.prayerName(prayer)} alarm fired $lateMin min late; too late to show")
            else -> {
                prefs.lastPostedAt = at
                post(ctx, prayer, at)
                log(ctx, "Posted ${ctx.prayerName(prayer)}" + if (lateMin >= 2) " ($lateMin min late)" else "")
            }
        }
        reschedule(ctx, "alarm")
    }

    // ---- Event log (kept on the phone, shown in Notifications so delivery problems are visible) ----

    private val logTime = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ROOT)

    fun log(ctx: Context, message: String) {
        val prefs = Prefs(ctx)
        val stamp = logTime.format(java.time.LocalDateTime.now())
        val lines = (listOf("$stamp  $message") + prefs.notifLog.split('\n').filter { it.isNotBlank() }).take(40)
        prefs.notifLog = lines.joinToString("\n")
    }

    private fun clock(millis: Long): String =
        DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))
}
