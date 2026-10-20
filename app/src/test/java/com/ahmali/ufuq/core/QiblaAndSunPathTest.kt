package com.ahmali.ufuq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class QiblaAndSunPathTest {

    /** Qibla bearing within 0.1° of an independent great-circle implementation (adhan-js). */
    @Test
    fun bearingMatchesReference() {
        for (ref in ReferenceData.qibla) {
            if (ref.city == "Makkah") continue // at the Kaaba the direction is undefined
            val ours = Qibla.bearing(ref.lat, ref.lon)
            val diff = abs(Qibla.turn(ref.bearing, ours))
            assertTrue("${ref.city}: expected ${ref.bearing}, got $ours", diff <= 0.1)
        }
    }

    @Test
    fun surreyBearingAndDistance() {
        assertEquals(16.9, Qibla.bearing(49.1913, -122.849), 0.1)
        assertEquals(11978.0, Qibla.distanceKm(49.1913, -122.849), 15.0)
        assertEquals(0.0, Qibla.distanceKm(Qibla.KAABA_LAT, Qibla.KAABA_LON), 0.001)
    }

    @Test
    fun turnDirection() {
        assertEquals(37.0, Qibla.turn(340.0, 17.0), 1e-9) // turn right
        assertEquals(-20.0, Qibla.turn(37.0, 17.0), 1e-9) // turn left
        assertEquals(180.0, abs(Qibla.turn(0.0, 180.0)), 1e-9)
        assertEquals(0.0, Qibla.turn(17.0, 17.0), 1e-9)
    }

    @Test
    fun sunPhaseDayAndNight() {
        val z = ZoneId.of("America/Vancouver")
        fun t(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 10, d, h, m, 0, 0, z)
        val prevSunset = t(18, 18, 16)
        val sunrise = t(19, 7, 38)
        val sunset = t(19, 18, 14)
        val nextSunrise = t(20, 7, 40)

        val evening = SunPath.phase(t(19, 17, 15), prevSunset, sunrise, sunset, nextSunrise)!!
        assertTrue(evening.isDay)
        assertEquals(0.907, evening.fraction, 0.01)

        val night = SunPath.phase(t(19, 19, 0), prevSunset, sunrise, sunset, nextSunrise)!!
        assertFalse(night.isDay)
        assertEquals(0.057, night.fraction, 0.01)

        val beforeDawn = SunPath.phase(t(19, 3, 30), prevSunset, sunrise, sunset, nextSunrise)!!
        assertFalse(beforeDawn.isDay)
        assertTrue(beforeDawn.fraction in 0.6..0.7)

        assertNull(SunPath.phase(t(19, 12, 0), prevSunset, null, null, nextSunrise))
    }
}
