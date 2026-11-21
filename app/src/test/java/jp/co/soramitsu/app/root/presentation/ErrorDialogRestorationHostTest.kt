package jp.co.soramitsu.app.root.presentation

import android.os.Bundle
import android.os.Looper
import android.os.Parcelable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import java.time.Duration
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogContract
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/** Transfer warnings survive recreation of their screen and report each choice once (debug restoration host). */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class ErrorDialogRestorationHostTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val results get() = ErrorDialogRestorationTestController.results
    private lateinit var host: ActivityController<ErrorDialogRestorationActivityTestHost>

    @Before
    fun setUp() {
        ErrorDialogRestorationTestController.reset()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        host = Robolectric.buildActivity(ErrorDialogRestorationActivityTestHost::class.java).setup()
    }

    @After
    fun tearDown() {
        if (!host.get().isDestroyed) host.pause().stop().destroy()
        ErrorDialogRestorationTestController.reset()
    }

    @Test
    fun aRestoredWarningCarriesItsPayloadAndReportsThePositiveChoiceOnce() {
        val payload = TransferValidationResult.ExistentialDepositWarning("1 DOT")
        assertTrue(host.get().showRestorableDialog(TransferValidationDialogContract.CONFIRM_SEND, payload))
        idle()
        assertEquals(1, host.get().activeErrorDialogCount())

        host.recreate()
        idle()
        assertEquals(1, ErrorDialogRestorationTestController.restoredActivityCount.get())
        val restored = host.get().currentDialog()
        assertNotNull(restored)
        val envelope = BundleCompat.getParcelable(
            restored!!.requireArguments(), ErrorDialog.RESULT_PAYLOAD, TransferValidationDialogResult::class.java
        )
        assertEquals(payload, envelope?.validationResult)
        assertEquals(host.get().pendingDialog()?.correlationId, envelope?.correlationId)

        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))
        compose.onNodeWithText(ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON).performClick()
        idle()
        assertEquals(
            listOf(ObservedTransferDialogResult(TransferValidationDialogContract.CONFIRM_SEND, ErrorDialog.Action.POSITIVE, payload)),
            results.toList()
        )
        assertNull(host.get().pendingDialog())
    }

    @Test
    fun theSendSetupWarningReportsItsAlternateAndCancelChoices() {
        val alternate = TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning("Polkadot")
        assertTrue(host.get().showRestorableDialog(TransferValidationDialogContract.SEND_SETUP, alternate))
        idle()
        publish(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.SECOND_POSITIVE)
        assertEquals(ErrorDialog.Action.SECOND_POSITIVE, results.single().action)

        ErrorDialogRestorationTestController.reset()
        host.get().dismissCurrentDialog()
        val cancelled = TransferValidationResult.UtilityExistentialDepositWarning("1 KSM")
        assertTrue(host.get().showRestorableDialog(TransferValidationDialogContract.SEND_SETUP, cancelled))
        idle()
        publish(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.NEGATIVE)
        assertEquals(
            listOf(ObservedTransferDialogResult(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.NEGATIVE, cancelled)),
            results.toList()
        )
    }

    @Test
    fun anAlternateChoiceThatWasNeverShownIsRejected() {
        val payload = TransferValidationResult.ExistentialDepositWarning("no alternate")
        assertTrue(host.get().showRestorableDialog(TransferValidationDialogContract.SEND_SETUP, payload, includeSecondPositiveButton = false))
        idle()
        val pending = requireNotNull(host.get().pendingDialog())
        assertNull(pending.secondPositiveButtonText)

        publish(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.SECOND_POSITIVE)
        assertTrue(results.isEmpty())
        assertEquals(pending, host.get().pendingDialog())

        publish(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.POSITIVE)
        assertEquals(ErrorDialog.Action.POSITIVE, results.single().action)
    }

    @Test
    fun aWarningWaitsBehindAnUnrelatedDialogAndDuplicatesAreSuppressed() {
        val contract = TransferValidationDialogContract.CROSS_CHAIN_SETUP
        val payload = TransferValidationResult.ExistentialDepositWarning("7 DOT")
        host.get().showUnrelatedDialog()
        assertTrue(host.get().showRestorableDialog(contract, payload))
        assertFalse("The same warning is not queued twice", host.get().showRestorableDialog(contract, payload))
        assertEquals(1, host.get().activeErrorDialogCount())

        host.get().dismissCurrentDialog()
        idle()
        host.get().executePendingDialogTransactions()
        assertEquals(1, host.get().activeErrorDialogCount())
        assertNotNull(host.get().pendingDialog())
        publish(contract, ErrorDialog.Action.POSITIVE)
        assertEquals(listOf(contract), results.map { it.contract })
    }

    @Test
    fun aRestoredDialogNeverCallsALostInMemoryCallback() {
        host.get().showEphemeralCallbackDialog()
        assertNotNull(host.get().currentDialog())
        host.recreate()
        idle()
        host.get().executePendingDialogTransactions()
        assertNull(host.get().currentDialog())
        assertEquals(0, ErrorDialogRestorationTestController.ephemeralCallbackCount.get())
        assertEquals(1, ErrorDialogRestorationTestController.restoredActivityCount.get())
    }

    private fun publish(contract: TransferValidationDialogContract, action: ErrorDialog.Action) {
        val pending = requireNotNull(host.get().pendingDialog())
        host.get().publishRawResult(
            contract,
            resultBundle(action, TransferValidationDialogResult(pending.correlationId, pending.validationResult))
        )
        idle()
    }

    private fun resultBundle(action: ErrorDialog.Action, payload: Parcelable): Bundle =
        bundleOf(ErrorDialog.RESULT_ACTION to action.name, ErrorDialog.RESULT_PAYLOAD to payload)

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }
}
