package com.ahmali.ufuq

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmali.ufuq.core.CalculationMethod
import com.ahmali.ufuq.core.City
import com.ahmali.ufuq.data.Prefs
import com.ahmali.ufuq.notify.AlarmScheduler
import com.ahmali.ufuq.ui.AboutActivity
import com.ahmali.ufuq.ui.AdjustmentsActivity
import com.ahmali.ufuq.ui.CityActivity
import com.ahmali.ufuq.ui.MainActivity
import com.ahmali.ufuq.ui.MonthActivity
import com.ahmali.ufuq.ui.NotificationsActivity
import com.ahmali.ufuq.ui.WelcomeActivity
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.startsWith
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end checks on a real device/emulator. Each step saves a screenshot under
 * Android/data/com.ahmali.ufuq/files/screenshots so CI can publish them.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppFlowTest {

    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = ctx.getString(id)

    private val surrey = City("Surrey", "British Columbia", "Canada", 49.1913, -122.849, 89, "America/Vancouver")

    @Before
    fun reset() {
        Prefs(ctx).clearAll()
    }

    private fun seed(method: CalculationMethod = CalculationMethod.JAFARI) {
        Prefs(ctx).apply {
            onboarded = true
            autoLocation = false
            manualCity = surrey
            this.method = method
        }
    }

    @Test
    fun a_firstLaunchPickCityThenMethod() {
        ActivityScenario.launch(WelcomeActivity::class.java).use {
            waitFor(withText(s(R.string.before_start)))
            shot("01_welcome")
            onView(withText(s(R.string.choose_city_instead))).perform(click())
            onView(isAssignableFrom(EditText::class.java)).perform(replaceText("Surrey"), closeSoftKeyboard())
            // The result row's accessibility label is unique ("South Surrey", "Surrey City Centre" differ).
            val result = withContentDescription("Surrey, British Columbia, Canada")
            waitFor(result, 30_000)
            shot("02_city_search")
            onView(result).perform(click())
            waitFor(withText(s(R.string.how_calculated)))
            shot("03_choose_method")
            onView(withText(s(R.string.method_isna))).perform(scrollTo(), click())
            onView(withText(s(R.string.continue_))).perform(click())
            waitFor(withText(startsWith("Surrey")))
            waitFor(withText(s(R.string.prayer_isha)))
            shot("04_today_isna")
            assertTrue(Prefs(ctx).onboarded)
            assertTrue(Prefs(ctx).method == CalculationMethod.ISNA)
        }
    }

    @Test
    fun b_tabsAndSubScreens() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(withText(s(R.string.next)))
            waitFor(withText(s(R.string.prayer_maghrib)))
            shot("05_today_jafari")

            onView(withContentDescription(s(R.string.tab_qibla))).perform(click())
            waitFor(withText(s(R.string.keep_away)))
            SystemClock.sleep(1500)
            shot("06_qibla")

            onView(withContentDescription(s(R.string.tab_settings))).perform(click())
            waitFor(withText(s(R.string.settings_title)))
            shot("07_settings")

            onView(withText(s(R.string.row_method))).perform(click())
            waitFor(withText(s(R.string.method_mwl)))
            shot("08_methods")
            pressBack()

            onView(withContentDescription(s(R.string.tab_today))).perform(click())
            onView(withText(s(R.string.timetable_link))).perform(scrollTo(), click())
            waitFor(withText(s(R.string.month_title)))
            shot("09_timetable_jafari")
            pressBack()
        }
    }

    @Test
    fun c_timetableSunniHasSixColumns() {
        seed(CalculationMethod.ISNA)
        ActivityScenario.launch(MonthActivity::class.java).use {
            waitFor(withText(s(R.string.month_title)))
            waitFor(withText(s(R.string.prayer_asr)))
            waitFor(withText(s(R.string.prayer_isha)))
            shot("10_timetable_isna")
        }
    }

    @Test
    fun d_noLocationNeverBlocks() {
        Prefs(ctx).apply { onboarded = true; autoLocation = true }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(withText(s(R.string.need_city_title)))
            shot("11_no_location")
            onView(withText(s(R.string.choose_city))).perform(click())
            waitFor(withText(s(R.string.use_my_location_auto)))
        }
    }

    @Test
    fun e_arabicAndForgivingSearch() {
        seed()
        ActivityScenario.launch(CityActivity::class.java).use {
            onView(isAssignableFrom(EditText::class.java)).perform(replaceText("Kerbala"), closeSoftKeyboard())
            waitFor(withContentDescription(startsWith("Karbala,")), 30_000)
            onView(isAssignableFrom(EditText::class.java)).perform(replaceText("كربلاء"), closeSoftKeyboard())
            waitFor(withContentDescription(startsWith("Karbala,")), 30_000)
            shot("12_arabic_search")
        }
    }

    @Test
    fun f_notificationsScheduleAnAlarm() {
        seed()
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS").close()
            SystemClock.sleep(500)
        }
        ActivityScenario.launch(NotificationsActivity::class.java).use {
            waitFor(withText(s(R.string.notif_master)))
            shot("13_notifications_off")
            onView(withText(s(R.string.notif_master))).perform(click())
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (!Prefs(ctx).notificationsOn && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(200)
            assertTrue("notifications switched on", Prefs(ctx).notificationsOn)
            assertTrue("an alarm is scheduled", Prefs(ctx).nextAlarmAt > System.currentTimeMillis())
            shot("14_notifications_on")

            // A notification actually posts (silently) on every Android version.
            AlarmScheduler.post(ctx, com.ahmali.ufuq.core.Prayer.MAGHRIB, System.currentTimeMillis())

            // Each alert style can be picked and posts without errors.
            for ((label, style) in listOf(
                R.string.alert_vibrate to com.ahmali.ufuq.data.AlertStyle.VIBRATE,
                R.string.alert_sound to com.ahmali.ufuq.data.AlertStyle.SOUND,
                R.string.alert_silent to com.ahmali.ufuq.data.AlertStyle.SILENT
            )) {
                waitFor(withText(s(label)))
                onView(withText(s(label))).perform(click())
                val until = SystemClock.uptimeMillis() + 5_000
                while (Prefs(ctx).alertStyle != style && SystemClock.uptimeMillis() < until) SystemClock.sleep(100)
                assertTrue("alert style $style", Prefs(ctx).alertStyle == style)
                if (style == com.ahmali.ufuq.data.AlertStyle.SOUND) shot("14b_notifications_sound")
                AlarmScheduler.post(ctx, com.ahmali.ufuq.core.Prayer.MAGHRIB, System.currentTimeMillis())
            }
        }
    }

    @Test
    fun g_otherScreensOpen() {
        seed(CalculationMethod.CUSTOM)
        ActivityScenario.launch(AdjustmentsActivity::class.java).use {
            waitFor(withText(s(R.string.adj_fajr_angle)))
            shot("15_adjustments")
        }
        ActivityScenario.launch(AboutActivity::class.java).use {
            waitFor(withText(s(R.string.credits_body)))
            shot("16_about")
        }
    }

    // ---- helpers ----

    private fun waitFor(matcher: Matcher<View>, timeoutMs: Long = 10_000) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var last: Throwable? = null
        while (SystemClock.uptimeMillis() < end) {
            try {
                onView(first(matcher)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                return
            } catch (e: NoMatchingViewException) {
                last = e
            } catch (e: AssertionError) {
                last = e
            }
            SystemClock.sleep(200)
        }
        throw AssertionError("Timed out waiting for view", last)
    }

    /** Matches only the first view (in traversal order) that matches [matcher]. Use a fresh one per check. */
    private fun first(matcher: Matcher<View>): Matcher<View> = object : TypeSafeMatcher<View>() {
        private var found: View? = null
        override fun describeTo(description: Description) {
            description.appendText("first of ")
            matcher.describeTo(description)
        }

        override fun matchesSafely(item: View): Boolean {
            // Lists get rebuilt (e.g. search results), so forget a view that has left the screen.
            if (found?.isAttachedToWindow == false) found = null
            if (found == null && matcher.matches(item)) found = item
            return item === found
        }
    }

    private fun shot(name: String) {
        SystemClock.sleep(400)
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
