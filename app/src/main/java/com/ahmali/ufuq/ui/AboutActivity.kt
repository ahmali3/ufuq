package com.ahmali.ufuq.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import com.ahmali.ufuq.BuildConfig
import com.ahmali.ufuq.R

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = subPage(getString(R.string.about_title))

        val head = vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
        head.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(80), dp(80)))
        val name = hbox().apply { gravity = Gravity.CENTER }
        name.addView(text(getString(R.string.app_name), 22f, Palette.INK, Fonts.bold))
        name.addView(text(getString(R.string.app_name_ar), 18f, Palette.MUTED).apply {
            textDirection = android.view.View.TEXT_DIRECTION_RTL
        }, wrapWrap().apply { marginStart = dp(8) })
        head.addView(name, wrapWrap().apply { topMargin = dp(8) })
        head.addView(text(getString(R.string.version, BuildConfig.VERSION_NAME), 13f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
        }, matchWrap().apply { topMargin = dp(2) })
        head.addView(text(getString(R.string.horizon_meaning), 13f, Palette.MUTED).lines().apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(6), dp(24), 0)
        })
        body.addView(head, matchWrap().apply { topMargin = dp(8) })

        body.addView(section(getString(R.string.disclaimer),
            listOf(getString(R.string.disclaimer_p1), getString(R.string.disclaimer_p2),
                getString(R.string.disclaimer_p3), getString(R.string.no_warranty))))
        val privacy = section(getString(R.string.privacy), listOf(getString(R.string.privacy_body)))
        privacy.addView(textButton(getString(R.string.privacy_policy)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_url))))
            } catch (e: Exception) { }
        }.apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; setPadding(0, 0, 0, 0) }, wrapWrap())
        body.addView(privacy)
        val contact = section(getString(R.string.contact), listOf(getString(R.string.contact_body)))
        contact.addView(textButton(getString(R.string.contact_email)) { emailDeveloper() }.apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(0, 0, 0, 0)
            contentDescription = getString(R.string.contact_email_desc, getString(R.string.contact_email))
        }, wrapWrap())
        body.addView(contact)
        body.addView(section(getString(R.string.credits), listOf(getString(R.string.credits_body))))
        body.addView(text(getString(R.string.made_by), 12f, Palette.FAINT).apply { gravity = Gravity.CENTER },
            matchWrap().apply { topMargin = dp(24) })
    }

    /** Opens the user's email app (no internet permission needed: the email app sends it). */
    private fun emailDeveloper() {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + getString(R.string.contact_email)))
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_subject, BuildConfig.VERSION_NAME))
        try {
            startActivity(i)
        } catch (e: Exception) {
            // No email app: copy the address instead.
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("email", getString(R.string.contact_email)))
            android.widget.Toast.makeText(this, getString(R.string.contact_copied), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun section(title: String, paragraphs: List<String>): LinearLayout = card().apply {
        setPadding(dp(16), dp(14), dp(16), dp(14))
        addView(text(title, 14f, Palette.INK, Fonts.bold))
        for (p in paragraphs) addView(text(p, 13f, Palette.BODY).lines(1.45f).top(this@AboutActivity, 6))
        layoutParams = matchWrap().apply { topMargin = dp(12) }
    }
}
