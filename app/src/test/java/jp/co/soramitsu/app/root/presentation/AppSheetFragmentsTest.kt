package jp.co.soramitsu.app.root.presentation

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.DialogFragment
import io.mockk.mockk
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Duration
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.R
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/** The alert and web sheets the Navigator opens over any screen. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class AppSheetFragmentsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val controller: NavController = mock(NavController::class.java)
    private val results = mutableMapOf<Int, SavedStateHandle>()
    private val navigator = Navigator(mockk(relaxed = true))
    private val alert = AlertViewState("Network unavailable", "Try again later", "Top up", iconRes = R.drawable.ic_alert_16)
    private lateinit var host: HiltShimHostActivity

    @Before
    fun setUp() {
        HiltShim.reset()
        HiltShim.inject(AlertFragment::class) { it.navigator = navigator }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        host = HiltShim.host()
        listOf(42, 7, 9).forEach { destination ->
            val handle = SavedStateHandle().also { results[destination] = it }
            val entry = mock(NavBackStackEntry::class.java)
            `when`(entry.savedStateHandle).thenReturn(handle)
            `when`(controller.getBackStackEntry(destination)).thenReturn(entry)
        }
        navigator.attach(controller, host)
    }

    @After
    fun tearDown() {
        navigator.detach(host)
        HiltShim.reset()
    }

    private fun result(destination: Int, key: String): Result<*>? = results.getValue(destination).get<Result<*>>(key)

    @Test
    fun theAlertsMainActionReportsSuccessToItsTargetAndCloses() {
        val sheet = show(AlertFragment().apply { arguments = AlertFragment.getBundle(alert, "network", 42) })
        compose.onNodeWithText("Network unavailable").assertExists()
        compose.onNodeWithText("Try again later").assertExists()
        compose.onNodeWithText("Top up").performClick()
        idle()
        assertEquals(Result.success(Unit), result(42, "network"))
        assertFalse(sheet.isAdded)
    }

    @Test
    fun closingOrCancellingTheAlertReportsAFailure() {
        val closed = show(AlertFragment().apply { arguments = AlertFragment.getBundle(alert, "closed", 7) })
        compose.onNodeWithContentDescription(host.getString(R.string.common_close)).performClick()
        idle()
        assertTrue(result(7, "closed")!!.isFailure)
        assertFalse(closed.isAdded)

        val cancelled = show(AlertFragment().apply { arguments = AlertFragment.getBundle(alert, emptyResultKey, 9) })
        cancelled.requireDialog().cancel()
        idle()
        assertTrue(result(9, emptyResultKey)!!.isFailure)
    }

    @Test
    fun theWebSheetShowsItsTitleAndClosesFromTheToolbar() {
        val sheet = show(WebViewerFragment().apply { arguments = WebViewerFragment.getBundle("Terms of use", "https://fearlesswallet.io/terms") })
        compose.onNodeWithText("Terms of use").assertExists()
        assertTrue(sheet.isAdded)
        sheet.dismiss()
        idle()
        assertFalse(sheet.isAdded)
    }

    private fun <T : DialogFragment> show(sheet: T): T {
        sheet.show(host.supportFragmentManager, sheet.javaClass.simpleName)
        host.supportFragmentManager.executePendingTransactions()
        idle()
        compose.waitForIdle()
        return sheet
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
