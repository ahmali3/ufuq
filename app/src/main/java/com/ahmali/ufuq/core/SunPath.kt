package com.ahmali.ufuq.core

import java.time.Duration
import java.time.ZonedDateTime

/** Where the sun is on the Today screen's arc. */
data class SunPhase(
    /** True between sunrise and sunset. */
    val isDay: Boolean,
    /** 0 at the start of the current day/night arc, 1 at its end. */
    val fraction: Double
)

object SunPath {
    /**
     * @param sunrise today's sunrise, [sunset] today's sunset, [prevSunset] yesterday's sunset,
     * [nextSunrise] tomorrow's sunrise. Null when the sun doesn't rise or set (polar regions).
     */
    fun phase(
        now: ZonedDateTime,
        prevSunset: ZonedDateTime?,
        sunrise: ZonedDateTime?,
        sunset: ZonedDateTime?,
        nextSunrise: ZonedDateTime?
    ): SunPhase? {
        if (sunrise == null || sunset == null) return null
        return when {
            now.isBefore(sunrise) -> prevSunset?.let { SunPhase(false, frac(it, sunrise, now)) }
            now.isBefore(sunset) -> SunPhase(true, frac(sunrise, sunset, now))
            else -> nextSunrise?.let { SunPhase(false, frac(sunset, it, now)) }
        }
    }

    private fun frac(start: ZonedDateTime, end: ZonedDateTime, now: ZonedDateTime): Double {
        val total = Duration.between(start, end).seconds.toDouble()
        if (total <= 0) return 0.0
        val done = Duration.between(start, now).seconds.toDouble()
        return (done / total).coerceIn(0.0, 1.0)
    }
}
