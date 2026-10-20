package com.ahmali.ufuq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

class PrayerCalculatorTest {

    private val names = listOf("fajr", "sunrise", "dhuhr", "asr", "sunset", "maghrib", "isha", "midnight")

    private fun methodFor(name: String) = when (name) {
        "MWL" -> CalculationMethod.MWL
        "ISNA" -> CalculationMethod.ISNA
        "Egypt" -> CalculationMethod.EGYPT
        "Makkah" -> CalculationMethod.UMM_AL_QURA
        "Karachi" -> CalculationMethod.KARACHI
        "Tehran" -> CalculationMethod.TEHRAN
        "Jafari" -> CalculationMethod.JAFARI
        else -> error("unknown method $name")
    }

    /** Every method, nine cities, solstices and equinoxes: within ±1 minute of praytimes.org. */
    @Test
    fun matchesPraytimesReference() {
        val failures = mutableListOf<String>()
        for (ref in ReferenceData.times) {
            val settings = CalcSettings(
                method = methodFor(ref.method),
                asr = if (ref.asr == "Hanafi") AsrMethod.HANAFI else AsrMethod.STANDARD,
                highLatitude = HighLatitudeRule.ANGLE_BASED,
                dhuhrSafetyMinutes = 0.0,
                applyRamadanRule = false
            )
            val ours = PrayerCalculator.utcMinutes(
                LocalDate.of(ref.y, ref.m, ref.d), ref.lat, ref.lon, settings, includeOffsets = false
            )
            for (i in names.indices) {
                val expected = ref.minutesUtc[i]
                val actual = ours[i]
                val ok = if (expected.isNaN()) actual.isNaN() else !actual.isNaN() && abs(expected - actual) <= 1.0
                if (!ok) {
                    failures += "${ref.method}/${ref.asr} ${ref.city} ${ref.y}-${ref.m}-${ref.d} ${names[i]}: expected $expected, got $actual"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue(ReferenceData.times.size >= 300)
    }

    @Test
    fun surreyJafariMatchesDesign() {
        val day = PrayerCalculator.compute(
            LocalDate.of(2026, 10, 19), 49.1913, -122.849,
            ZoneId.of("America/Vancouver"), CalcSettings()
        )
        assertClock("06:05", day[Prayer.FAJR])
        assertClock("07:38", day[Prayer.SUNRISE])
        assertClock("12:57", day[Prayer.DHUHR])
        assertClock("18:14", day[Prayer.SUNSET])
        assertClock("18:34", day[Prayer.MAGHRIB])
    }

    @Test
    fun surreyIsnaMatchesDesign() {
        val day = PrayerCalculator.compute(
            LocalDate.of(2026, 10, 19), 49.1913, -122.849,
            ZoneId.of("America/Vancouver"), CalcSettings(method = CalculationMethod.ISNA)
        )
        assertClock("06:11", day[Prayer.FAJR])
        assertClock("15:43", day[Prayer.ASR])
        assertClock("18:14", day[Prayer.MAGHRIB])
        assertClock("19:42", day[Prayer.ISHA])
        assertEquals(day[Prayer.SUNSET], day[Prayer.MAGHRIB])
    }

    @Test
    fun daylightSavingTransitionUsesCorrectOffsets() {
        val zone = ZoneId.of("America/Vancouver")
        val before = PrayerCalculator.compute(LocalDate.of(2026, 10, 31), 49.1913, -122.849, zone, CalcSettings())
        val after = PrayerCalculator.compute(LocalDate.of(2026, 11, 1), 49.1913, -122.849, zone, CalcSettings())
        val fajrBefore = before[Prayer.FAJR]!!
        val fajrAfter = after[Prayer.FAJR]!!
        assertEquals(ZoneOffset.ofHours(-7), fajrBefore.offset)
        assertEquals(ZoneOffset.ofHours(-8), fajrAfter.offset)
        // Real (UTC) Fajr moves ~1.5 min later; the clock reading jumps back about an hour.
        val utcShift = Duration.between(fajrBefore.toInstant(), fajrAfter.toInstant()).toMinutes() - 24 * 60
        assertTrue("utc shift $utcShift", utcShift in 0..3)
        val clockShift = fajrAfter.toLocalTime().toSecondOfDay() / 60 - fajrBefore.toLocalTime().toSecondOfDay() / 60
        assertTrue("clock shift $clockShift", clockShift in -60..-57)
        // Every time on the transition day is on the correct date.
        for (p in Prayer.entries.filter { it != Prayer.MIDNIGHT }) {
            assertEquals(LocalDate.of(2026, 11, 1), after[p]!!.toLocalDate())
        }
    }

    @Test
    fun manualCityInAnotherZoneUsesThatZone() {
        val day = PrayerCalculator.compute(
            LocalDate.of(2026, 10, 19), 32.616, 44.0249, ZoneId.of("Asia/Baghdad"), CalcSettings()
        )
        val fajr = day[Prayer.FAJR]!!
        assertEquals(ZoneOffset.ofHours(3), fajr.offset)
        assertTrue(fajr.hour in 4..5)
        assertTrue(day[Prayer.MAGHRIB]!!.hour == 17)
    }

    @Test
    fun polarDayHasNoSunriseButDoesNotCrash() {
        val day = PrayerCalculator.compute(
            LocalDate.of(2026, 6, 21), 69.6492, 18.9553, ZoneId.of("Europe/Oslo"), CalcSettings()
        )
        assertNull(day[Prayer.SUNRISE])
        assertNull(day[Prayer.SUNSET])
        assertNotNull(day[Prayer.DHUHR])
    }

    @Test
    fun highLatitudeSummerFajrStillDefined() {
        // London in June: twilight never fully ends at 18°, so the high-latitude rule supplies Fajr/Isha.
        for (rule in HighLatitudeRule.entries) {
            val day = PrayerCalculator.compute(
                LocalDate.of(2026, 6, 21), 51.5074, -0.1278, ZoneId.of("Europe/London"),
                CalcSettings(method = CalculationMethod.MWL, highLatitude = rule)
            )
            assertNotNull(rule.name, day[Prayer.FAJR])
            assertNotNull(rule.name, day[Prayer.ISHA])
            assertTrue(rule.name, day[Prayer.FAJR]!!.isBefore(day[Prayer.SUNRISE]))
            assertTrue(rule.name, day[Prayer.ISHA]!!.isAfter(day[Prayer.SUNSET]))
        }
    }

    @Test
    fun ummAlQuraIshaIs120MinutesInRamadan() {
        val zone = ZoneId.of("Asia/Riyadh")
        val s = CalcSettings(method = CalculationMethod.UMM_AL_QURA)
        val ramadan = PrayerCalculator.compute(LocalDate.of(2026, 3, 1), 21.4225, 39.8262, zone, s)
        val normal = PrayerCalculator.compute(LocalDate.of(2026, 4, 15), 21.4225, 39.8262, zone, s)
        assertEquals(120, Duration.between(ramadan[Prayer.MAGHRIB], ramadan[Prayer.ISHA]).toMinutes())
        assertEquals(90, Duration.between(normal[Prayer.MAGHRIB], normal[Prayer.ISHA]).toMinutes())
    }

    @Test
    fun tabularHijriFindsRamadan() {
        assertEquals(8, PrayerCalculator.tabularHijriMonth(LocalDate.of(2026, 2, 17)))
        assertEquals(9, PrayerCalculator.tabularHijriMonth(LocalDate.of(2026, 2, 18)))
        assertEquals(9, PrayerCalculator.tabularHijriMonth(LocalDate.of(2026, 3, 19)))
        assertEquals(10, PrayerCalculator.tabularHijriMonth(LocalDate.of(2026, 3, 20)))
    }

    @Test
    fun offsetsApplyOnTopOfMethod() {
        val zone = ZoneId.of("Europe/Istanbul")
        val date = LocalDate.of(2026, 10, 19)
        val base = PrayerCalculator.compute(
            date, 41.0082, 28.9784, zone,
            CalcSettings(method = CalculationMethod.MWL, dhuhrSafetyMinutes = 0.0)
        )
        val turkey = PrayerCalculator.compute(
            date, 41.0082, 28.9784, zone,
            CalcSettings(
                method = CalculationMethod.TURKEY, dhuhrSafetyMinutes = 0.0,
                userOffsets = mapOf(Prayer.FAJR to 3)
            )
        )
        assertEquals(-7, Duration.between(base[Prayer.SUNRISE], turkey[Prayer.SUNRISE]).toMinutes())
        assertEquals(7, Duration.between(base[Prayer.MAGHRIB], turkey[Prayer.MAGHRIB]).toMinutes())
        assertEquals(3, Duration.between(base[Prayer.FAJR], turkey[Prayer.FAJR]).toMinutes())
    }

    @Test
    fun dhuhrHasOneMinuteSafetyMargin() {
        val zone = ZoneId.of("America/Vancouver")
        val date = LocalDate.of(2026, 10, 19)
        val withMargin = PrayerCalculator.utcMinutes(date, 49.19, -122.85, CalcSettings(), true)
        val without = PrayerCalculator.utcMinutes(date, 49.19, -122.85, CalcSettings(dhuhrSafetyMinutes = 0.0), true)
        assertEquals(1.0, withMargin[2] - without[2], 1e-9)
        assertNotNull(zone)
    }

    @Test
    fun jafariLocksAsrToStandard() {
        assertEquals(AsrMethod.STANDARD, CalcSettings(asr = AsrMethod.HANAFI).effectiveAsr)
        assertEquals(AsrMethod.HANAFI, CalcSettings(method = CalculationMethod.ISNA, asr = AsrMethod.HANAFI).effectiveAsr)
    }

    @Test
    fun customUsesItsOwnParameters() {
        val custom = MethodParams(15.0, Twilight.Minutes(20.0), Twilight.Minutes(60.0), MidnightMode.JAFARI)
        val s = CalcSettings(method = CalculationMethod.CUSTOM, customParams = custom)
        val day = PrayerCalculator.compute(
            LocalDate.of(2026, 10, 19), 49.19, -122.85, ZoneId.of("America/Vancouver"), s
        )
        assertEquals(20, Duration.between(day[Prayer.SUNSET], day[Prayer.MAGHRIB]).toMinutes())
        assertEquals(60, Duration.between(day[Prayer.MAGHRIB], day[Prayer.ISHA]).toMinutes())
        assertTrue(custom.maghribDiffersFromSunset)
        assertFalse(CalculationMethod.ISNA.params!!.maghribDiffersFromSunset)
        assertTrue(CalculationMethod.TURKEY.params!!.maghribDiffersFromSunset)
    }

    private fun assertClock(expected: String, actual: java.time.ZonedDateTime?) {
        assertNotNull(actual)
        val (h, m) = expected.split(":").map { it.toInt() }
        val diff = abs(actual!!.hour * 60 + actual.minute - (h * 60 + m))
        assertTrue("expected $expected, got ${actual.toLocalTime()}", diff <= 1)
    }
}
