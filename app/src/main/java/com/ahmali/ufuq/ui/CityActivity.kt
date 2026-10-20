package com.ahmali.ufuq.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.City
import com.ahmali.ufuq.data.Cities
import com.ahmali.ufuq.data.Locator
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.notify.AlarmScheduler

/** Pick a city (manual) or go back to Auto. Search works offline over the bundled list. */
class CityActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var results: LinearLayout
    private lateinit var input: EditText
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var generation = 0
    private var denied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = vbox()
        root.addView(backHeader(getString(R.string.city_title)).apply {
            setPaddingRelative(dp(8), dp(8), dp(16), dp(4))
        }, matchWrap())

        val field = hbox().apply {
            background = rounded(Palette.CARD, dpf(25), Palette.ACCENT, dp(1.5f))
            setPaddingRelative(dp(16), 0, dp(12), 0)
            minimumHeight = dp(50)
        }
        field.addView(icon(R.drawable.ic_search, Palette.MUTED, 20))
        input = EditText(this).apply {
            hint = getString(R.string.city_search)
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.INK)
            textSize = 16f
            background = null
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            contentDescription = getString(R.string.city_search)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = schedule(s?.toString().orEmpty())
            })
        }
        field.addView(input, weight1().apply { marginStart = dp(10) })
        val top = vbox().apply { setPadding(dp(16), dp(8), dp(16), 0) }
        top.addView(field, matchWrap())
        top.addView(text(getString(R.string.city_hint), 12f, Palette.MUTED).apply {
            setPaddingRelative(dp(8), dp(8), dp(8), 0)
        })
        root.addView(top, matchWrap())

        val scroll = ScrollView(this)
        results = vbox().apply { setPadding(dp(16), dp(8), dp(16), dp(24)) }
        scroll.addView(results, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(root)

        Cities.warmUpSearch(this)
        showHome()
    }

    private fun schedule(query: String) {
        pending?.let(handler::removeCallbacks)
        val q = query.trim()
        if (q.isEmpty()) {
            generation++
            showHome()
            return
        }
        val r = Runnable { search(q) }
        pending = r
        handler.postDelayed(r, 150)
    }

    private fun search(q: String) {
        val gen = ++generation
        if (results.childCount == 0 || results.getChildAt(0).tag != "loading") {
            results.removeAllViews()
            results.addView(text(getString(R.string.loading_cities), 14f, Palette.MUTED).apply { tag = "loading" })
        }
        Cities.runInBackground {
            val found = Cities.blocking(this).search(q)
            handler.post { if (gen == generation && !isFinishing) showResults(found) }
        }
    }

    private fun showHome() {
        results.removeAllViews()
        val current = prefs.autoLabel
        val sub = if (prefs.autoLocation && current != null) getString(R.string.currently, current) else null
        results.addView(iconRow(R.drawable.ic_locate, getString(R.string.use_my_location_auto), sub, highlight = true) { useAuto() })
        if (denied) {
            results.addView(text(getString(R.string.location_denied), 13f, Palette.BODY).lines().top(this, 8))
            results.addView(outlineButton(getString(R.string.open_settings)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }.apply { layoutParams = wrapWrap().apply { topMargin = dp(8) } })
        }
        val recent = prefs.recentCities
        if (recent.isNotEmpty()) {
            results.addView(sectionLabel(getString(R.string.recent)).top(this, 20))
            for (c in recent) results.addView(iconRow(R.drawable.ic_recent, c.name, c.label.removePrefix(c.name).removePrefix(", ")) { pick(c) })
        }
    }

    private fun showResults(found: List<City>) {
        results.removeAllViews()
        results.addView(sectionLabel(getString(R.string.results)).top(this, 4))
        if (found.isEmpty()) {
            results.addView(text(getString(R.string.no_results), 14f, Palette.MUTED).top(this, 12))
            return
        }
        for (c in found) {
            val detail = listOf(c.region, c.country).filter { it.isNotBlank() && it != c.name }.distinct().joinToString(", ")
            results.addView(iconRow(R.drawable.ic_pin, c.name, detail) { pick(c) })
        }
    }

    private fun iconRow(iconRes: Int, title: String, subtitle: String?, highlight: Boolean = false, onClick: () -> Unit) =
        hbox().apply {
            minimumHeight = dp(58)
            setPaddingRelative(dp(8), dp(6), dp(8), dp(6))
            isClickable = true
            isFocusable = true
            background = ripple(null, rounded(0xFFFFFFFF.toInt(), dpf(12)))
            val badge = FrameLayout(this@CityActivity).apply {
                background = if (highlight) rounded(Palette.ACCENT_SOFT, dpf(18)) else null
            }
            badge.addView(icon(iconRes, if (highlight) Palette.ACCENT else Palette.PAST, 18), FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            addView(badge, LinearLayout.LayoutParams(dp(36), dp(36)))
            val texts = vbox()
            texts.addView(text(title, 15f, Palette.INK, if (highlight) Fonts.medium else Fonts.regular))
            if (!subtitle.isNullOrBlank()) texts.addView(text(subtitle, 13f, Palette.MUTED))
            addView(texts, weight1().apply { marginStart = dp(12) })
            contentDescription = listOfNotNull(title, subtitle).joinToString(", ")
            setOnClickListener { onClick() }
        }

    private fun pick(c: City) {
        prefs.manualCity = c
        prefs.autoLocation = false
        prefs.addRecent(c)
        AlarmScheduler.reschedule(this)
        setResult(RESULT_OK)
        finish()
    }

    private fun useAuto() {
        if (Locator.hasPermission(this)) {
            prefs.autoLocation = true
            AlarmScheduler.reschedule(this)
            setResult(RESULT_OK)
            finish()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            useAuto()
        } else {
            denied = true
            if (input.text.isNullOrBlank()) showHome()
        }
    }

    companion object {
        private const val REQ = 20
    }
}
