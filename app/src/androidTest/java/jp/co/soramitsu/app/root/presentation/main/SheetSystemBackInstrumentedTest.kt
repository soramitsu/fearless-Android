package jp.co.soramitsu.app.root.presentation.main

import android.content.DialogInterface
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.navigation.KeepSheetOpenOnBack
import jp.co.soramitsu.common.presentation.ErrorDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Material dialogs and a real Back key, on whichever Android runs this. Back reaches a sheet differently from
 * Android 13: through OnBackInvokedDispatcher, where the last callback registered answers first.
 */
@RunWith(AndroidJUnit4::class)
class SheetSystemBackInstrumentedTest {

    @Test
    fun everySheetLeftToMaterialClosesOnSystemBack() {
        val sheets = listOf<() -> Fragment>(
            { jp.co.soramitsu.account.impl.presentation.nomis_scoring.ScoreDetailsFragment() },
            { jp.co.soramitsu.staking.impl.presentation.pools.SelectPoolFragment() },
            { jp.co.soramitsu.staking.impl.presentation.pools.edit.EditPoolConfirmFragment() },
            { jp.co.soramitsu.staking.impl.presentation.pools.edit.EditPoolFragment() },
            { jp.co.soramitsu.staking.impl.presentation.pools.PoolInfoOptionsFragment() },
            { jp.co.soramitsu.staking.impl.presentation.setup.pool.join.SetupStakingPoolFragment() },
            { jp.co.soramitsu.staking.impl.presentation.setup.pool.StartStakingPoolFragment() },
            { jp.co.soramitsu.staking.impl.presentation.setup.pool.create.CreatePoolSetupFragment() },
            { jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.ConfirmJoinPoolFragment() },
            { jp.co.soramitsu.staking.impl.presentation.common.filters.ValidatorsSettingsFragment() },
            { jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select.compose.SelectCustomValidatorsFragment() },
            { jp.co.soramitsu.staking.impl.presentation.validators.compose.SelectValidatorsFragment() },
            { jp.co.soramitsu.staking.impl.presentation.validators.compose.StartSelectValidatorsFragment() },
            { jp.co.soramitsu.staking.impl.presentation.validators.compose.ConfirmSelectValidatorsFragment() },
            { jp.co.soramitsu.staking.impl.presentation.validators.compose.SelectedValidatorsFragment() },
            { jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward.ClaimRewardsFragment() },
            { jp.co.soramitsu.wallet.impl.presentation.send.confirm.ConfirmSendFragment() },
            // Not on the Compose base class, and they kept Material from hiding them in the same way.
            { jp.co.soramitsu.staking.impl.presentation.validators.details.ValidatorDetailsFragment() },
            { jp.co.soramitsu.app.root.presentation.WebViewerFragment() },
            { jp.co.soramitsu.wallet.impl.presentation.balance.list.ServiceFragment() },
            { jp.co.soramitsu.wallet.impl.presentation.balance.nft.filters.NftFiltersFragment() }
        )
        ActivityScenario.launch(PolkaswapNavigationLayoutTestActivity::class.java).use { scenario ->
            val failures = sheets.mapNotNull { factory ->
                val sheet = factory()
                val name = sheet.javaClass.simpleName
                runCatching { assertProductionBehaviourClosesOnBack(scenario, sheet) }
                    .exceptionOrNull()?.let { "$name: ${it.message}" }
            }
            assertEquals(emptyList<String>(), failures)
        }
    }

    @Test
    fun aSheetOnTheBaseClassClosesOnSystemBack() = withSheet { scenario, sheet ->
        pressBack()

        awaitClosed(scenario, sheet)
        assertEquals(1, sheet.cancels.get())
    }

    @Test
    fun systemBackLeavesASubmittingSheetOpenUntilItIsDone() = withSheet { scenario, sheet ->
        setSubmitting(scenario, sheet, true)

        pressBack()
        settle()
        scenario.onActivity {
            assertTrue(sheet.requireDialog().isShowing)
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.behavior().state)
        }
        assertEquals(0, sheet.cancels.get())

        setSubmitting(scenario, sheet, false)
        pressBack()

