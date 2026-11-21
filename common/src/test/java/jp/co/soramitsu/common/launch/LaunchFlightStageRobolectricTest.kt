package jp.co.soramitsu.common.launch

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LaunchFlightStageRobolectricTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun clearStage() = LaunchFlightStage.resetForTest()

    @After
    fun resetStage() = LaunchFlightStage.resetForTest()

    @Test
    fun shouldApplyTierValuesClampLookupsAndRestoreRestingState() {
        LaunchFlightStage.begin(80f, 90f, { 0.5f + it * 0.1f }, { it / 5f })
        assertTrue(LaunchFlightStage.active)
        assertEquals(0.5f, LaunchFlightStage.scaleOf(-1), 0f)
        assertEquals(1f, LaunchFlightStage.scaleOf(100), 0f)
        assertEquals(0.4f, LaunchFlightStage.alphaOf(2), 0f)
        assertEquals(80f, LaunchFlightStage.focusX(), 0f)
        assertEquals(90f, LaunchFlightStage.focusY(), 0f)
        LaunchFlightStage.apply({ 0.8f }, { 0.6f })
        assertEquals(0.8f, LaunchFlightStage.scaleOf(3), 0f)
        LaunchFlightStage.end()
        assertFalse(LaunchFlightStage.active)
        assertEquals(1f, LaunchFlightStage.scaleOf(3), 0f)
        assertEquals(1f, LaunchFlightStage.alphaOf(3), 0f)
    }

    @Test
    fun shouldTransformOnlyTheDrivenViewGroupAndCleanUpDetachedViews() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val parent = FrameLayout(activity)
            val wallet = View(activity)
            val pin = View(activity)
            LaunchFlightStage.registerView(wallet, 2, LaunchFlightStage.MAIN_GROUP)
            LaunchFlightStage.registerView(pin, 1, "pin")
            parent.addView(wallet, FrameLayout.LayoutParams(100, 60))
            parent.addView(pin, FrameLayout.LayoutParams(100, 60))
            activity.setContentView(parent)
            parent.layout(0, 0, 200, 200)
            assertEquals(2, LaunchFlightStage.declared)
            LaunchFlightStage.registerView(wallet, 2, LaunchFlightStage.MAIN_GROUP)
            assertEquals(2, LaunchFlightStage.declared)
            LaunchFlightStage.begin(50f, 40f, { 0.7f }, { 0.4f }, LaunchFlightStage.MAIN_GROUP)
            assertEquals(0.7f, wallet.scaleX, 0f)
            assertEquals(0.4f, wallet.alpha, 0f)
            assertEquals(1f, pin.scaleX, 0f)
            val later = View(activity)
            LaunchFlightStage.registerView(later, 5, LaunchFlightStage.MAIN_GROUP)
            parent.addView(later)
            assertEquals(0.7f, later.scaleY, 0f)
            parent.removeView(wallet)
            assertEquals(1f, wallet.alpha, 0f)
            assertEquals(2, LaunchFlightStage.declared)
            LaunchFlightStage.end()
            assertEquals(1f, later.scaleX, 0f)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun shouldRegisterAndDisposeComposeTiersWithoutLosingTheirContent() {
        var shown by mutableStateOf(true)
        compose.setContent {
            FearlessAppTheme {
                if (shown) Box(Modifier.size(100.dp).launchTier(2)) { Text("Wallet") }
            }
        }
        compose.onNodeWithText("Wallet").assertIsDisplayed()
        assertEquals(1, LaunchFlightStage.composedTiers)
        compose.runOnIdle { LaunchFlightStage.begin(50f, 50f, { 0.8f }, { 0.5f }) }
        compose.onNodeWithText("Wallet").assertIsDisplayed()
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        assertEquals(0, LaunchFlightStage.composedTiers)
        assertEquals(0, LaunchFlightStage.declared)
    }
}
