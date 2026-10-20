package com.ahmali.ufuq.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.Prayer
import com.ahmali.ufuq.core.SunPath
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.data.Schedule
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayPage(
    private val activity: Activity,
    private val prefs: Prefs,
    private val onUseLocation: () -> Unit
) {
    val view = ScrollView(activity).apply { isFillViewport = true }
    private val body = activity.vbox().apply { setPadding(activity.dp(24), activity.dp(24), activity.dp(24), activity.dp(24)) }
    private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    init {
        view.addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun refresh() = with(activity) {
        body.removeAllViews()
        val place = prefs.currentPlace()
        val method = methodShort(prefs.method)
        if (place == null) {
            body.addView(placeHeader(null, method, prefs))
            body.addView(needPlaceCard(onUseLocation).top(this, 32))
            return@with
        }

        val settings = prefs.calcSettings()
        val now = ZonedDateTime.now(place.zone)
        val date: LocalDate = now.toLocalDate()
        val day = Schedule.day(place, date, settings)
        val fmt = TimeFmt(this, prefs)

        body.addView(placeHeader(place, getString(R.string.date_method, dateFmt.format(now), method), prefs))

        // Sun arc: today's day arc, or the night arc between sunsets and sunrises.
        val yesterday = Schedule.day(place, date.minusDays(1), settings)
        val tomorrow = Schedule.day(place, date.plusDays(1), settings)
        val sunPhase = SunPath.phase(now, yesterday[Prayer.SUNSET], day[Prayer.SUNRISE], day[Prayer.SUNSET], tomorrow[Prayer.SUNRISE])
        body.addView(SunArcView(this).apply {
            this.phase = sunPhase
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, matchWrap().apply { topMargin = dp(16) })

        val sunrise = if (sunPhase != null && !sunPhase.isDay && now.isAfter(day[Prayer.SUNSET])) tomorrow[Prayer.SUNRISE] else day[Prayer.SUNRISE]
        val sunset = if (sunPhase != null && !sunPhase.isDay && now.isBefore(day[Prayer.SUNRISE])) yesterday[Prayer.SUNSET] else day[Prayer.SUNSET]
        val ends = hbox()
        if (sunrise != null && sunset != null) {
            ends.addView(text(getString(R.string.sunrise_at, fmt.full(sunrise)), 12f, Palette.MUTED), weight1())
            ends.addView(text(getString(R.string.sunset_at, fmt.full(sunset)), 12f, Palette.MUTED).apply { gravity = Gravity.END }, weight1())
        } else {
            ends.addView(text(getString(R.string.no_sunrise), 12f, Palette.MUTED), weight1())
        }
        body.addView(ends, matchWrap().apply { topMargin = dp(2) })

        // Next prayer.
        val rows = Schedule.todayRows(prefs)
        val next = Schedule.next(place, now, rows.filter { it != Prayer.SUNSET }, settings)
        if (next != null) {
            val block = vbox()
            block.addView(text(getString(R.string.next), 13f, Palette.MUTED, Fonts.medium).apply { letterSpacing = 0.06f })
            val line = hbox().apply { gravity = Gravity.BOTTOM }
            line.addView(text(prayerName(next.first), 40f, Palette.INK, Fonts.medium).apply {
                letterSpacing = -0.02f
                maxLines = 1
            }, weight1())
            line.addView(text(fmt.full(next.second), 22f, Palette.INK, Fonts.regular, tabular = true).apply {
                setPadding(0, 0, 0, dp(6))
            })
            block.addView(line)
            block.addView(text(countdown(now, next.second), 16f, Palette.ACCENT, Fonts.medium))
            block.contentDescription = "${getString(R.string.next)} ${prayerName(next.first)} ${fmt.full(next.second)}, ${countdown(now, next.second)}"
            body.addView(block, matchWrap().apply { topMargin = dp(24) })
        }

        // Today's list.
        val list = vbox()
        list.addView(hairline())
        for (p in rows) {
            val t = day[p]
            val isNext = next != null && next.first == p && t != null && t == next.second
            val past = t != null && !t.isAfter(now)
            val color = when {
                isNext -> Palette.ACCENT
                past -> Palette.PAST
                else -> Palette.INK
            }
            val face = if (isNext) Fonts.bold else Fonts.regular
            val row = hbox().apply { minimumHeight = dp(46) }
            row.addView(text(prayerName(p), 16f, color, face), weight1())
            val timeText = t?.let { fmt.full(it) } ?: getString(R.string.time_not_today)
            row.addView(text(timeText, 16f, color, face, tabular = true))
            row.contentDescription = "${prayerName(p)} $timeText"
            list.addView(row, matchWrap())
            list.addView(hairline())
        }
        body.addView(list, matchWrap().apply { topMargin = dp(20) })

        body.addView(text(getString(R.string.timetable_link), 14f, Palette.ACCENT, Fonts.medium).apply {
            minHeight = dp(44)
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(null)
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(activity, MonthActivity::class.java)) }
        }, wrapWrap().apply { topMargin = dp(6) })

        body.addView(text(getString(R.string.disclaimer_short), 11f, Palette.FAINT).lines(1.35f), matchWrap().apply { topMargin = dp(4) })

        maybeReviewCard()
    }

    private fun countdown(now: ZonedDateTime, at: ZonedDateTime): String {
        val secs = Duration.between(now, at).seconds
        if (secs < 60) return activity.getString(R.string.in_less_min)
        val mins = (secs + 59) / 60
        val h = (mins / 60).toInt()
        val m = (mins % 60).toInt()
        return if (h > 0) activity.getString(R.string.in_h_min, h, m) else activity.getString(R.string.in_min, m)
    }

    /** A gentle, one-time rating prompt after a week of use; only if a store can open it. */
    private fun maybeReviewCard() = with(activity) {
        if (prefs.reviewPromptDone) return@with
        if (LocalDate.now().toEpochDay() - prefs.firstOpenDay < 7) return@with
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        if (market.resolveActivity(packageManager) == null) return@with
        val card = card().apply { setPadding(dp(16), dp(14), dp(16), dp(8)) }
        card.addView(text(getString(R.string.review_title), 15f, Palette.INK, Fonts.bold))
        card.addView(text(getString(R.string.review_body), 13f, Palette.BODY).top(this, 4))
        val buttons = hbox()
        buttons.addView(textButton(getString(R.string.review_rate)) {
            prefs.reviewPromptDone = true
            try { startActivity(market) } catch (e: Exception) { }
            refresh()
        })
        buttons.addView(textButton(getString(R.string.review_later)) {
            prefs.reviewPromptDone = true
            refresh()
        })
        card.addView(buttons)
        body.addView(card, matchWrap().apply { topMargin = dp(16) })
    }
}
