package com.ahmali.ufuq.ui

import android.content.Context
import android.text.format.DateFormat
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.CalculationMethod
import com.ahmali.ufuq.core.MethodParams
import com.ahmali.ufuq.core.Prayer
import com.ahmali.ufuq.core.Twilight
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.data.TimeFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Context.prayerName(p: Prayer): String = getString(
    when (p) {
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.SUNRISE -> R.string.prayer_sunrise
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.SUNSET -> R.string.prayer_sunset
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
        Prayer.MIDNIGHT -> R.string.prayer_midnight
    }
)

fun Context.methodName(m: CalculationMethod): String = getString(
    when (m) {
        CalculationMethod.JAFARI -> R.string.method_jafari
        CalculationMethod.TEHRAN -> R.string.method_tehran
        CalculationMethod.MWL -> R.string.method_mwl
        CalculationMethod.ISNA -> R.string.method_isna
        CalculationMethod.EGYPT -> R.string.method_egypt
        CalculationMethod.UMM_AL_QURA -> R.string.method_makkah
        CalculationMethod.KARACHI -> R.string.method_karachi
        CalculationMethod.KUWAIT -> R.string.method_kuwait
        CalculationMethod.QATAR -> R.string.method_qatar
        CalculationMethod.SINGAPORE -> R.string.method_muis
        CalculationMethod.TURKEY -> R.string.method_diyanet
        CalculationMethod.CUSTOM -> R.string.method_custom
    }
)

fun Context.methodShort(m: CalculationMethod): String = getString(
    when (m) {
        CalculationMethod.JAFARI -> R.string.short_jafari
        CalculationMethod.TEHRAN -> R.string.short_tehran
        CalculationMethod.MWL -> R.string.short_mwl
        CalculationMethod.ISNA -> R.string.short_isna
        CalculationMethod.EGYPT -> R.string.short_egypt
        CalculationMethod.UMM_AL_QURA -> R.string.short_makkah
        CalculationMethod.KARACHI -> R.string.short_karachi
        CalculationMethod.KUWAIT -> R.string.short_kuwait
        CalculationMethod.QATAR -> R.string.short_qatar
        CalculationMethod.SINGAPORE -> R.string.short_muis
        CalculationMethod.TURKEY -> R.string.short_diyanet
        CalculationMethod.CUSTOM -> R.string.short_custom
    }
)

private fun num(d: Double): String =
    if (d == Math.floor(d)) d.toInt().toString() else String.format(Locale.ROOT, "%.1f", d)

/** "Fajr 16° · Maghrib 4° · Isha 14°" */
fun Context.methodParamsText(p: MethodParams): String {
    val parts = mutableListOf(getString(R.string.p_fajr, num(p.fajrAngle)))
    when (val m = p.maghrib) {
        is Twilight.Angle -> parts += getString(R.string.p_maghrib, num(m.degrees))
        is Twilight.Minutes -> if (m.minutes != 0.0) parts += getString(R.string.p_maghrib_min, num(m.minutes))
    }
    when (val i = p.isha) {
        is Twilight.Angle -> parts += getString(R.string.p_isha, num(i.degrees))
        is Twilight.Minutes -> parts += getString(R.string.p_isha_min, num(i.minutes))
    }
    return parts.joinToString(getString(R.string.p_sep))
}

/** Formats times following the user's 12/24-hour choice. */
class TimeFmt(private val context: Context, prefs: Prefs) {
    val is24: Boolean = when (prefs.timeFormat) {
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    private val locale: Locale = Locale.getDefault()
    private val clock12 = DateTimeFormatter.ofPattern("h:mm", locale)
    private val clock24 = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val ampm = DateTimeFormatter.ofPattern("a", locale)

    /** "6:34 PM" or "18:34". */
    fun full(t: ZonedDateTime): String = if (is24) clock24.format(t) else "${clock12.format(t)} ${ampm.format(t)}"

    /** Clock part only: "6:34" or "18:34". */
    fun clock(t: ZonedDateTime): String = if (is24) clock24.format(t) else clock12.format(t)

    /** "am"/"pm" (lower case), or "" in 24-hour mode. */
    fun suffix(t: ZonedDateTime): String = if (is24) "" else ampm.format(t).lowercase(locale)
}
