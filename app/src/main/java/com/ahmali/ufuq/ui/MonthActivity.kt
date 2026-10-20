package com.ahmali.ufuq.ui

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.os.Build
import android.util.TypedValue
import android.widget.TextView
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.Prayer
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.data.Schedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The next 30 days. Up to five columns: left-aligned with am/pm after each time.
 * Six columns (Asr and Isha shown): centred, slightly smaller.
 */
class MonthActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val place = prefs.currentPlace()
        if (place == null) {
            finish()
            return
        }
        val settings = prefs.calcSettings()
        val cols = Schedule.tableColumns(prefs)
        val wide = cols.size >= 6
        val fmt = TimeFmt(this, prefs)
        val dayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val wdFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val city = place.label.substringBefore(",")

        val root = vbox()
        root.addView(backHeader(getString(R.string.month_title), getString(R.string.date_method, city, methodShort(prefs.method))).apply {
            setPaddingRelative(dp(8), dp(8), dp(16), dp(4))
        }, matchWrap())

        val side = if (wide) 4 else 6
        val dateW = dp(if (wide) 46 else 52)
        val gap = dp(if (wide) 3 else 4)
        val timeSize = if (wide) 12f else 13f
        val headSize = if (wide) 11f else 12f
        val suffixSize = if (wide) 9.5f else 10f
        val align = if (wide) Gravity.CENTER else Gravity.START

        fun gridRow(): LinearLayout = hbox().apply { setPaddingRelative(dp(side), 0, dp(side), 0) }

        // Header row
        val header = gridRow().apply { setPaddingRelative(dp(side), dp(16), dp(side), dp(10)) }
        header.addView(text(getString(R.string.col_date), 12f, Palette.MUTED, Fonts.medium), LinearLayout.LayoutParams(dateW, ViewGroup.LayoutParams.WRAP_CONTENT))
        for (p in cols) {
            header.addView(text(prayerName(p), headSize, Palette.MUTED, Fonts.medium).apply {
                gravity = align or Gravity.CENTER_VERTICAL
                maxLines = 1
                shrinkToFit(headSize)
            }, LinearLayout.LayoutParams(0, dp(22), 1f).apply { marginStart = gap })
        }
        val headerWrap = vbox().apply { setPadding(dp(12), 0, dp(12), 0) }
        headerWrap.addView(header, matchWrap())
        headerWrap.addView(hairline(Palette.HAIRLINE_STRONG))
        root.addView(headerWrap, matchWrap())

        val scroll = ScrollView(this)
        val list = vbox().apply { setPadding(dp(12), 0, dp(12), dp(24)) }
        val today = java.time.ZonedDateTime.now(place.zone).toLocalDate()
        for (i in 0 until 30) {
            val date: LocalDate = today.plusDays(i.toLong())
            val day = Schedule.day(place, date, settings)
            val isToday = i == 0
            val color = if (isToday) Palette.ACCENT else Palette.INK
            val subColor = if (isToday) Palette.ACCENT else Palette.MUTED
            val row = gridRow().apply {
                minimumHeight = dp(48)
                if (isToday) background = rounded(Palette.ACCENT_SOFT, dpf(10))
            }
            val dateCol = vbox()
            dateCol.addView(text(dayFmt.format(date), 13f, color, Fonts.bold).apply { maxLines = 1 })
            dateCol.addView(text(wdFmt.format(date), 11f, subColor, Fonts.medium))
            row.addView(dateCol, LinearLayout.LayoutParams(dateW, ViewGroup.LayoutParams.WRAP_CONTENT))
            val spoken = StringBuilder("${dayFmt.format(date)}: ")
            for (p in cols) {
                val t = day[p]
                val s = SpannableStringBuilder()
                if (t == null) {
                    s.append(getString(R.string.time_not_today))
                } else {
                    s.append(fmt.clock(t))
                    val suffix = fmt.suffix(t)
                    if (suffix.isNotEmpty()) {
                        val start = s.length
                        s.append(if (wide) suffix else " $suffix")
                        s.setSpan(RelativeSizeSpan(suffixSize / timeSize), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        s.setSpan(ForegroundColorSpan(subColor), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    spoken.append("${prayerName(p)} ${fmt.full(t)}, ")
                }
                row.addView(text(s, timeSize, color, if (isToday) Fonts.bold else Fonts.regular, tabular = true).apply {
                    gravity = align or Gravity.CENTER_VERTICAL
                    maxLines = 1
                    shrinkToFit(timeSize)
                }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginStart = gap })
            }
            row.contentDescription = spoken.toString().trimEnd(' ', ',')
            list.addView(row, matchWrap())
            if (!isToday) list.addView(hairline())
        }
        list.addView(text(getString(R.string.disclaimer_short), 11f, Palette.FAINT).lines(1.35f), matchWrap().apply { topMargin = dp(14) })
        scroll.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(root)
    }
}

/** On Android 8+, shrink text that doesn't fit its column (large font settings, narrow phones). */
private fun TextView.shrinkToFit(maxSp: Float) {
    if (Build.VERSION.SDK_INT >= 26) {
        setAutoSizeTextTypeUniformWithConfiguration(8, maxSp.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
    }
}
