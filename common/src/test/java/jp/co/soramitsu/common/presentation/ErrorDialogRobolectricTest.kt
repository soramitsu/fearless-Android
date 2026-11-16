package jp.co.soramitsu.common.presentation

import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.time.Duration
import jp.co.soramitsu.common.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class ErrorDialogRobolectricTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<FragmentActivity>
    private val activity get() = controller.get()

    @Before
    fun createHost() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        controller = Robolectric.buildActivity(FragmentActivity::class.java)
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar)
        controller.setup().visible()
    }

    @After
    fun closeHost() {
        controller.pause().stop().destroy()
    }

    @Test
    fun shouldDecodeOnlyKnownActionsAndRejectBlankRequestKeys() {
        assertNull(ErrorDialog.resultAction(Bundle()))
        assertNull(ErrorDialog.resultAction(Bundle().apply { putString(ErrorDialog.RESULT_ACTION, "UNKNOWN") }))
        ErrorDialog.Action.entries.forEach { action ->
            assertEquals(action, ErrorDialog.resultAction(Bundle().apply { putString(ErrorDialog.RESULT_ACTION, action.name) }))
        }
        assertThrows(IllegalArgumentException::class.java) { ErrorDialog("Title", "Message", resultRequestKey = " ") }
    }

    @Test
    fun shouldRouteEachVerticalActionAndDismiss() {
        for (label in listOf("Accept", "Export", "Cancel")) {
            val events = mutableListOf<String>()
            val sheet = ErrorDialog(
                title = "Unable to continue", message = "Check the network", positiveButtonText = "Accept",
                secondPositiveButtonText = "Export", negativeButtonText = "Cancel",
                positiveClick = { events += "Accept" }, secondPositiveClick = { events += "Export" }, negativeClick = { events += "Cancel" }
            )
            show(sheet)
            compose.onNodeWithText("Unable to continue").assertIsDisplayed()
            compose.onNodeWithText("Check the network").assertIsDisplayed()
            val behavior = (sheet.requireDialog() as BottomSheetDialog).behavior
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
            assertFalse(behavior.isDraggable)
            assertTrue(behavior.isHideable)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
            compose.onNodeWithText(label).performClick()
            compose.waitForIdle()
            assertEquals(listOf(label), events)
            compose.runOnUiThread { activity.supportFragmentManager.executePendingTransactions() }
            assertFalse(sheet.isAdded)
        }
    }

    @Test
    fun shouldReturnTheActionAndPayloadInsteadOfCallingRuntimeLambdas() {
        var callbackCalls = 0
        var result: Bundle? = null
        val payload = Bundle().apply { putString("wallet", "test-wallet") }
        activity.supportFragmentManager.setFragmentResultListener("decision", activity) { _, data -> result = data }
        val sheet = ErrorDialog(
            title = "Confirm", message = "Choose an action", positiveButtonText = "Continue",
            resultRequestKey = "decision", resultPayload = payload, positiveClick = { callbackCalls++ }
        )
        show(sheet)
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        assertEquals(0, callbackCalls)
        assertEquals(ErrorDialog.Action.POSITIVE, ErrorDialog.resultAction(requireNotNull(result)))
        assertEquals("test-wallet", result!!.getBundle(ErrorDialog.RESULT_PAYLOAD)?.getString("wallet"))
    }

    @Test
    fun shouldKeepNonHideableHorizontalDialogsOpenOnBack() {
        val events = mutableListOf<String>()
        val sheet = ErrorDialog(
            title = null, message = "Request pending", positiveButtonText = "Retry", negativeButtonText = "Abort",
            isHideable = false, buttonsOrientation = LinearLayout.HORIZONTAL,
            onBackClick = { events += "back" }, positiveClick = { events += "retry" }, negativeClick = { events += "abort" }
        )
        show(sheet)
        compose.onNodeWithContentDescription(activity.getString(R.string.common_close)).assertDoesNotExist()
        compose.runOnUiThread { (sheet.requireDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue(sheet.isAdded)
        assertTrue(events.isEmpty())
        assertFalse((sheet.requireDialog() as BottomSheetDialog).behavior.isHideable)
        compose.onNodeWithText("Abort").performClick()
        compose.waitForIdle()
        assertEquals(listOf("abort"), events)
    }

    @Test
    fun shouldDispatchBackAndDismissFromTheCloseControl() {
        var backs = 0
        val sheet = ErrorDialog("Info", "Try later", onBackClick = { backs++ })
        show(sheet)
        compose.onNodeWithContentDescription(activity.getString(R.string.common_close)).performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        compose.runOnUiThread { activity.supportFragmentManager.executePendingTransactions() }
        assertFalse(sheet.isAdded)
    }

    @Test
    fun shouldDismissRestoredDialogsWhoseRuntimeCallbacksWereLost() {
        val original = ErrorDialog("Retry", "Unavailable", positiveButtonText = "Retry", positiveClick = {})
        show(original)
        lateinit var restored: ErrorDialog
        compose.runOnUiThread {
            val manager = activity.supportFragmentManager
            val state = manager.saveFragmentInstanceState(original)
            restored = ErrorDialog().apply {
                arguments = Bundle(original.requireArguments())
                setInitialSavedState(state)
            }
            original.dismissNow()
            restored.show(manager)
            manager.executePendingTransactions()
        }
        compose.waitForIdle()
        compose.runOnUiThread { activity.supportFragmentManager.executePendingTransactions() }
        assertFalse(restored.isAdded)
    }

    @Test
    fun shouldShowInformationalDialogContentAndAllowHiding() {
        val sheet = InfoDialog("Network information", "Balances are refreshing")
        compose.runOnUiThread {
            sheet.show(activity.supportFragmentManager)
            activity.supportFragmentManager.executePendingTransactions()
        }
        compose.onNodeWithText("Network information").assertIsDisplayed()
        compose.onNodeWithText("Balances are refreshing").assertIsDisplayed()
        val behavior = (sheet.requireDialog() as BottomSheetDialog).behavior
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
        assertTrue(behavior.isDraggable)
        assertTrue(behavior.isHideable)
    }

    private fun show(sheet: ErrorDialog) {
        compose.runOnUiThread {
            sheet.show(activity.supportFragmentManager)
            activity.supportFragmentManager.executePendingTransactions()
        }
        compose.waitForIdle()
    }
}
