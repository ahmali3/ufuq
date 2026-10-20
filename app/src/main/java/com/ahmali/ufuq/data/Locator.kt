package com.ahmali.ufuq.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper

/**
 * Coarse location from the platform LocationManager only (no Google Play Services), so it
 * works on phones without them. Never blocks the app: callers fall back to a chosen city.
 */
object Locator {

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasFine(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Providers we may use. Before Android 12 the GPS provider needs fine location. */
    private fun providers(ctx: Context, lm: LocationManager): List<String> {
        val all = try { lm.allProviders } catch (e: Exception) { emptyList<String>() }
        val wanted = mutableListOf(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= 31 || hasFine(ctx)) wanted += LocationManager.GPS_PROVIDER
        if (Build.VERSION.SDK_INT >= 31) wanted += LocationManager.FUSED_PROVIDER
        return wanted.filter { it in all }
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val candidates = providers(ctx, lm) + LocationManager.PASSIVE_PROVIDER
        return candidates.mapNotNull {
            try { lm.getLastKnownLocation(it) } catch (e: Exception) { null }
        }.maxByOrNull { it.time }
    }

    /** Asks for one fresh fix; calls back with null on timeout or if nothing is available. */
    @SuppressLint("MissingPermission")
    fun requestFresh(ctx: Context, timeoutMs: Long = 20_000, callback: (Location?) -> Unit) {
        if (!hasPermission(ctx)) { callback(null); return }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { callback(null); return }
        val enabled = providers(ctx, lm).filter {
            try { lm.isProviderEnabled(it) } catch (e: Exception) { false }
        }
        if (enabled.isEmpty()) { callback(null); return }

        val main = Handler(Looper.getMainLooper())
        var done = false
        val signals = mutableListOf<CancellationSignal>()
        val listeners = mutableListOf<LocationListener>()

        fun finish(loc: Location?) {
            if (done) return
            done = true
            signals.forEach { it.cancel() }
            listeners.forEach { l -> try { lm.removeUpdates(l) } catch (e: Exception) { } }
            callback(loc)
        }
        main.postDelayed({ finish(null) }, timeoutMs)

        for (p in enabled) {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    signals += signal
                    lm.getCurrentLocation(p, signal, ctx.mainExecutor) { loc -> if (loc != null) finish(loc) }
                } else {
                    val l = object : LocationListener {
                        override fun onLocationChanged(location: Location) = finish(location)
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    listeners += l
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(p, l, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                // Provider unavailable or permission revoked; others may still answer.
            }
        }
    }
}
