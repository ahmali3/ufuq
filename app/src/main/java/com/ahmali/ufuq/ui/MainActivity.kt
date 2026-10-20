package com.ahmali.ufuq.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ahmali.ufuq.R
import com.ahmali.ufuq.data.Cities
import com.ahmali.ufuq.data.Locator
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.notify.AlarmScheduler
import java.util.Locale

/** Hosts the Today, Qibla and Settings tabs. */
class MainActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var content: FrameLayout
    private lateinit var today: TodayPage
    private lateinit var qibla: QiblaPage
    private lateinit var settings: SettingsPage
    private val tabs = mutableListOf<Tab>()
    private var current = 0
    private var resumed = false
    private var lastFreshFix = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (current == TAB_TODAY) today.refresh()
            // Wake again just after the next minute boundary.
            val ms = System.currentTimeMillis()
            handler.postDelayed(this, 60_000 - ms % 60_000 + 200)
        }
    }

    private class Tab(val root: View, val pill: View, val icon: ImageView, val label: TextView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.onboarded) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        prefs.firstOpenDay // records the first day of use for the review prompt

        today = TodayPage(this, prefs, ::requestLocation)
        qibla = QiblaPage(this, prefs, ::requestLocation)
        settings = SettingsPage(this, prefs)

        val root = vbox()
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val nav = buildNav()
        root.addView(nav, matchWrap())
        setScreen(root, bottomTarget = nav)

        current = savedInstanceState?.getInt("tab") ?: TAB_TODAY
        show(current)
        Cities.warmUpSearch(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    override fun onResume() {
        super.onResume()
        if (!::prefs.isInitialized) return
        resumed = true
        refreshAll()
        if (current == TAB_QIBLA) qibla.onShow()
        handler.post(ticker)
        refreshLocation()
        AlarmScheduler.reschedule(this)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        if (::prefs.isInitialized) qibla.onHide()
        handler.removeCallbacks(ticker)
    }

    private fun refreshAll() {
        today.refresh()
        qibla.refresh()
        settings.refresh()
    }

    // ---- Tabs ----

    private fun buildNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Palette.NAV_BG)
            setPadding(0, dp(8), 0, dp(10))
        }
        val items = listOf(
            Triple(R.drawable.ic_tab_today, R.string.tab_today, TAB_TODAY),
            Triple(R.drawable.ic_tab_qibla, R.string.tab_qibla, TAB_QIBLA),
            Triple(R.drawable.ic_tab_settings, R.string.tab_settings, TAB_SETTINGS)
        )
        for ((iconRes, labelRes, index) in items) {
            val item = vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                background = ripple(null)
                minimumHeight = dp(56)
                contentDescription = getString(labelRes)
                setOnClickListener { show(index) }
            }
            val pill = FrameLayout(this)
            val iconView = icon(iconRes, Palette.MUTED, 22)
            pill.addView(iconView, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            item.addView(pill, LinearLayout.LayoutParams(dp(60), dp(32)))
            val label = text(getString(labelRes), 12f, Palette.MUTED, Fonts.medium).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            item.addView(label, wrapWrap().apply { topMargin = dp(4) })
            nav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            tabs += Tab(item, pill, iconView, label)
        }
        return nav
    }

    private fun show(index: Int) {
        if (current == TAB_QIBLA && index != TAB_QIBLA) qibla.onHide()
        current = index
        content.removeAllViews()
        val page = when (index) {
            TAB_QIBLA -> qibla.view
            TAB_SETTINGS -> settings.view
            else -> today.view
        }
        content.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tabs.forEachIndexed { i, t ->
            val active = i == index
            t.pill.background = if (active) rounded(Palette.ACCENT_TINT, dpf(16)) else null
            t.icon.setColorFilter(if (active) Palette.ACCENT else Palette.MUTED)
            t.label.setTextColor(if (active) Palette.INK else Palette.MUTED)
            t.label.typeface = if (active) Fonts.bold else Fonts.medium
            t.root.isSelected = active
        }
        when (index) {
            TAB_TODAY -> today.refresh()
            TAB_QIBLA -> {
                qibla.refresh()
                if (resumed) qibla.onShow()
            }
            TAB_SETTINGS -> settings.refresh()
        }
    }

    // ---- Location ----

    /** Called from "Use my location": switch to Auto and ask for permission if needed. */
    private fun requestLocation() {
        prefs.autoLocation = true
        if (Locator.hasPermission(this)) {
            refreshLocation(force = true)
            refreshAll()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) refreshLocation(force = true)
            refreshAll()
        }
    }

    private fun refreshLocation(force: Boolean = false) {
        if (!prefs.autoLocation || !Locator.hasPermission(this)) return
        if (prefs.currentPlace() == null) Locator.lastKnown(this)?.let(::applyFix)
        val now = System.currentTimeMillis()
        if (force || now - lastFreshFix > 10 * 60_000) {
            lastFreshFix = now
            Locator.requestFresh(this) { loc ->
                if (loc != null) applyFix(loc) else if (prefs.currentPlace() == null) {
                    Locator.lastKnown(this)?.let(::applyFix) ?: refreshAll()
                }
            }
        }
    }

    private fun applyFix(loc: Location) {
        val lat = loc.latitude
        val lon = loc.longitude
        Cities.get(this) { idx ->
            val label = idx.nearest(lat, lon)?.label
                ?: String.format(Locale.ROOT, "%.2f, %.2f", lat, lon)
            prefs.saveAutoFix(lat, lon, label)
            if (!isFinishing) {
                refreshAll()
                AlarmScheduler.reschedule(this)
            }
        }
    }

    companion object {
        const val TAB_TODAY = 0
        const val TAB_QIBLA = 1
        const val TAB_SETTINGS = 2
        private const val REQ_LOCATION = 10
    }
}
