package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock

class PoolFullUnstakeDepositorAlertFragmentTest : StakingFragmentFixture() {
    @get:Rule val compose = createEmptyComposeRule()
    // Result is a value class: the router mock receives the unboxed value (Unit, or the internal Result.Failure).
    private val outcomes = mutableListOf<Any?>()

    @Before
    fun prepareClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun showAlert(): BottomSheetDialog {
        val router = mockk<StakingRouter>(relaxed = true)
        every { router.setAlertResult(PoolFullUnstakeDepositorAlertFragment.KEY_RESULT, any(), null) } answers {
            outcomes.add(invocation.args[1])
            Unit
        }
        val fragment = PoolFullUnstakeDepositorAlertFragment()
        HiltFragmentHarness.launchDialog(fragment, mockk<ViewModel>(relaxed = true),
            PoolFullUnstakeDepositorAlertFragment.getBundle("25 DOT")) { it.navigator = router }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val dialog = fragment.requireDialog() as BottomSheetDialog
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, dialog.behavior.state)
        assertFalse(dialog.behavior.isDraggable)
        assertTrue(dialog.behavior.isHideable)
        return dialog
    }

    @Test
    fun warningDisplaysTheDepositAndReturnsSuccessForExternalManagement() {
        val dialog = showAlert()
        compose.onNodeWithText("25 DOT", substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.pool_depositor_unstake_polkadot_js_plus_button_text), ignoreCase = true)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Unit, outcomes.single())
        assertFalse(dialog.isShowing)
    }

    @Test
    fun closingTheWarningReturnsFailureAndDismissesTheSheet() {
        val dialog = showAlert()
        compose.onNodeWithText(context.getString(R.string.common_close), ignoreCase = true)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("kotlin.Result\$Failure", outcomes.single()?.javaClass?.name)
        assertFalse(dialog.isShowing)
    }
}
