package jp.co.soramitsu.app.root.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.ErrorDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class BaseFragmentErrorDialogContractTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun positiveButtonInvokesOnlyThePositiveCallback() {
        val calls = CopyOnWriteArrayList<String>()
        withDialog(calls) {
            compose.onNodeWithText("Retry catalogue").performClick()
            compose.waitForIdle()
            assertEquals(listOf("positive"), calls.toList())
        }
    }

    @Test fun negativeButtonInvokesOnlyTheNegativeCallback() {
        val calls = CopyOnWriteArrayList<String>()
        withDialog(calls) {
            compose.onNodeWithText("Cancel operation").performClick()
            compose.waitForIdle()
            assertEquals(listOf("negative"), calls.toList())
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun dialogBackInvokesOnlyTheBackCallback() {
        val calls = CopyOnWriteArrayList<String>()
        withDialog(calls) { scenario ->
            scenario.onActivity { activity ->
                val parent = activity.supportFragmentManager.findFragmentByTag("base-dialog-contract")!!
                val dialog = parent.childFragmentManager.findFragmentByTag(ErrorDialog.TAG) as ErrorDialog
                dialog.requireDialog().onBackPressed()
            }
            compose.waitForIdle()
            assertEquals(listOf("back"), calls.toList())
        }
    }

    @Test fun horizontalButtonRequestRendersButtonsOnTheSameRow() {
        withDialog(CopyOnWriteArrayList(), LinearLayout.HORIZONTAL) {
            val positive = compose.onNodeWithText("Retry catalogue").fetchSemanticsNode().boundsInRoot
            val negative = compose.onNodeWithText("Cancel operation").fetchSemanticsNode().boundsInRoot
            assertEquals(positive.center.y, negative.center.y, 1f)
            assertTrue(
                "Horizontal dialog buttons must not overlap",
                negative.right <= positive.left || positive.right <= negative.left
            )
        }
    }

    private fun withDialog(
        calls: MutableList<String>,
        orientation: Int = LinearLayout.VERTICAL,
        check: (ActivityScenario<ErrorDialogRestorationActivityTestHost>) -> Unit
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<ErrorDialogRestorationActivityTestHost>(
            Intent(context, ErrorDialogRestorationActivityTestHost::class.java)
        ).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = BaseFragmentErrorDialogTestFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.error_dialog_restoration_parent_container, fragment, "base-dialog-contract")
                    .commitNow()
                fragment.viewModel.showError(
                    title = "Catalogue error",
                    message = "The provider is unavailable",
                    positiveButtonText = "Retry catalogue",
                    negativeButtonText = "Cancel operation",
                    buttonsOrientation = orientation,
                    positiveClick = { calls += "positive" },
                    negativeClick = { calls += "negative" },
                    onBackClick = { calls += "back" }
                )
                fragment.childFragmentManager.executePendingTransactions()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            compose.waitUntil(DIALOG_TIMEOUT_MILLIS) {
                compose.onAllNodesWithText("Retry catalogue").fetchSemanticsNodes().size == 1
            }
            check(scenario)
        }
    }
    private companion object {
        const val DIALOG_TIMEOUT_MILLIS = 5_000L
    }
}

class BaseFragmentErrorDialogTestFragment : BaseFragment<BaseViewModel>() {
    override val viewModel = BaseViewModel()
    override fun initViews() = Unit
    override fun subscribe(viewModel: BaseViewModel) = Unit
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(requireContext())
}
