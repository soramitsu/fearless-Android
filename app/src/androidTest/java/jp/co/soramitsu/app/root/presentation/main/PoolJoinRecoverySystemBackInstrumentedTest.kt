package jp.co.soramitsu.app.root.presentation.main

import android.view.KeyEvent
import android.widget.TextView
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.PoolJoinRecoveryFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual Material dialog/OS Back using the production recovery Fragment's behavior policy.
 * Installed public-wallet tests separately establish ViewModel revocation and navigation ownership.
 */
@RunWith(AndroidJUnit4::class)
class PoolJoinRecoverySystemBackInstrumentedTest {
    @Test fun systemBackCancelsFixedRecoverySheetAfterInitialAndRecreatedHost() {
        assertSystemBack { PoolJoinRecoveryFragment() }
    }

    @Test fun systemBackCancelsCreateConfirmationOrRecoveryAfterHostRecreation() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.confirm.pool.create.ConfirmCreatePoolFragment() }
    }

    @Test fun systemBackCancelsMemberClaimAfterInitialAndRecreatedHost() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.claim.ConfirmPoolClaimFragment() }
    }

    @Test fun systemBackCancelsMemberRedeemAfterInitialAndRecreatedHost() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.redeem.ConfirmPoolRedeemFragment() }
    }

    @Test fun systemBackCancelsMemberUnbondAfterInitialAndRecreatedHost() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmPoolUnbondFragment() }
    }

    @Test fun systemBackCancelsMemberBondMoreAfterInitialAndRecreatedHost() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm.ConfirmPoolBondMoreFragment() }
    }

    @Test fun systemBackCancelsMemberClaimPreparationAfterHostRecreation() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.claim.PoolClaimFragment() }
    }

    @Test fun systemBackCancelsMemberRedeemPreparationAfterHostRecreation() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.redeem.PoolRedeemFragment() }
    }

    @Test fun systemBackCancelsMemberUnbondPreparationAfterHostRecreation() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.unbond.PoolUnstakeFragment() }
    }

    @Test fun systemBackCancelsMemberBondMorePreparationAfterHostRecreation() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.bond.select.PoolBondMoreFragment() }
    }

    @Test fun systemBackCancelsPoolManagementAfterHostRecreation() {
        assertSystemBack { jp.co.soramitsu.staking.impl.presentation.staking.balance.ManagePoolStakeFragment() }
    }

    private fun assertSystemBack(fragmentFactory: () -> androidx.fragment.app.Fragment) {
        ActivityScenario.launch(PolkaswapNavigationLayoutTestActivity::class.java).use { scenario ->
            for (recreated in listOf(false, true)) {
                if (recreated) scenario.recreate()
                val canceled = CountDownLatch(1)
                val dismissed = CountDownLatch(1)
                val shown = CountDownLatch(1)
                lateinit var dialog: BottomSheetDialog
                scenario.onActivity { activity ->
                    dialog = BottomSheetDialog(activity, R.style.CustomBottomSheetDialogTheme)
                    dialog.setContentView(TextView(activity).apply { text = "Public recovery Back fixture" })
                    dialog.setOnShowListener {
                        val fragment = fragmentFactory()
                        // Invoke the actual override or its inherited baseline policy. No fake policy.
                        val owner = generateSequence(fragment.javaClass as Class<*>?) { it.superclass }
                            .first { type -> type.declaredMethods.any { it.name == "setupBehavior" } }
                        owner.getDeclaredMethod("setupBehavior", BottomSheetBehavior::class.java).apply { isAccessible = true }
                            .invoke(fragment, dialog.behavior)
                        shown.countDown()
                    }
                    dialog.setOnCancelListener { canceled.countDown() }
                    dialog.setOnDismissListener { dismissed.countDown() }
                    dialog.show()
                }
                try {
                    assertTrue("Production sheet policy applied", shown.await(5, TimeUnit.SECONDS))
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    scenario.onActivity { assertFalse("Recovery remains non-draggable", dialog.behavior.isDraggable) }
                    awaitDialogWindowFocus(scenario, dialog)
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    assertTrue("OS Back must cancel recovery; recreated=$recreated", canceled.await(5, TimeUnit.SECONDS))
                    assertTrue("Canceled recovery must dismiss", dismissed.await(5, TimeUnit.SECONDS))
                    assertNotEquals("OS Back must leave the host alive", Lifecycle.State.DESTROYED, scenario.state)
                    scenario.onActivity { assertFalse(dialog.isShowing) }
                } finally {
                    if (scenario.state != Lifecycle.State.DESTROYED) scenario.onActivity { dialog.dismiss() }
                }
            }
        }
    }

    private fun awaitDialogWindowFocus(
        scenario: ActivityScenario<PolkaswapNavigationLayoutTestActivity>,
        dialog: BottomSheetDialog
    ) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var focused = false
        var polls = 0
        while (!focused && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { focused = dialog.isShowing && dialog.window?.decorView?.hasWindowFocus() == true }
            polls++
            if (!focused) SystemClock.sleep(16)
        }
        println("Public Material Back fixture: dialogFocused=$focused, focusPolls=$polls")
        assertTrue("The shown dialog must own window focus before OS Back is injected", focused)
    }
}
