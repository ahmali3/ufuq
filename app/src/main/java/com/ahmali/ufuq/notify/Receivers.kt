package com.ahmali.ufuq.notify

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at a prayer time: posts the quiet notification and schedules the next ones. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.handleAlarm(context, intent)
    }
}

/** Boot, app update, clock or time-zone change, exact-alarm permission change: reschedule. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "update"
            Intent.ACTION_TIME_CHANGED -> "clock changed"
            Intent.ACTION_TIMEZONE_CHANGED -> "time zone changed"
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> "exact alarms allowed"
            else -> return
        }
        AlarmScheduler.reschedule(context, reason, catchUp = true)
    }
}

/**
 * Hourly safety net. If the phone held back or dropped an alarm, this re-sets the alarms and
 * posts a reminder from the last hour that never appeared.
 */
class WatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        AlarmScheduler.reschedule(this, "watchdog", catchUp = true)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
