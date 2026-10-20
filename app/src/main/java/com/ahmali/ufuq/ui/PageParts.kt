package com.ahmali.ufuq.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.ahmali.ufuq.R
import com.ahmali.ufuq.data.Locator
import com.ahmali.ufuq.data.Place
import com.ahmali.ufuq.data.Prefs

/** "City, Region, Country" + a second line, with the AUTO/MANUAL chip that opens city choice. */
fun Activity.placeHeader(place: Place?, subtitle: String, prefs: Prefs): LinearLayout = hbox().apply {
    val titles = vbox()
    titles.addView(text(place?.label?.ifBlank { null } ?: getString(R.string.finding_city), 15f, Palette.INK, Fonts.medium).apply {
        maxLines = 2
    })
    titles.addView(text(subtitle, 13f, Palette.MUTED).top(this@placeHeader, 2))
    addView(titles, weight1())
    val chip = text(getString(if (prefs.autoLocation) R.string.loc_auto else R.string.loc_manual), 12f, Palette.ACCENT, Fonts.bold).apply {
        letterSpacing = 0.04f
        gravity = Gravity.CENTER
        minHeight = dp(32)
        minWidth = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
        background = ripple(rounded(Color.TRANSPARENT, dpf(16), Palette.BORDER, dp(1)), rounded(Color.WHITE, dpf(16)))
        contentDescription = getString(R.string.loc_change)
        setOnClickListener { startActivity(Intent(this@placeHeader, CityActivity::class.java)) }
    }
    addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginStart = dp(12) })
}

/** Shown when there's no location yet: never blocks, always offers a city. */
fun Activity.needPlaceCard(onUseLocation: () -> Unit): LinearLayout = card().apply {
    setPadding(dp(18), dp(18), dp(18), dp(12))
    val waiting = Prefs(this@needPlaceCard).autoLocation && Locator.hasPermission(this@needPlaceCard)
    addView(text(getString(R.string.need_city_title), 18f, Palette.INK, Fonts.bold))
    addView(text(getString(if (waiting) R.string.finding_city else R.string.need_city_body), 14f, Palette.BODY).apply {
        setLineSpacing(0f, 1.3f)
    }.top(this@needPlaceCard, 6))
    addView(primaryButton(getString(R.string.choose_city)) {
        startActivity(Intent(this@needPlaceCard, CityActivity::class.java))
    }.top(this@needPlaceCard, 16))
    if (!waiting) addView(textButton(getString(R.string.use_location), onUseLocation).top(this@needPlaceCard, 4))
}

fun TextView.lines(multiplier: Float = 1.4f): TextView = apply { setLineSpacing(0f, multiplier) }
