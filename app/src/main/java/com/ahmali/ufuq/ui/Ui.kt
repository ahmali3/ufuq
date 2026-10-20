package com.ahmali.ufuq.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ahmali.ufuq.R

/** The sand palette. */
object Palette {
    const val BG = 0xFFF2EADB.toInt()
    const val CARD = 0xFFF8F3EA.toInt()
    const val NAV_BG = 0xFFEADFCB.toInt()
    const val INK = 0xFF1A1A17.toInt()
    const val MUTED = 0xFF6A5A45.toInt()
    const val PAST = 0xFF8C7C64.toInt()
    const val FAINT = 0xFF6E5E48.toInt()
    const val BODY = 0xFF3D342A.toInt()
    const val HAIRLINE = 0xFFDDCFB6.toInt()
    const val HAIRLINE_STRONG = 0xFFCDBB9C.toInt()
    const val ROW_DIVIDER = 0xFFE6DAC4.toInt()
    const val BORDER = 0xFFD6C7AC.toInt()
    const val ACCENT = 0xFF6B3E1F.toInt()
    const val ACCENT_TINT = 0x226B3E1F
    const val ACCENT_SOFT = 0x1F6B3E1F
    const val PRESSED = 0x1A6B3E1F
    const val OK = 0xFF3E8E5E.toInt()
    const val WARN = 0xFFB7791F.toInt()
}

fun Context.dp(v: Number): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.dpf(v: Number): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics)

object Fonts {
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
}

fun Context.text(
    value: CharSequence = "",
    sizeSp: Float = 15f,
    color: Int = Palette.INK,
    face: Typeface = Fonts.regular,
    tabular: Boolean = false
): TextView = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    typeface = face
    includeFontPadding = true
    if (tabular) fontFeatureSettings = "tnum"
}

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
fun wrapWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
fun weight1() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

fun <T : View> T.top(ctx: Context, margin: Number): T {
    val lp = (layoutParams as? ViewGroup.MarginLayoutParams) ?: matchWrap()
    lp.topMargin = ctx.dp(margin)
    layoutParams = lp
    return this
}

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

/** A ripple for tap feedback, clipped to [content] (or the view bounds). */
fun ripple(content: Drawable?, mask: Drawable? = null): Drawable =
    RippleDrawable(ColorStateList.valueOf(Palette.PRESSED), content, mask ?: ColorDrawable(Color.WHITE))

fun Context.icon(res: Int, tint: Int, sizeDp: Int = 22): ImageView = ImageView(this).apply {
    setImageResource(res)
    setColorFilter(tint)
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

fun Context.sectionLabel(value: String): TextView =
    text(value, 12f, Palette.MUTED, Fonts.bold).apply { letterSpacing = 0.06f }

/** A rounded card that holds rows. */
fun Context.card(): LinearLayout = vbox().apply {
    background = rounded(Palette.CARD, dpf(16))
    clipToOutline = true
}

/** Settings-style row: title on the start side, value and chevron on the end side. */
fun Context.valueRow(
    title: String,
    value: String?,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null
): LinearLayout = hbox().apply {
    minimumHeight = dp(52)
    setPaddingRelative(dp(16), dp(8), dp(12), dp(8))
    addView(text(title, 16f), weight1())
    if (value != null) {
        addView(text(value, 14f, Palette.MUTED).apply {
            gravity = Gravity.END
            maxLines = 2
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(12)
        })
    }
    if (chevron && onClick != null) {
        addView(icon(R.drawable.ic_chevron, Palette.MUTED, 16).apply {
            if (layoutDirection == View.LAYOUT_DIRECTION_RTL) rotation = 180f
        }, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(6) })
    }
    if (onClick != null) {
        isClickable = true
        isFocusable = true
        background = ripple(null)
        setOnClickListener { onClick() }
    }
}

fun Context.rowDivider(): View = View(this).apply {
    setBackgroundColor(Palette.ROW_DIVIDER)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
        marginStart = dp(16)
    }
}

fun Context.hairline(color: Int = Palette.HAIRLINE): View = View(this).apply {
    setBackgroundColor(color)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(1)))
}

