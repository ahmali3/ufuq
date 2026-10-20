package com.ahmali.ufuq.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/** One day's times. A null entry means the time does not occur (e.g. midnight sun). */
class DayTimes(
    val date: LocalDate,
    val zone: ZoneId,
    private val times: Map<Prayer, ZonedDateTime?>
) {
    operator fun get(prayer: Prayer): ZonedDateTime? = times[prayer]
}

/**
 * Prayer times from the standard solar formulas (praytimes.org algorithm, written from the
 * published formulas). Everything is computed in UTC and converted to the location's zone per
 * instant, so daylight-saving changes are handled exactly.
 */
object PrayerCalculator {

    private val ORDER = listOf(
        Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR,
        Prayer.SUNSET, Prayer.MAGHRIB, Prayer.ISHA, Prayer.MIDNIGHT
    )

    fun compute(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        settings: CalcSettings
    ): DayTimes {
        val minutes = utcMinutes(date, latitude, longitude, settings, includeOffsets = true)
        val utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val map = HashMap<Prayer, ZonedDateTime?>()
        ORDER.forEachIndexed { i, p ->
            val m = minutes[i]
            map[p] = if (m.isNaN()) null else {
                // Round to the nearest minute, like published timetables.
                val instant: Instant = utcMidnight.plusSeconds(m.roundToLong() * 60)
                instant.atZone(zone)
            }
        }
        return DayTimes(date, zone, map)
    }

    /**
     * Minutes after 00:00 UTC on [date], in the order fajr, sunrise, dhuhr, asr, sunset,
     * maghrib, isha, midnight. NaN where a time does not occur. Unrounded.
     */
    internal fun utcMinutes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        settings: CalcSettings,
        includeOffsets: Boolean
    ): DoubleArray {
        val p = settings.params
        val c = Ctx(
            jDate = Astro.julian(date.year, date.monthValue, date.dayOfMonth) - longitude / 360.0,
            lat = latitude
        )

        // Initial estimates (hours), then one refinement pass (as praytimes.org).
        val t0 = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0, 18.0).map { it / 24.0 }
        val riseSet = 0.833 // sea level; see README for why elevation is not applied
        val maghribAngle = (p.maghrib as? Twilight.Angle)?.degrees
        val ishaAngle = (p.isha as? Twilight.Angle)?.degrees

        var fajr = c.sunAngleTime(p.fajrAngle, t0[0], ccw = true)
        var sunrise = c.sunAngleTime(riseSet, t0[1], ccw = true)
        var dhuhr = c.midDay(t0[2])
        var asr = c.asrTime(settings.effectiveAsr.shadowFactor.toDouble(), t0[3])
        var sunset = c.sunAngleTime(riseSet, t0[4], ccw = false)
        var maghrib = if (maghribAngle != null) c.sunAngleTime(maghribAngle, t0[5], ccw = false) else Double.NaN
        var isha = if (ishaAngle != null) c.sunAngleTime(ishaAngle, t0[6], ccw = false) else Double.NaN

        // Convert local apparent hours to UTC hours.
        val shift = -longitude / 15.0
        fajr += shift; sunrise += shift; dhuhr += shift; asr += shift
        sunset += shift; maghrib += shift; isha += shift

        // High latitudes: when twilight never ends, use a portion of the night.
        val night = timeDiff(sunset, sunrise)
        fajr = adjustHighLat(fajr, sunrise, p.fajrAngle, night, ccw = true, settings.highLatitude)
        if (ishaAngle != null) {
            isha = adjustHighLat(isha, sunset, ishaAngle, night, ccw = false, settings.highLatitude)
        }
        if (maghribAngle != null) {
            maghrib = adjustHighLat(maghrib, sunset, maghribAngle, night, ccw = false, settings.highLatitude)
        }

        // Minute-based definitions.
        (p.maghrib as? Twilight.Minutes)?.let { maghrib = sunset + it.minutes / 60.0 }
        (p.isha as? Twilight.Minutes)?.let {
            val mins = if (settings.applyRamadanRule && p.ramadanIshaMinutes != null && isRamadan(date)) {
                p.ramadanIshaMinutes
            } else {
                it.minutes
            }
            isha = maghrib + mins / 60.0
        }

        dhuhr += settings.dhuhrSafetyMinutes / 60.0

        val midnight = when (p.midnight) {
            MidnightMode.JAFARI -> sunset + timeDiff(sunset, fajr + 24.0) / 2.0
            MidnightMode.STANDARD -> sunset + timeDiff(sunset, sunrise + 24.0) / 2.0
        }

        val hours = doubleArrayOf(fajr, sunrise, dhuhr, asr, sunset, maghrib, isha, midnight)
        if (includeOffsets) {
            ORDER.forEachIndexed { i, prayer ->
                val off = (p.methodOffsets[prayer] ?: 0) + (settings.userOffsets[prayer] ?: 0)
                if (off != 0) hours[i] += off / 60.0
            }
        }
        return DoubleArray(hours.size) { hours[it] * 60.0 }
    }

    private class Ctx(val jDate: Double, val lat: Double) {
        fun midDay(t: Double): Double =
            Astro.fixHour(12.0 - Astro.sunPosition(jDate + t).equationOfTime)

        fun sunAngleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = Astro.sunPosition(jDate + t).declination
            val noon = midDay(t)
            val x = (-Astro.dsin(angle) - Astro.dsin(decl) * Astro.dsin(lat)) /
                (Astro.dcos(decl) * Astro.dcos(lat))
            val h = Astro.darccos(x) / 15.0 // NaN when the sun never reaches the angle
            return if (ccw) noon - h else noon + h
        }

        fun asrTime(factor: Double, t: Double): Double {
            val decl = Astro.sunPosition(jDate + t).declination
            val angle = -Astro.darccot(factor + Astro.dtan(abs(lat - decl)))
            return sunAngleTime(angle, t, ccw = false)
        }
    }

    /** Hours from [a] forward to [b], wrapped to 0..24. NaN propagates. */
    private fun timeDiff(a: Double, b: Double) = Astro.fixHour(b - a)

    private fun adjustHighLat(
        time: Double,
        base: Double,
        angle: Double,
        night: Double,
        ccw: Boolean,
        rule: HighLatitudeRule
    ): Double {
        val portion = night * when (rule) {
            HighLatitudeRule.ANGLE_BASED -> angle / 60.0
            HighLatitudeRule.ONE_SEVENTH -> 1.0 / 7.0
            HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
        }
        val diff = if (ccw) timeDiff(time, base) else timeDiff(base, time)
        if (time.isNaN() || diff > portion) {
            return if (ccw) base - portion else base + portion
        }
        return time
    }

    private fun isRamadan(date: LocalDate): Boolean =
        ramadanCheck?.invoke(date) ?: (tabularHijriMonth(date) == 9)

    /**
     * Optional override for the Ramadan check. By default the arithmetic (tabular) Islamic
     * calendar is used, which can differ from Umm al-Qura by a day.
     */
    @Volatile
    var ramadanCheck: ((LocalDate) -> Boolean)? = null

    /** Hijri month (1–12) from the arithmetic Islamic calendar. */
    internal fun tabularHijriMonth(date: LocalDate): Int {
        val jdn = (Astro.julian(date.year, date.monthValue, date.dayOfMonth) + 0.5).toLong()
        var l = jdn - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        return ((24 * l) / 709).toInt()
    }
}