        awaitClosed(scenario, sheet)
        assertEquals(1, sheet.cancels.get())
    }

    @Test
    fun aTapOutsideStillLeavesASubmissionThatNeverAnswers() = withSheet { scenario, sheet ->
        setSubmitting(scenario, sheet, true)

        scenario.onActivity {
            sheet.requireDialog().findViewById<View>(com.google.android.material.R.id.touch_outside).performClick()
        }

        awaitClosed(scenario, sheet)
    }

    @Test
    fun aSheetsOwnBackRunsAheadOfMaterial() {
        val ownBacks = AtomicInteger()
        withSheet(configure = { it.ownBack.value = { ownBacks.incrementAndGet() } }) { scenario, sheet ->
            pressBack()
            settle()

            assertEquals(1, ownBacks.get())
            scenario.onActivity {
                assertTrue(sheet.requireDialog().isShowing)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.behavior().state)
            }
            assertEquals(0, sheet.cancels.get())
        }
    }

    @Test
    fun aSubmittingSheetHoldsBackAheadOfItsOwnBack() {
        val ownBacks = AtomicInteger()
        withSheet(configure = { it.ownBack.value = { ownBacks.incrementAndGet() } }) { scenario, sheet ->
            setSubmitting(scenario, sheet, true)

            pressBack()
            settle()
            assertEquals(0, ownBacks.get())

            setSubmitting(scenario, sheet, false)
            pressBack()
            settle()
            assertEquals(1, ownBacks.get())
        }
    }

    @Test
    fun aDialogThatCannotBeHiddenIgnoresSystemBackInsteadOfCollapsing() {
        val backs = AtomicInteger()
        ActivityScenario.launch(PolkaswapNavigationLayoutTestActivity::class.java).use { scenario ->
            lateinit var dialog: ErrorDialog
            scenario.onActivity { activity ->
                dialog = ErrorDialog(
                    title = "Back under test",
                    message = "This dialog needs an answer",
                    isHideable = false,
                    positiveButtonText = "OK",
                    onBackClick = { backs.incrementAndGet() }
                )
                dialog.show(activity.supportFragmentManager, "error")
            }
            awaitExpanded(scenario, dialog)

            pressBack()
            settle()

            assertEquals(0, backs.get())
            scenario.onActivity {
                assertTrue(dialog.requireDialog().isShowing)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, dialog.behavior().state)
            }
        }
    }

    @Test
    fun aDialogThatCanBeHiddenRunsItsBackActionOnSystemBack() {
        val backs = AtomicInteger()
        ActivityScenario.launch(PolkaswapNavigationLayoutTestActivity::class.java).use { scenario ->
            lateinit var dialog: ErrorDialog
            scenario.onActivity { activity ->
                dialog = ErrorDialog(
                    title = "Back under test",
                    message = "This dialog can be left",
                    positiveButtonText = "OK",
                    onBackClick = { backs.incrementAndGet() }
                )
                dialog.show(activity.supportFragmentManager, "error")
            }
            awaitExpanded(scenario, dialog)

            pressBack()

            awaitClosed(scenario, dialog)
            assertEquals(1, backs.get())
        }
    }

    private fun withSheet(
        configure: (BackTestSheet) -> Unit = {},
        block: (ActivityScenario<PolkaswapNavigationLayoutTestActivity>, BackTestSheet) -> Unit
    ) {
        ActivityScenario.launch(PolkaswapNavigationLayoutTestActivity::class.java).use { scenario ->
            lateinit var sheet: BackTestSheet
            scenario.onActivity { activity ->
                sheet = BackTestSheet().also(configure)
                sheet.show(activity.supportFragmentManager, "sheet")
            }
            await("the sheet composes") { sheet.composed.get() }
            awaitExpanded(scenario, sheet)
            block(scenario, sheet)
        }
    }

    private fun setSubmitting(
        scenario: ActivityScenario<PolkaswapNavigationLayoutTestActivity>,
        sheet: BackTestSheet,
        submitting: Boolean
    ) {
        scenario.onActivity { sheet.submitting.value = submitting }
        await("the sheet recomposes") { sheet.composedSubmitting.get() == submitting }
    }

    /** The production `setupBehavior` of [sheet] on a bare Material dialog: its ViewModel needs wallet services. */
    private fun assertProductionBehaviourClosesOnBack(
        scenario: ActivityScenario<PolkaswapNavigationLayoutTestActivity>,
        sheet: Fragment
    ) {
        val shown = CountDownLatch(1)
        val canceled = CountDownLatch(1)
        lateinit var dialog: BottomSheetDialog
        scenario.onActivity { activity ->
            dialog = BottomSheetDialog(activity, R.style.CustomBottomSheetDialogTheme)
            dialog.setContentView(TextView(activity).apply { text = "Back under test" })
            dialog.setOnShowListener {
                val owner = generateSequence<Class<*>>(sheet.javaClass) { it.superclass }
                    .first { type -> type.declaredMethods.any { it.name == "setupBehavior" } }
                owner.getDeclaredMethod("setupBehavior", BottomSheetBehavior::class.java)
                    .apply { isAccessible = true }
                    .invoke(sheet, dialog.behavior)
                shown.countDown()
            }
            dialog.setOnCancelListener { canceled.countDown() }
            dialog.show()
        }
        try {
            assertTrue("Sheet behaviour applied", shown.await(LONG_WAIT_SECONDS, TimeUnit.SECONDS))
            await("the dialog takes window focus") {
                var focused = false
                scenario.onActivity { focused = dialog.isShowing && dialog.window?.decorView?.hasWindowFocus() == true }
                focused
            }
            pressBack()
            assertTrue("System Back must close the sheet", canceled.await(LONG_WAIT_SECONDS, TimeUnit.SECONDS))
        } finally {
            scenario.onActivity { dialog.dismiss() }
        }
    }

    private fun awaitExpanded(
        scenario: ActivityScenario<PolkaswapNavigationLayoutTestActivity>,
        sheet: DialogFragment
    ) {
        await("the sheet opens with window focus") {
            var ready = false
            scenario.onActivity {
                val dialog = sheet.dialog as? BottomSheetDialog
                ready = dialog != null && dialog.isShowing && dialog.window?.decorView?.hasWindowFocus() == true &&
                    dialog.behavior.state == BottomSheetBehavior.STATE_EXPANDED
            }
            ready
        }
    }

    private fun awaitClosed(
        scenario: ActivityScenario<PolkaswapNavigationLayoutTestActivity>,
        sheet: DialogFragment
    ) {
        await("the sheet closes") {
            var closed = false
            scenario.onActivity { closed = sheet.dialog?.isShowing != true }
            closed
        }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /** Long enough for Material's hide or collapse animation to have changed the state, had Back reached it. */
    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(SETTLE_MS)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(LONG_WAIT_SECONDS)
        while (!condition()) {
            assertFalse("Timed out waiting until $what", SystemClock.elapsedRealtime() > deadline)
            SystemClock.sleep(POLL_MS)
        }
    }

    private fun DialogFragment.behavior() = (requireDialog() as BottomSheetDialog).behavior

    private companion object {
        // Emulators on a busy host.
        const val LONG_WAIT_SECONDS = 60L
        const val SETTLE_MS = 1_500L
        const val POLL_MS = 16L
    }
}

/** The real base class and a real dialog, without the wallet services a production sheet's ViewModel needs. */
class BackTestSheet : BaseComposeBottomSheetDialogFragment<BaseViewModel>() {

    override val viewModel = BaseViewModel()

    val submitting = mutableStateOf(false)
    val ownBack = mutableStateOf<(() -> Unit)?>(null)
    val cancels = AtomicInteger()
    val composed = AtomicBoolean(false)
    val composedSubmitting = AtomicBoolean(false)

    override fun onCancel(dialog: DialogInterface) {
        cancels.incrementAndGet()
        super.onCancel(dialog)
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        ownBack.value?.let { BackHandler(onBack = it) }
        val isSubmitting = submitting.value
        KeepSheetOpenOnBack(submitting = isSubmitting)
        Text("Back under test")
        SideEffect {
            composedSubmitting.set(isSubmitting)
            composed.set(true)
        }
    }
}
