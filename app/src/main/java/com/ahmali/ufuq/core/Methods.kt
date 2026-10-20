package com.ahmali.ufuq.core

enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, SUNSET, MAGHRIB, ISHA, MIDNIGHT }

/** How a twilight time is defined: a sun angle below the horizon, or minutes after a base time. */
sealed class Twilight {
    data class Angle(val degrees: Double) : Twilight()

    /** Maghrib: minutes after sunset. Isha: minutes after Maghrib. */
    data class Minutes(val minutes: Double) : Twilight()
}

enum class MidnightMode {
    /** Halfway between sunset and sunrise. */
    STANDARD,

    /** Halfway between sunset and Fajr. */
    JAFARI
}

enum class AsrMethod(val shadowFactor: Int) { STANDARD(1), HANAFI(2) }

/** Used when twilight never ends (high latitudes in summer). */
enum class HighLatitudeRule { ANGLE_BASED, MIDDLE_OF_NIGHT, ONE_SEVENTH }

data class MethodParams(
    val fajrAngle: Double,
    val maghrib: Twilight,
    val isha: Twilight,
    val midnight: MidnightMode,
    /** Umm al-Qura: Isha is 120 minutes after Maghrib during Ramadan. */
    val ramadanIshaMinutes: Double? = null,
    /** Fixed minute adjustments published by the method itself (e.g. Diyanet). */
    val methodOffsets: Map<Prayer, Int> = emptyMap()
) {
    /** True when Maghrib is something other than the moment of sunset. */
    val maghribDiffersFromSunset: Boolean
        get() = maghrib != Twilight.Minutes(0.0) || (methodOffsets[Prayer.MAGHRIB] ?: 0) != 0
}

/**
 * Calculation methods. Parameters are the commonly published values (praytimes.org and the
 * issuing bodies); verify against each body before release.
 */
enum class CalculationMethod(
    val id: String,
    /** Methods whose followers pray Dhuhr/Asr and Maghrib/Isha together: Asr and Isha are hidden by default. */
    val joinsPrayers: Boolean,
    val params: MethodParams?
) {
    JAFARI(
        "jafari", true,
        MethodParams(16.0, Twilight.Angle(4.0), Twilight.Angle(14.0), MidnightMode.JAFARI)
    ),
    TEHRAN(
        "tehran", true,
        MethodParams(17.7, Twilight.Angle(4.5), Twilight.Angle(14.0), MidnightMode.JAFARI)
    ),
    MWL(
        "mwl", false,
        MethodParams(18.0, Twilight.Minutes(0.0), Twilight.Angle(17.0), MidnightMode.STANDARD)
    ),
    ISNA(
        "isna", false,
        MethodParams(15.0, Twilight.Minutes(0.0), Twilight.Angle(15.0), MidnightMode.STANDARD)
    ),
    EGYPT(
        "egypt", false,
        MethodParams(19.5, Twilight.Minutes(0.0), Twilight.Angle(17.5), MidnightMode.STANDARD)
    ),
    UMM_AL_QURA(
        "makkah", false,
        MethodParams(
            18.5, Twilight.Minutes(0.0), Twilight.Minutes(90.0), MidnightMode.STANDARD,
            ramadanIshaMinutes = 120.0
        )
    ),
    KARACHI(
        "karachi", false,
        MethodParams(18.0, Twilight.Minutes(0.0), Twilight.Angle(18.0), MidnightMode.STANDARD)
    ),
    KUWAIT(
        "kuwait", false,
        MethodParams(18.0, Twilight.Minutes(0.0), Twilight.Angle(17.5), MidnightMode.STANDARD)
    ),
    QATAR(
        "qatar", false,
        MethodParams(18.0, Twilight.Minutes(0.0), Twilight.Minutes(90.0), MidnightMode.STANDARD)
    ),
    SINGAPORE(
        "muis", false,
        MethodParams(20.0, Twilight.Minutes(0.0), Twilight.Angle(18.0), MidnightMode.STANDARD)
    ),
    TURKEY(
        "diyanet", false,
        MethodParams(
            18.0, Twilight.Minutes(0.0), Twilight.Angle(17.0), MidnightMode.STANDARD,
            methodOffsets = mapOf(
                Prayer.SUNRISE to -7, Prayer.DHUHR to 5, Prayer.ASR to 4, Prayer.MAGHRIB to 7
            )
        )
    ),

    /** User-defined; starts as a copy of the method selected before it. */
    CUSTOM("custom", false, null);

    /** Asr is fixed to the standard shadow (Ja'fari practice) for these methods. */
    val asrLocked: Boolean get() = this == JAFARI || this == TEHRAN

    companion object {
        fun fromId(id: String?): CalculationMethod = entries.firstOrNull { it.id == id } ?: JAFARI
    }
}

data class CalcSettings(
    val method: CalculationMethod = CalculationMethod.JAFARI,
    /** Parameters used when [method] is CUSTOM. */
    val customParams: MethodParams? = null,
    val asr: AsrMethod = AsrMethod.STANDARD,
    val highLatitude: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    /** User's per-prayer minute adjustments, applied on top of any method. */
    val userOffsets: Map<Prayer, Int> = emptyMap(),
    /** Dhuhr is a little after solar noon, as a safety margin. */
    val dhuhrSafetyMinutes: Double = 1.0,
    /** Apply Umm al-Qura's Ramadan Isha rule. */
    val applyRamadanRule: Boolean = true
) {
    val params: MethodParams
        get() = method.params ?: customParams ?: CalculationMethod.JAFARI.params!!

    val effectiveAsr: AsrMethod
        get() = if (method.asrLocked) AsrMethod.STANDARD else asr
}
