package com.ahmali.ufuq.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Qibla {
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262
    private const val EARTH_RADIUS_KM = 6371.0088

    /** Initial great-circle bearing to the Kaaba, degrees clockwise from true north (0..360). */
    fun bearing(latitude: Double, longitude: Double): Double {
        val p1 = Math.toRadians(latitude)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LON - longitude)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Great-circle distance to the Kaaba in kilometres. */
    fun distanceKm(latitude: Double, longitude: Double): Double {
        val p1 = Math.toRadians(latitude)
        val p2 = Math.toRadians(KAABA_LAT)
        val dp = p2 - p1
        val dl = Math.toRadians(KAABA_LON - longitude)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
    }

    /**
     * Signed turn from the current [heading] to the Qibla [bearing], in degrees:
     * positive = turn right (clockwise), negative = turn left. Range -180..180.
     */
    fun turn(heading: Double, bearing: Double): Double {
        var d = (bearing - heading) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }
}
