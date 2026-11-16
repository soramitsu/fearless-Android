package jp.co.soramitsu.wallet.impl.presentation

import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class TransferValidationDialogRobolectricTest {

    private val ed = TransferValidationResult.ExistentialDepositWarning("0.01 DOT")
    private val warning = ValidationWarning("Below ED", "The account may be reaped", "Continue", "Cancel", "Send all")

    @Test
    fun onlyApprovableWarningsAreQueuedAndDuplicatesAreIgnored() {
        val coordinator = TransferValidationDialogCoordinator(SavedStateHandle())

        assertFalse(coordinator.enqueue(TransferValidationDialogContract.SEND_SETUP, TransferValidationResult.InsufficientBalance, warning))
        assertTrue(coordinator.enqueue(TransferValidationDialogContract.SEND_SETUP, ed, warning))
        assertFalse(coordinator.enqueue(TransferValidationDialogContract.SEND_SETUP, ed, warning))

        val first = coordinator.pendingDialog.value!!
        assertEquals("Below ED", first.title)
        assertEquals("Send all", first.secondPositiveButtonText)

        val newer = ValidationWarning("Below ED again", "Explanation", "Continue", "Cancel", null)
        assertTrue(coordinator.enqueue(TransferValidationDialogContract.SEND_SETUP, ed, newer))
        assertTrue(coordinator.pendingDialog.value!!.correlationId != first.correlationId)
    }

    @Test
    fun consumeRequiresTheSameContractCorrelationAndAButtonThatExists() {
        val coordinator = TransferValidationDialogCoordinator(SavedStateHandle())
        assertNull(coordinator.consumeIfMatches(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.POSITIVE, TransferValidationDialogResult("x", ed)))

        coordinator.enqueue(TransferValidationDialogContract.SEND_SETUP, ed, ValidationWarning("t", "m", "ok", "no", null))
        val pending = coordinator.pendingDialog.value!!
        val result = TransferValidationDialogResult(pending.correlationId, ed)

        assertNull(coordinator.consumeIfMatches(TransferValidationDialogContract.CONFIRM_SEND, ErrorDialog.Action.POSITIVE, result))
        assertNull(coordinator.consumeIfMatches(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.POSITIVE, TransferValidationDialogResult("other", ed)))
        assertNull(coordinator.consumeIfMatches(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.SECOND_POSITIVE, result))
        assertEquals(pending, coordinator.consumeIfMatches(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.POSITIVE, result))
        assertNull(coordinator.pendingDialog.value)
        assertNull(coordinator.consumeIfMatches(TransferValidationDialogContract.SEND_SETUP, ErrorDialog.Action.POSITIVE, result))
    }

    @Test
    fun approvableWarningsAreTheExistentialDepositAndBridgeFeeWarnings() {
        assertTrue(ed.isApprovableTransferWarning())
        assertTrue(TransferValidationResult.UtilityExistentialDepositWarning("1").isApprovableTransferWarning())
        assertTrue(TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning("SORA").isApprovableTransferWarning())
        assertFalse(TransferValidationResult.Valid.isApprovableTransferWarning())
        val pending = PendingTransferValidationDialog(TransferValidationDialogContract.CONFIRM_SEND, "id", TransferValidationResult.Valid, "t", "m", "p", null, "n")
        assertFalse(pending.matches(TransferValidationDialogResult("id", TransferValidationResult.Valid)))
    }

    @Test
    fun bindingRejectsCallbacksThatDoNotMatchTheContract() {
        val controller = activity()
        val fm = controller.get().supportFragmentManager
        val coordinator = TransferValidationDialogCoordinator(SavedStateHandle())

        assertThrows(IllegalArgumentException::class.java) {
            fm.bindTransferValidationDialog(TransferValidationDialogContract.CONFIRM_SEND, coordinator, controller.get())
        }
        assertThrows(IllegalArgumentException::class.java) {
            fm.bindTransferValidationDialog(TransferValidationDialogContract.CONFIRM_SEND, coordinator, controller.get(), onPositive = {}, onSecondPositive = {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            fm.bindTransferValidationDialog(TransferValidationDialogContract.CONFIRM_SEND, coordinator, controller.get(), onPositive = {}, onNegative = {})
        }
    }

    @Test
    fun pendingWarningIsShownOnceAndItsCorrelatedResultRunsTheCallback() {
        val controller = activity()
        val activity = controller.get()
        val fm = activity.supportFragmentManager
        val coordinator = TransferValidationDialogCoordinator(SavedStateHandle())
        val results = mutableListOf<String>()
        fm.bindTransferValidationDialog(
            TransferValidationDialogContract.SEND_SETUP,
            coordinator,
            activity,
            onPositive = { results += "positive:$it" },
            onSecondPositive = { results += "second:$it" },
            onNegative = { results += "negative:$it" }
        )

        coordinator.enqueue(TransferValidationDialogContract.SEND_SETUP, ed, warning)
        ShadowLooper.idleMainLooper()
        fm.executePendingTransactions()
        val pending = coordinator.pendingDialog.value!!
        assertNotNull(fm.findFragmentByTag(TAG_PREFIX + TransferValidationDialogContract.SEND_SETUP.requestKey))
        // A second show while the dialog is active is refused.
        assertFalse(fm.showTransferValidationDialogOnce(TransferValidationDialogContract.SEND_SETUP, pending))

        // A stale correlation, an unknown action and a missing payload are ignored.
        fm.setFragmentResult(pending.contract.requestKey, bundleOf(ErrorDialog.RESULT_ACTION to "POSITIVE", ErrorDialog.RESULT_PAYLOAD to TransferValidationDialogResult("stale", ed)))
        fm.setFragmentResult(pending.contract.requestKey, bundleOf(ErrorDialog.RESULT_ACTION to "BACK", ErrorDialog.RESULT_PAYLOAD to TransferValidationDialogResult(pending.correlationId, ed)))
        fm.setFragmentResult(pending.contract.requestKey, bundleOf(ErrorDialog.RESULT_ACTION to "NOPE"))
        fm.setFragmentResult(pending.contract.requestKey, bundleOf(ErrorDialog.RESULT_ACTION to "SECOND_POSITIVE"))
        assertTrue(results.isEmpty())

        fm.setFragmentResult(
            pending.contract.requestKey,
            bundleOf(ErrorDialog.RESULT_ACTION to "SECOND_POSITIVE", ErrorDialog.RESULT_PAYLOAD to TransferValidationDialogResult(pending.correlationId, ed))
        )

        assertEquals(listOf("second:$ed"), results)
        assertNull(coordinator.pendingDialog.value)
        controller.pause().stop().destroy()
    }

    @Test
    fun dialogForAnotherContractIsNotShownHere() {
        val controller = activity()
        val activity = controller.get()
        val fm = activity.supportFragmentManager
        val coordinator = TransferValidationDialogCoordinator(SavedStateHandle())
        val results = mutableListOf<String>()
        fm.bindTransferValidationDialog(TransferValidationDialogContract.CONFIRM_SEND, coordinator, activity, onPositive = { results += "p" })

        coordinator.enqueue(TransferValidationDialogContract.CROSS_CHAIN_SETUP, ed, warning)
        ShadowLooper.idleMainLooper()
        fm.executePendingTransactions()

        assertNull(fm.findFragmentByTag(TAG_PREFIX + TransferValidationDialogContract.CONFIRM_SEND.requestKey))
        assertNull(fm.findFragmentByTag(TAG_PREFIX + TransferValidationDialogContract.CROSS_CHAIN_SETUP.requestKey))
    }

    @Test
    fun negativeResultOfConfirmContractIsDroppedWithoutCallback() {
        val controller = activity()
        val activity = controller.get()
        val fm = activity.supportFragmentManager
        val coordinator = TransferValidationDialogCoordinator(SavedStateHandle())
        val results = mutableListOf<String>()
        fm.bindTransferValidationDialog(TransferValidationDialogContract.CROSS_CHAIN_CONFIRM, coordinator, activity, onPositive = { results += "p" })
        coordinator.enqueue(TransferValidationDialogContract.CROSS_CHAIN_CONFIRM, ed, warning)
        ShadowLooper.idleMainLooper()
        val pending = coordinator.pendingDialog.value!!

        fm.setFragmentResult(
            pending.contract.requestKey,
            bundleOf(ErrorDialog.RESULT_ACTION to "NEGATIVE", ErrorDialog.RESULT_PAYLOAD to TransferValidationDialogResult(pending.correlationId, ed))
        )

        assertTrue(results.isEmpty())
        assertNull(coordinator.pendingDialog.value)
    }

    @Test
    fun showIsRefusedAfterStateIsSaved() {
        val controller = activity()
        val fm = controller.get().supportFragmentManager
        controller.pause().stop()
        val pending = PendingTransferValidationDialog(TransferValidationDialogContract.CONFIRM_SEND, "id", ed, "t", "m", "p", null, "n")

        controller.saveInstanceState(android.os.Bundle())

        assertFalse(fm.showTransferValidationDialogOnce(TransferValidationDialogContract.CONFIRM_SEND, pending))
    }

    private fun activity(): ActivityController<FragmentActivity> {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar)
        return controller.setup()
    }

    private companion object {
        const val TAG_PREFIX = "${ErrorDialog.TAG}:transfer-validation:"
    }
}
