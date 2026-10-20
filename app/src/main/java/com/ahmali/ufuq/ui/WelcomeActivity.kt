package com.ahmali.ufuq.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.CalculationMethod
import com.ahmali.ufuq.data.Locator
import com.ahmali.ufuq.data.Prefs

/**
 * First launch, two steps: (1) the full disclaimer and location request, (2) choose the
 * calculation method. Location is optional; choosing a city instead never blocks.
 */
class WelcomeActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private var step = 1
    private var chosen = CalculationMethod.JAFARI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        step = savedInstanceState?.getInt("step") ?: 1
        chosen = CalculationMethod.fromId(savedInstanceState?.getString("method") ?: prefs.method.id)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
        outState.putString("method", chosen.id)
    }

    /** Back on step 2 returns to step 1 (predictive back on Android 13+, onBackPressed before). */
    private val backToStep1: Any? = if (Build.VERSION.SDK_INT >= 33) {
        OnBackInvokedCallback { step = 1; render() }
    } else null
    private var backRegistered = false

    private fun updateBackHandling() {
        if (Build.VERSION.SDK_INT < 33) return
        val cb = backToStep1 as OnBackInvokedCallback
        if (step == 2 && !backRegistered) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backRegistered = true
        } else if (step == 1 && backRegistered) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cb)
            backRegistered = false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step == 2) {
            step = 1
            render()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun render() {
        val root = vbox()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = vbox().apply { setPadding(dp(24), dp(24), dp(24), dp(16)) }
        scroll.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        col.addView(progress(step))
        val bottom = vbox().apply { setPadding(dp(24), dp(8), dp(24), dp(20)) }

        if (step == 1) {
            val head = hbox()
            head.addView(ImageView(this).apply {
                setImageResource(R.mipmap.ic_launcher)
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            val names = vbox()
            val title = hbox().apply { gravity = Gravity.BOTTOM }
            title.addView(text(getString(R.string.app_name), 28f, Palette.INK, Fonts.bold))
            title.addView(text(getString(R.string.app_name_ar), 22f, Palette.MUTED), wrapWrap().apply { marginStart = dp(10) })
            names.addView(title)
            names.addView(text(getString(R.string.welcome_tagline), 14f, Palette.MUTED))
            head.addView(names, weight1().apply { marginStart = dp(14) })
            col.addView(head, matchWrap().apply { topMargin = dp(20) })

            col.addView(text(getString(R.string.before_start), 20f, Palette.INK, Fonts.bold).top(this, 28))
            for (p in listOf(R.string.disclaimer_p1, R.string.disclaimer_p2, R.string.disclaimer_p3)) {
                col.addView(text(getString(p), 14f, Palette.BODY).lines(1.5f).top(this, 12))
            }

            val note = hbox().apply {
                background = rounded(Palette.NAV_BG, dpf(16))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                gravity = Gravity.TOP
            }
            note.addView(icon(R.drawable.ic_pin, Palette.ACCENT, 20))
            note.addView(text(getString(R.string.location_note), 13f, Palette.BODY).lines(1.4f), weight1().apply { marginStart = dp(12) })
            bottom.addView(note, matchWrap())
            bottom.addView(primaryButton(getString(R.string.ok_use_location)) { askLocation() }, matchWrap().apply { topMargin = dp(16) })
            bottom.addView(textButton(getString(R.string.choose_city_instead)) {
                startActivityForResult(Intent(this, CityActivity::class.java), REQ_CITY)
            }, matchWrap().apply { topMargin = dp(4) })
        } else {
            col.addView(text(getString(R.string.how_calculated), 24f, Palette.INK, Fonts.bold).lines(1.2f).top(this, 20))
            col.addView(text(getString(R.string.how_calculated_sub), 14f, Palette.MUTED).lines(1.45f).top(this, 8))
            col.addView(hairline().top(this, 16))
            col.addView(methodList(chosen) { chosen = it })
            col.addView(hairline())
            bottom.addView(primaryButton(getString(R.string.continue_)) { finishOnboarding() }, matchWrap())
        }
        root.addView(bottom, matchWrap())
        setScreen(root)
        updateBackHandling()
    }

    private fun progress(current: Int): LinearLayout = hbox().apply {
        contentDescription = getString(R.string.step_of, current, 2)
        for (i in 1..2) {
            addView(android.view.View(this@WelcomeActivity).apply {
                background = rounded(if (i <= current) Palette.ACCENT else Palette.HAIRLINE_STRONG, dpf(2))
            }, LinearLayout.LayoutParams(dp(24), dp(4)).apply { marginEnd = dp(6) })
        }
    }

    private fun askLocation() {
        prefs.autoLocation = true
        if (Locator.hasPermission(this)) {
            toStep2()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Granted or not, carry on: without permission the app asks for a city instead.
        if (requestCode == REQ_LOCATION) toStep2()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CITY && resultCode == RESULT_OK) toStep2()
    }

    private fun toStep2() {
        step = 2
        render()
    }

    private fun finishOnboarding() {
        prefs.selectMethod(chosen)
        prefs.method = chosen
        prefs.onboarded = true
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val REQ_LOCATION = 40
        private const val REQ_CITY = 41
    }
}
