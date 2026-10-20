package com.ahmali.ufuq.ui

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import com.ahmali.ufuq.R
import com.ahmali.ufuq.core.CalculationMethod
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.notify.AlarmScheduler

/** Radio list of all methods (no grouping or labels by school), Custom last. */
fun Activity.methodList(selected: CalculationMethod, onPick: (CalculationMethod) -> Unit): LinearLayout {
    val list = vbox()
    val buttons = mutableListOf<RadioButton>()
    for (m in CalculationMethod.entries) {
        val row = hbox().apply {
            minimumHeight = dp(56)
            setPaddingRelative(dp(4), dp(6), dp(8), dp(6))
            isClickable = true
            isFocusable = true
            background = ripple(null, rounded(0xFFFFFFFF.toInt(), dpf(12)))
        }
        val radio = RadioButton(this).apply {
            isChecked = m == selected
            isClickable = false
            isFocusable = false
            buttonTintList = android.content.res.ColorStateList.valueOf(Palette.ACCENT)
        }
        buttons += radio
        row.addView(radio, wrapWrap())
        val texts = vbox()
        texts.addView(text(methodName(m), 15f, Palette.INK, Fonts.medium))
        val sub = if (m == CalculationMethod.CUSTOM) getString(R.string.method_custom_desc) else methodParamsText(m.params!!)
        texts.addView(text(sub, 12f, Palette.MUTED).top(this, 1))
        row.addView(texts, weight1().apply { marginStart = dp(6) })
        row.contentDescription = "${methodName(m)}. $sub"
        row.setOnClickListener {
            buttons.forEach { it.isChecked = false }
            radio.isChecked = true
            onPick(m)
        }
        if (m == CalculationMethod.CUSTOM) list.addView(hairline().top(this, 6))
        list.addView(row, matchWrap())
    }
    return list
}

class MethodActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val body = subPage(getString(R.string.method_title))
        body.addView(methodList(prefs.method) { m ->
            prefs.selectMethod(m)
            AlarmScheduler.reschedule(this)
        })
    }
}
