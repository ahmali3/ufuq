package com.ahmali.ufuq.data

import com.ahmali.ufuq.core.CalcSettings
import com.ahmali.ufuq.core.DayTimes
import com.ahmali.ufuq.core.Prayer
import com.ahmali.ufuq.core.PrayerCalculator
import java.time.LocalDate
import java.time.ZonedDateTime

/** Which times appear where, following the method and the "Show Asr and Isha" setting. */
object Schedule {

    fun day(place: Place, date: LocalDate, settings: CalcSettings): DayTimes =
        PrayerCalculator.compute(date, place.latitude, place.longitude, place.zone, settings)

    /** Rows on the Today screen. */
    fun todayRows(prefs: Prefs): List<Prayer> {
        val showAI = prefs.showAsrIsha
        val showSunset = prefs.calcSettings().params.maghribDiffersFromSunset
        return buildList {
            add(Prayer.FAJR); add(Prayer.SUNRISE); add(Prayer.DHUHR)
            if (showAI) add(Prayer.ASR)
            if (showSunset) add(Prayer.SUNSET)
            add(Prayer.MAGHRIB)
            if (showAI) add(Prayer.ISHA)
        }
    }

    /** Columns of the 30-day table (at most six, so it stays readable on a phone). */
    fun tableColumns(prefs: Prefs): List<Prayer> {
        val showAI = prefs.showAsrIsha
        val showSunset = !showAI && prefs.calcSettings().params.maghribDiffersFromSunset
        return buildList {
            add(Prayer.FAJR); add(Prayer.SUNRISE); add(Prayer.DHUHR)
            if (showAI) add(Prayer.ASR)
            if (showSunset) add(Prayer.SUNSET)
            add(Prayer.MAGHRIB)
            if (showAI) add(Prayer.ISHA)
        }
    }

    /** Prayers offered in Notifications. */
    fun notifiable(prefs: Prefs): List<Prayer> = buildList {
        add(Prayer.FAJR); add(Prayer.SUNRISE); add(Prayer.DHUHR)
        if (prefs.showAsrIsha) add(Prayer.ASR)
        add(Prayer.MAGHRIB)
        if (prefs.showAsrIsha) add(Prayer.ISHA)
    }

    /** The next of [candidates] strictly after [now], looking up to two days ahead. */
    fun next(
        place: Place,
        now: ZonedDateTime,
        candidates: Collection<Prayer>,
        settings: CalcSettings
    ): Pair<Prayer, ZonedDateTime>? {
        if (candidates.isEmpty()) return null
        val today = now.withZoneSameInstant(place.zone).toLocalDate()
        for (offset in 0L..2L) {
            val day = day(place, today.plusDays(offset), settings)
            val hit = candidates.mapNotNull { p -> day[p]?.let { p to it } }
                .filter { it.second.isAfter(now) }
                .minByOrNull { it.second }
            if (hit != null) return hit
        }
        return null
    }
}
