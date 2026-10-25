package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.presentation.createStartupConnectionAlert
import jp.co.soramitsu.app.root.presentation.createStartupVersionAlert
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupConnectionAlertInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before fun drawHostBeforeOpeningTheDialog() {
        compose.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) }
        compose.waitForIdle()
    }

    @Test fun cacheWarningsCanContinueAndRetryInEveryShippedLocale() {
        for (locale in listOf("en", "ja", "zh", "ru", "pt", "tr", "in", "vi")) {
            for (step in listOf(InitializationStep.AppConfig, InitializationStep.ChainsConfig, InitializationStep.SubstrateTypes)) {
                var retried: InitializationStep? = null
                lateinit var alert: AlertBottomSheet
                lateinit var context: ContextThemeWrapper
                compose.runOnUiThread {
                    context = ContextThemeWrapper(compose.activity, jp.co.soramitsu.app.R.style.Theme_Soramitsu_Fearless)
                    context.applyOverrideConfiguration(Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) })
                    alert = createStartupConnectionAlert(context, step) { retried = it }
                    alert.show()
                }
                settleDialog()
                val message = alert.findViewById<TextView>(R.id.alertMessage)!!
                assertEquals(context.getString(if (step == InitializationStep.AppConfig) R.string.startup_settings_refresh_unavailable else R.string.startup_network_refresh_unavailable), message.text.toString())
                assertTrue(message.text.isNotBlank())
                capture("$locale-${step.name}-continue")
                tap(alert.findViewById<View>(R.id.alertSecondaryButton)!!, "$locale-${step.name}-continue-action")
                compose.runOnUiThread { assertFalse(alert.isShowing); assertNull(retried) }
                compose.runOnUiThread { alert = createStartupConnectionAlert(context, step) { retried = it }; alert.show() }
                settleDialog()
                capture("$locale-${step.name}")
                tap(alert.findViewById<View>(R.id.alertConfirmButton)!!, "$locale-${step.name}-retry-action")
                compose.runOnUiThread { assertFalse(alert.isShowing); assertEquals(step, retried) }
            }
        }
    }

    @Test fun versionGuidanceCanContinueAndOpenUpdateInEveryShippedLocale() {
        for (locale in listOf("en", "ja", "zh", "ru", "pt", "tr", "in", "vi")) {
            lateinit var alert: AlertBottomSheet
            lateinit var context: ContextThemeWrapper
            var updates = 0
            compose.runOnUiThread {
                context = ContextThemeWrapper(compose.activity, jp.co.soramitsu.app.R.style.Theme_Soramitsu_Fearless)
                context.applyOverrideConfiguration(Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) })
                alert = createStartupVersionAlert(context) { updates++ }
                alert.show()
            }
            settleDialog()
            assertEquals(context.getString(R.string.startup_app_update_notice), alert.findViewById<TextView>(R.id.alertMessage)!!.text.toString())
            capture("$locale-version-guidance")
            tap(alert.findViewById<View>(R.id.alertSecondaryButton)!!, "$locale-version-continue-action")
            compose.runOnUiThread { assertFalse(alert.isShowing); assertEquals(0, updates) }
            compose.runOnUiThread { alert = createStartupVersionAlert(context) { updates++ }; alert.show() }
            settleDialog()
            tap(alert.findViewById<View>(R.id.alertConfirmButton)!!, "$locale-version-update-action")
            compose.runOnUiThread { assertFalse(alert.isShowing); assertEquals(1, updates) }
        }
    }

    private fun tap(view: View, evidenceName: String) {
        scrollToAction(view)
        capture(evidenceName)
        val bounds = Rect()
        val screen = IntArray(2)
        compose.runOnUiThread {
            assertTrue("Action is not visible", view.getGlobalVisibleRect(bounds))
            assertEquals("Action is clipped vertically", view.height, bounds.height())
            assertTrue(view.isEnabled)
            view.getLocationOnScreen(screen)
            bounds.set(screen[0], screen[1], screen[0] + view.width, screen[1] + view.height)
        }
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        settleDialog()
    }

    private fun scrollToAction(view: View) {
        repeat(8) {
            var visible = false
            val scrollBounds = Rect()
            compose.runOnUiThread {
                val bounds = Rect()
                visible = view.getGlobalVisibleRect(bounds) && bounds.height() == view.height
                val scroll = view.rootView.findViewById<View>(R.id.alertScroll)!!
                val location = IntArray(2)
                scroll.getLocationOnScreen(location)
                scrollBounds.set(location[0], location[1], location[0] + scroll.width, location[1] + scroll.height)
            }
            if (visible) return
            val downTime = SystemClock.uptimeMillis()
            val x = scrollBounds.exactCenterX()
            val start = scrollBounds.top + scrollBounds.height() * 0.8f
            val end = scrollBounds.top + scrollBounds.height() * 0.3f
            for (point in 0..20) {
                val action = when (point) { 0 -> MotionEvent.ACTION_DOWN; 20 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, start + (end - start) * point / 20, 0)
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
                SystemClock.sleep(15)
            }
            settleDialog()
        }
        fail("Action could not be reached by scrolling")
    }

    private fun settleDialog() {
        instrumentation.waitForIdleSync()
        // Window animations are outside Compose idling. Allow the shown/dismissed sheet to reach
        // its rendered position before measuring coordinates or recording a screenshot.
        SystemClock.sleep(350)
        instrumentation.waitForIdleSync()
    }

    private fun capture(name: String) {
        settleDialog()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
        val dir = compose.activity.getExternalFilesDir("startup-cache-evidence")!!.apply { mkdirs() }
        File(dir, "$name-${(compose.activity.resources.configuration.fontScale * 100).toInt()}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
