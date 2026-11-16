package jp.co.soramitsu.common.base

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.fw.LocalFwSecureSurface
import jp.co.soramitsu.common.presentation.ErrorDialog
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalMaterialApi::class)
@RunWith(RobolectricTestRunner::class)
class BaseFragmentHostsRobolectricTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<FragmentActivity>
    private val activity get() = controller.get()

    @Before
    fun createHost() {
        controller = Robolectric.buildActivity(FragmentActivity::class.java)
        activity.setTheme(R.style.Theme_Soramitsu_Fearless)
        controller.setup().visible()
        activity.setContentView(FrameLayout(activity).apply { id = 12345 })
    }

    @After fun destroyHost() { controller.pause().stop().destroy() }

    @Test
    fun shouldHostComposeContentAndRouteViewModelEvents() {
        val fragment = ComposePage()
        attach(fragment)
        compose.onNodeWithText("Page secure=false").assertIsDisplayed()
        checkEvents(fragment, fragment.viewModel, true)
    }

    @Test
    fun shouldHostSecureComposeSheetsExpandedWithoutDragging() {
        val fragment = ComposeSheet()
        attach(fragment)
        compose.onNodeWithText("Sheet secure=true").assertIsDisplayed()
        val behavior = (fragment.requireDialog() as BottomSheetDialog).behavior
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
        assertFalse(behavior.isDraggable)
        assertTrue(behavior.isHideable)
        checkEvents(fragment, fragment.viewModel, true)
        assertFalse(fragment.viewModel.isSoftKeyboardOpenFlow.value)
    }

    @Test
    fun shouldInitializeAndSubscribeLegacyPagesAndHandleBack() {
        val fragment = LegacyPage()
        attach(fragment)
        assertTrue(fragment.initialized)
        assertTrue(fragment.subscribed)
        assertEquals("Legacy page", (fragment.requireView() as TextView).text.toString())
        checkEvents(fragment, fragment.viewModel, true)
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, fragment.backs)
    }

    @Test
    fun shouldInitializeLegacySheetsAndRelayTheirMessages() {
        val fragment = LegacySheet()
        attach(fragment)
        assertTrue(fragment.initialized)
        assertTrue(fragment.subscribed)
        val behavior = (fragment.requireDialog() as BottomSheetDialog).behavior
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
        assertFalse(behavior.isDraggable)
        assertTrue(behavior.isHideable)
        checkEvents(fragment, fragment.viewModel, false)
    }

    private fun attach(fragment: Fragment) {
        compose.runOnUiThread {
            if (fragment is DialogFragment) fragment.showNow(activity.supportFragmentManager, "host")
            else activity.supportFragmentManager.beginTransaction().add(12345, fragment).commitNow()
        }
        compose.waitForIdle()
    }

    private fun checkEvents(fragment: Fragment, model: BaseViewModel, stateDialog: Boolean) {
        compose.runOnUiThread { model.showMessage("Saved successfully") }
        assertEquals("Saved successfully", ShadowToast.getTextOfLatestToast())
        for (titled in listOf(false, true)) {
            compose.runOnUiThread {
                if (titled) model.showError("Network error", "Try again") else model.showError("Try again")
                fragment.childFragmentManager.executePendingTransactions()
            }
            compose.onNodeWithText("Try again").assertIsDisplayed()
            val title = if (titled) "Network error" else activity.getString(R.string.common_error_general_title)
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.runOnUiThread { (fragment.childFragmentManager.fragments.single() as ErrorDialog).dismissNow() }
        }
        if (stateDialog) {
            compose.runOnUiThread {
                model.showError(title = "Decision", message = "Confirm this action", positiveButtonText = "Continue", negativeButtonText = "Abort", isHideable = false)
                fragment.childFragmentManager.executePendingTransactions()
            }
            compose.onNodeWithText("Confirm this action").assertIsDisplayed()
            compose.onNodeWithText("Continue").assertIsDisplayed()
            compose.onNodeWithText("Abort").assertIsDisplayed()
            val dialog = fragment.childFragmentManager.fragments.single() as ErrorDialog
            assertFalse((dialog.requireDialog() as BottomSheetDialog).behavior.isHideable)
            compose.runOnUiThread { dialog.dismissNow() }
        }
    }

    class ComposePage : BaseComposeFragment<BaseViewModel>() {
        override val viewModel = BaseViewModel()
        @Composable override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
            Text("Page secure=${LocalFwSecureSurface.current}")
        }
    }

    class ComposeSheet : BaseComposeBottomSheetDialogFragment<BaseViewModel>() {
        override val viewModel = BaseViewModel()
        @Composable override fun Content(padding: PaddingValues) { Text("Sheet secure=${LocalFwSecureSurface.current}") }
    }

    class LegacyPage : BaseFragment<BaseViewModel>() {
        override val viewModel = BaseViewModel()
        var initialized = false
        var subscribed = false
        var backs = 0
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            TextView(requireContext()).apply { text = "Legacy page"; setBackgroundColor(Color.BLACK) }
        override fun initViews() { initialized = true; onBackPressed { backs++ } }
        override fun subscribe(viewModel: BaseViewModel) { subscribed = true }
    }

    class LegacySheet : BaseBottomSheetDialogFragment<BaseViewModel>(0) {
        override val viewModel = BaseViewModel()
        var initialized = false
        var subscribed = false
        override fun provideView(): View = TextView(requireContext()).apply { text = "Legacy sheet" }
        override fun initViews() { initialized = true }
        override fun subscribe(viewModel: BaseViewModel) { subscribed = true }
    }
}
