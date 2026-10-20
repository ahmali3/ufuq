package com.ahmali.ufuq.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at a prayer time: posts the quiet notification and schedules the next one. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.handleAlarm(context, intent)
    }
}

/** Boot, app update, clock or time-zone change, exact-alarm permission change: reschedule. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" ->
                AlarmScheduler.reschedule(context)
        }
    }
}