/** Brown pill button. */
fun Context.primaryButton(label: String, onClick: () -> Unit): TextView =
    text(label, 16f, Color.WHITE, Fonts.medium).apply {
        gravity = Gravity.CENTER
        minHeight = dp(52)
        setPadding(dp(20), 0, dp(20), 0)
        background = ripple(rounded(Palette.ACCENT, dpf(26)), rounded(Color.WHITE, dpf(26)))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        contentDescription = label
    }

/** Text-only button in the accent colour. */
fun Context.textButton(label: String, onClick: () -> Unit): TextView =
    text(label, 15f, Palette.ACCENT, Fonts.medium).apply {
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
        background = ripple(null)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

/** Outlined pill button. */
fun Context.outlineButton(label: String, onClick: () -> Unit): TextView =
    text(label, 14f, Palette.ACCENT, Fonts.medium).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)
        setPadding(dp(16), 0, dp(16), 0)
        background = ripple(rounded(Color.TRANSPARENT, dpf(22), Palette.ACCENT, dp(1.5f)), rounded(Color.WHITE, dpf(22)))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

/** Screen header with a back arrow, used by sub-pages. */
fun Activity.backHeader(title: String, subtitle: String? = null): LinearLayout = hbox().apply {
    val back = ImageButton(this@backHeader).apply {
        setImageResource(R.drawable.ic_back)
        setColorFilter(Palette.INK)
        background = ripple(null, rounded(Color.WHITE, dpf(22)))
        contentDescription = getString(R.string.back)
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) rotation = 180f
        setOnClickListener { finish() }
    }
    addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
    val titles = vbox()
    titles.addView(text(title, 20f, Palette.INK, Fonts.bold))
    if (subtitle != null) titles.addView(text(subtitle, 13f, Palette.MUTED))
    addView(titles, weight1().apply { marginStart = dp(4) })
}

/**
 * Base for all screens: draws edge to edge (required on Android 15+) and keeps content
 * clear of the status and navigation bars on every version.
 */
abstract class BaseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
    }

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        val w = window
        w.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            w.navigationBarColor = Color.TRANSPARENT
            w.setDecorFitsSystemWindows(false)
            // Go through decorView: Window.insetsController crashes before the decor exists.
            w.decorView.windowInsetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else if (Build.VERSION.SDK_INT >= 26) {
            w.navigationBarColor = Color.TRANSPARENT
            w.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            // Android 6–7 can't draw dark navigation icons, so keep a dark navigation bar there.
            w.navigationBarColor = Palette.INK
            w.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
    }

    /**
     * Sets [root] as the content and pads it for the system bars. [bottomTarget] receives the
     * bottom inset instead of the root (so a bottom bar's colour reaches the screen edge).
     */
    @Suppress("DEPRECATION")
    fun setScreen(root: View, bottomTarget: View? = null) {
        val baseBottom = bottomTarget?.paddingBottom ?: 0
        root.setOnApplyWindowInsetsListener { v, insets ->
            val top = insets.systemWindowInsetTop
            val bottom = insets.systemWindowInsetBottom
            v.setPadding(insets.systemWindowInsetLeft, top, insets.systemWindowInsetRight,
                if (bottomTarget == null) bottom else 0)
            bottomTarget?.setPadding(bottomTarget.paddingLeft, bottomTarget.paddingTop,
                bottomTarget.paddingRight, baseBottom + bottom)
            insets
        }
        root.setBackgroundColor(Palette.BG)
        setContentView(root)
        root.requestApplyInsets()
    }

    /** Standard sub-page: back header plus scrolling body. Returns the body column. */
    fun subPage(title: String, subtitle: String? = null): LinearLayout {
        val root = vbox()
        root.addView(backHeader(title, subtitle).apply {
            setPaddingRelative(dp(8), dp(8), dp(16), dp(4))
        }, matchWrap())
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = vbox().apply { setPadding(dp(16), dp(8), dp(16), dp(24)) }
        scroll.addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(root)
        return body
    }
}
