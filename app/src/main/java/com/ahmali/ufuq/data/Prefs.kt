package com.ahmali.ufuq.data

import android.content.Context
import android.content.SharedPreferences
import com.ahmali.ufuq.core.AsrMethod
import com.ahmali.ufuq.core.CalcSettings
import com.ahmali.ufuq.core.CalculationMethod
import com.ahmali.ufuq.core.City
import com.ahmali.ufuq.core.HighLatitudeRule
import com.ahmali.ufuq.core.MethodParams
import com.ahmali.ufuq.core.MidnightMode
import com.ahmali.ufuq.core.Prayer
import com.ahmali.ufuq.core.Twilight
import java.time.LocalDate
import java.time.ZoneId

/** Where times and the Qibla are calculated for. */
data class Place(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
    val auto: Boolean
)

enum class TimeFormat { SYSTEM, H12, H24 }

/** All saved settings. Stored on the device only. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("ufuq", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    // ---- Calculation ----

    var method: CalculationMethod
        get() = CalculationMethod.fromId(sp.getString("method", null))
        set(v) = sp.edit().putString("method", v.id).apply()

    var asr: AsrMethod
        get() = if (sp.getString("asr", "standard") == "hanafi") AsrMethod.HANAFI else AsrMethod.STANDARD
        set(v) = sp.edit().putString("asr", if (v == AsrMethod.HANAFI) "hanafi" else "standard").apply()

    var highLatitude: HighLatitudeRule
        get() = when (sp.getString("highLat", "angle")) {
            "middle" -> HighLatitudeRule.MIDDLE_OF_NIGHT
            "seventh" -> HighLatitudeRule.ONE_SEVENTH
            else -> HighLatitudeRule.ANGLE_BASED
        }
        set(v) = sp.edit().putString(
            "highLat", when (v) {
                HighLatitudeRule.MIDDLE_OF_NIGHT -> "middle"
                HighLatitudeRule.ONE_SEVENTH -> "seventh"
                HighLatitudeRule.ANGLE_BASED -> "angle"
            }
        ).apply()

    /** User's choice for "Show Asr and Isha times"; null = the method's default. */
    var showAsrIshaOverride: Boolean?
        get() = when (sp.getInt("showAsrIsha", -1)) {
            0 -> false
            1 -> true
            else -> null
        }
        set(v) = sp.edit().putInt("showAsrIsha", if (v == null) -1 else if (v) 1 else 0).apply()

    val showAsrIsha: Boolean
        get() = showAsrIshaOverride ?: !method.joinsPrayers

    var customParams: MethodParams
        get() {
            val base = CalculationMethod.JAFARI.params!!
            if (!sp.contains("c_fajr")) return base
            fun tw(prefix: String): Twilight {
                val v = sp.getFloat("${prefix}_v", 0f).toDouble()
                return if (sp.getString("${prefix}_mode", "angle") == "min") Twilight.Minutes(v) else Twilight.Angle(v)
            }
            return MethodParams(
                fajrAngle = sp.getFloat("c_fajr", 16f).toDouble(),
                maghrib = tw("c_maghrib"),
                isha = tw("c_isha"),
                midnight = if (sp.getString("c_midnight", "jafari") == "standard") MidnightMode.STANDARD else MidnightMode.JAFARI
            )
        }
        set(p) {
            fun put(e: SharedPreferences.Editor, prefix: String, t: Twilight) {
                when (t) {
                    is Twilight.Angle -> e.putString("${prefix}_mode", "angle").putFloat("${prefix}_v", t.degrees.toFloat())
                    is Twilight.Minutes -> e.putString("${prefix}_mode", "min").putFloat("${prefix}_v", t.minutes.toFloat())
                }
            }
            val e = sp.edit().putFloat("c_fajr", p.fajrAngle.toFloat())
                .putString("c_midnight", if (p.midnight == MidnightMode.STANDARD) "standard" else "jafari")
            put(e, "c_maghrib", p.maghrib)
            put(e, "c_isha", p.isha)
            e.apply()
        }

    fun offset(p: Prayer): Int = sp.getInt("off_${p.name}", 0)
    fun setOffset(p: Prayer, minutes: Int) = sp.edit().putInt("off_${p.name}", minutes).apply()
    fun clearOffsets() {
        val e = sp.edit()
        Prayer.entries.forEach { e.remove("off_${it.name}") }
        e.apply()
    }

    val changedOffsets: Int get() = Prayer.entries.count { offset(it) != 0 }

    /** Switch method. Choosing Custom copies the current method's parameters first. */
    fun selectMethod(m: CalculationMethod) {
        if (m == method) return
        if (m == CalculationMethod.CUSTOM) {
            val current = calcSettings().params
            customParams = current.copy(ramadanIshaMinutes = null, methodOffsets = emptyMap())
            showAsrIshaOverride = showAsrIsha
        } else {
            showAsrIshaOverride = null
        }
        method = m
    }

    fun calcSettings(): CalcSettings {
        val m = method
        return CalcSettings(
            method = m,
            customParams = if (m == CalculationMethod.CUSTOM) customParams else null,
            asr = asr,
            highLatitude = highLatitude,
            userOffsets = Prayer.entries.associateWith { offset(it) }.filterValues { it != 0 }
        )
    }

    var timeFormat: TimeFormat
        get() = when (sp.getString("timeFormat", "system")) {
            "12" -> TimeFormat.H12
            "24" -> TimeFormat.H24
            else -> TimeFormat.SYSTEM
        }
        set(v) = sp.edit().putString(
            "timeFormat", when (v) {
                TimeFormat.H12 -> "12"
                TimeFormat.H24 -> "24"
                TimeFormat.SYSTEM -> "system"
            }
        ).apply()

    // ---- Location ----

    var autoLocation: Boolean
        get() = sp.getBoolean("auto", true)
        set(v) = sp.edit().putBoolean("auto", v).apply()

    var manualCity: City?
        get() = decodeCity(sp.getString("manualCity", null))
        set(v) = sp.edit().putString("manualCity", v?.let(::encodeCity)).apply()

    /** Last auto fix: precise coordinates plus the nearest city's label. */
    fun saveAutoFix(latitude: Double, longitude: Double, label: String) {
        sp.edit().putString("autoLat", latitude.toString()).putString("autoLon", longitude.toString())
            .putString("autoLabel", label).putLong("autoAt", System.currentTimeMillis()).apply()
    }

    val autoFixAgeMillis: Long
        get() = System.currentTimeMillis() - sp.getLong("autoAt", 0L)

    val autoLabel: String? get() = sp.getString("autoLabel", null)

    /** The place to calculate for, or null when there's no location yet. */
    fun currentPlace(): Place? {
        if (!autoLocation) {
            val c = manualCity ?: return null
            val zone = runCatching { ZoneId.of(c.zoneId) }.getOrDefault(ZoneId.systemDefault())
            return Place(c.label, c.latitude, c.longitude, zone, auto = false)
        }
        val lat = sp.getString("autoLat", null)?.toDoubleOrNull() ?: return null
        val lon = sp.getString("autoLon", null)?.toDoubleOrNull() ?: return null
        // In Auto mode the phone's own time zone is the truth (it follows you when you travel).
        return Place(sp.getString("autoLabel", "") ?: "", lat, lon, ZoneId.systemDefault(), auto = true)
    }

    fun addRecent(c: City) {
        val list = recentCities.filter { it.label != c.label }.toMutableList()
        list.add(0, c)
        sp.edit().putString("recent", list.take(5).joinToString("\n") { encodeCity(it) }).apply()
    }

    val recentCities: List<City>
        get() = sp.getString("recent", "")!!.split('\n').mapNotNull { decodeCity(it) }

    // ---- Notifications ----

    var notificationsOn: Boolean
        get() = sp.getBoolean("notif", false)
        set(v) = sp.edit().putBoolean("notif", v).apply()

    fun notifyFor(p: Prayer): Boolean =
        sp.getBoolean("notif_${p.name}", p != Prayer.SUNRISE)

    fun setNotifyFor(p: Prayer, on: Boolean) = sp.edit().putBoolean("notif_${p.name}", on).apply()

    var batteryTipDismissed: Boolean
        get() = sp.getBoolean("batteryTip", false)
        set(v) = sp.edit().putBoolean("batteryTip", v).apply()

    /** Epoch millis of the scheduled alarm (0 = none). Used by tests and for diagnostics. */
    var nextAlarmAt: Long
        get() = sp.getLong("nextAlarm", 0L)
        set(v) = sp.edit().putLong("nextAlarm", v).apply()

    var nextAlarmExact: Boolean
        get() = sp.getBoolean("nextAlarmExact", false)
        set(v) = sp.edit().putBoolean("nextAlarmExact", v).apply()

    var nextAlarmPrayer: String?
        get() = sp.getString("nextAlarmPrayer", null)
        set(v) = sp.edit().putString("nextAlarmPrayer", v).apply()

    /** The prayer time (epoch ms) of the last notification shown, so one is never shown twice. */
    var lastPostedAt: Long
        get() = sp.getLong("lastPosted", 0L)
        set(v) = sp.edit().putLong("lastPosted", v).apply()

    // ---- Review prompt ----

    val firstOpenDay: Long
        get() {
            val v = sp.getLong("firstDay", -1L)
            if (v >= 0) return v
            val today = LocalDate.now().toEpochDay()
            sp.edit().putLong("firstDay", today).apply()
            return today
        }

    var reviewPromptDone: Boolean
        get() = sp.getBoolean("reviewDone", false)
        set(v) = sp.edit().putBoolean("reviewDone", v).apply()

    fun clearAll() = sp.edit().clear().commit()

    private fun encodeCity(c: City) = listOf(
        c.name, c.region, c.country, c.latitude.toString(), c.longitude.toString(),
        c.elevation.toString(), c.zoneId
    ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    private fun decodeCity(s: String?): City? {
        if (s.isNullOrBlank()) return null
        val f = s.split('\t')
        if (f.size < 7) return null
        return City(
            f[0], f[1], f[2], f[3].toDoubleOrNull() ?: return null, f[4].toDoubleOrNull() ?: return null,
            f[5].toIntOrNull() ?: 0, f[6]
        )
    }
}
