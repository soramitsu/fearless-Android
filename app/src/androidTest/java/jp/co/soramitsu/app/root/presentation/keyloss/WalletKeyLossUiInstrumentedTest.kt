package jp.co.soramitsu.app.root.presentation.keyloss

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.storage.encrypt.WalletKeyLossConsent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletKeyLossUiInstrumentedTest {
    @Test fun recoveryStartsWithRetryAndNeverExposesCachedWalletsOrFreshSlotWithoutConsent() {
        ActivityScenario.launch(WalletKeyLossRecoveryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                val labels = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
                assertTrue(labels.contains(activity.getString(R.string.wallet_keyloss_retry)))
                assertFalse(labels.contains(activity.getString(R.string.wallet_keyloss_start)))
                assertFalse(labels.contains(activity.getString(R.string.wallet_keyloss_cached_warning)))
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) // public test fixture capture only
            }
            capture("keyloss-home.png")
        }
    }

    @Test fun startingStorageRequiresExplicitBackupCheckboxAndCancelKeepsStorageUntouched() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("key_alias", Context.MODE_PRIVATE)
        val before = preferences.all.toMap()
        ActivityScenario.launch(WalletKeyLossRecoveryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // UI-only eligibility fixture; no activation is executed by this test.
                val consent = WalletKeyLossConsent::class.java.declaredConstructors.single().newInstance("ui-only", 1, 1L)
                WalletKeyLossRecoveryActivity::class.java.getDeclaredField("consent").apply { isAccessible = true }.set(activity, consent)
                WalletKeyLossRecoveryActivity::class.java.getDeclaredMethod("showHome", Int::class.javaObjectType)
                    .apply { isAccessible = true }.invoke(activity, null)
                val start = descendants(activity.window.decorView).filterIsInstance<Button>()
                    .single { it.text.toString() == activity.getString(R.string.wallet_keyloss_start) }
                start.performClick()
                val dialog = WalletKeyLossRecoveryActivity::class.java.getDeclaredField("activeDialog")
                    .apply { isAccessible = true }.get(activity) as androidx.appcompat.app.AlertDialog
                val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                assertFalse(positive.isEnabled)
                assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            capture("keyloss-consent-top.png")
            scenario.onActivity { activity ->
                val dialog = WalletKeyLossRecoveryActivity::class.java.getDeclaredField("activeDialog")
                    .apply { isAccessible = true }.get(activity) as androidx.appcompat.app.AlertDialog
                val check = descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().single()
                generateSequence(check.parent) { it.parent }.filterIsInstance<ScrollView>().first().apply {
                    isSmoothScrollingEnabled = false
                    fullScroll(View.FOCUS_DOWN)
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = WalletKeyLossRecoveryActivity::class.java.getDeclaredField("activeDialog")
                    .apply { isAccessible = true }.get(activity) as androidx.appcompat.app.AlertDialog
                val check = descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().single()
                assertTrue(check.getGlobalVisibleRect(android.graphics.Rect()))
                check.performClick()
                assertTrue(dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled)
            }
            capture("keyloss-consent.png")
            scenario.onActivity { activity ->
                val dialog = WalletKeyLossRecoveryActivity::class.java.getDeclaredField("activeDialog")
                    .apply { isAccessible = true }.get(activity) as androidx.appcompat.app.AlertDialog
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertEquals(before, preferences.all)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        val image = instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
        instrumentation.targetContext.openFileOutput(name, Context.MODE_PRIVATE).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
