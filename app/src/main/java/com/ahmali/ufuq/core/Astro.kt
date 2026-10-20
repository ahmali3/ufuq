package com.ahmali.ufuq.core

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Low-precision solar position (U.S. Naval Observatory approximation), as used by the
 * praytimes.org algorithm. Accurate to about a minute for prayer times between 1950 and 2050.
 */
internal object Astro {

    fun rad(d: Double) = Math.toRadians(d)
    fun deg(r: Double) = Math.toDegrees(r)

    fun dsin(d: Double) = sin(rad(d))
    fun dcos(d: Double) = cos(rad(d))
    fun dtan(d: Double) = tan(rad(d))
    fun darcsin(x: Double) = deg(asin(x))
    fun darccos(x: Double) = deg(acos(x))
    fun darctan2(y: Double, x: Double) = deg(atan2(y, x))
    fun darccot(x: Double) = deg(atan(1.0 / x))

    fun fixAngle(a: Double) = fix(a, 360.0)
    fun fixHour(h: Double) = fix(h, 24.0)

    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }

    /** Julian day at 00:00 UT of the given Gregorian date (Meeus). */
    fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    class SunPosition(val declination: Double, val equationOfTime: Double)

    fun sunPosition(jd: Double): SunPosition {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return SunPosition(decl, eqt)
    }
}
