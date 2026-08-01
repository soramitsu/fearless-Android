package jp.co.soramitsu.app.root.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.presentation.PendingTransferValidationDialog
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogContract
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogCoordinator
import jp.co.soramitsu.wallet.impl.presentation.bindTransferValidationDialog

/**
 * Debug-only activity for exercising nested FragmentManager save/restore.
 * This component is absent from release-like manifests.
 */
class ErrorDialogRestorationActivityTestHost : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            ErrorDialogRestorationTestController.restoredActivityCount
                .incrementAndGet()
        }
        setContentView(
            FrameLayout(this).apply {
                id = R.id.error_dialog_restoration_parent_container
            }
        )

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.error_dialog_restoration_parent_container,
                    ErrorDialogRestorationParentTestFragment(),
                    PARENT_FRAGMENT_TAG
                )
                .commitNow()
        }
    }

    fun showRestorableDialog(
        contract: TransferValidationDialogContract,
        payload: TransferValidationResult,
        includeSecondPositiveButton: Boolean = true
    ): Boolean = currentParent().enqueueRestorableDialog(
        contract,
        payload,
        includeSecondPositiveButton
    )

    fun pendingDialog(): PendingTransferValidationDialog? =
        currentParent().pendingDialog()

    fun showUnrelatedDialog() {
        currentParent().showUnrelatedDialog()
    }

    fun dismissCurrentDialog() {
        currentParent().dismissCurrentDialog()
    }

    fun showEphemeralCallbackDialog() {
        currentParent().showEphemeralCallbackDialog()
    }

    fun publishRawResult(
        contract: TransferValidationDialogContract,
        result: Bundle
    ) {
        currentParent().publishRawResult(contract, result)
    }

    fun currentDialog(): ErrorDialog? = currentParent().currentDialog()

    fun currentParent(): ErrorDialogRestorationParentTestFragment {
        supportFragmentManager.executePendingTransactions()
        return checkNotNull(
            supportFragmentManager.findFragmentByTag(
                PARENT_FRAGMENT_TAG
            ) as? ErrorDialogRestorationParentTestFragment
        ) {
            "The restoration parent fragment is not attached"
        }
    }

    fun executePendingDialogTransactions() {
        currentParent().childFragmentManager.executePendingTransactions()
    }

    fun activeErrorDialogCount(): Int =
        currentParent().activeErrorDialogCount()

    companion object {
        const val POSITIVE_BUTTON = "Confirm transfer"
        const val SECOND_POSITIVE_BUTTON = "Confirm alternate path"
        const val NEGATIVE_BUTTON = "Cancel transfer"
        const val EPHEMERAL_POSITIVE_BUTTON = "Acknowledge ephemeral warning"

        private const val PARENT_FRAGMENT_TAG =
            "error_dialog_restoration_parent"
    }
}

/**
 * Mirrors the production topology: a screen Fragment owns ErrorDialog through
 * its child FragmentManager and observes results with its view lifecycle.
 */
class ErrorDialogRestorationParentTestFragment : Fragment() {

    private val validationDialogCoordinator:
        TransferValidationDialogCoordinator by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(requireContext())

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        registerListenerContracts()
    }

    private fun registerListenerContracts() {
        childFragmentManager.bindTransferValidationDialog(
            TransferValidationDialogContract.SEND_SETUP,
            validationDialogCoordinator,
            viewLifecycleOwner,
            onPositive = {
                record(
                    TransferValidationDialogContract.SEND_SETUP,
                    ErrorDialog.Action.POSITIVE,
                    it
                )
            },
            onSecondPositive = {
                record(
                    TransferValidationDialogContract.SEND_SETUP,
                    ErrorDialog.Action.SECOND_POSITIVE,
                    it
                )
            },
            onNegative = {
                record(
                    TransferValidationDialogContract.SEND_SETUP,
                    ErrorDialog.Action.NEGATIVE,
                    it
                )
            }
        )

        positiveOnlyContracts().forEach { contract ->
            childFragmentManager.bindTransferValidationDialog(
                contract,
                validationDialogCoordinator,
                viewLifecycleOwner,
                onPositive = {
                    record(contract, ErrorDialog.Action.POSITIVE, it)
                }
            )
        }
    }

    fun enqueueRestorableDialog(
        contract: TransferValidationDialogContract,
        payload: TransferValidationResult,
        includeSecondPositiveButton: Boolean
    ): Boolean =
        validationDialogCoordinator.enqueue(
            contract,
            payload,
            testWarning(contract, includeSecondPositiveButton)
        )

    fun pendingDialog(): PendingTransferValidationDialog? =
        validationDialogCoordinator.pendingDialog.value

    fun showUnrelatedDialog() {
        ErrorDialog(
            title = "Unrelated error",
            message = "Dismiss before the transfer warning",
            positiveButtonText = UNRELATED_BUTTON,
            isHideable = true
        ).showNow(childFragmentManager, UNRELATED_DIALOG_TAG)
    }

    fun dismissCurrentDialog() {
        currentDialog()?.dismiss()
        childFragmentManager.executePendingTransactions()
    }

    fun showEphemeralCallbackDialog() {
        check(currentDialog() == null) {
            "Only one restoration dialog may be active"
        }
        ErrorDialog(
            title = TEST_TITLE,
            message = TEST_MESSAGE,
            positiveButtonText =
                ErrorDialogRestorationActivityTestHost
                    .EPHEMERAL_POSITIVE_BUTTON,
            isHideable = false,
            positiveClick = {
                ErrorDialogRestorationTestController
                    .ephemeralCallbackCount
                    .incrementAndGet()
            }
        ).showNow(childFragmentManager, ErrorDialog.TAG)
    }

    fun publishRawResult(
        contract: TransferValidationDialogContract,
        result: Bundle
    ) {
        childFragmentManager.setFragmentResult(contract.requestKey, result)
    }

    fun currentDialog(): ErrorDialog? {
        return childFragmentManager.fragments
            .filterIsInstance<ErrorDialog>()
            .firstOrNull { !it.isRemoving }
    }

    fun activeErrorDialogCount(): Int =
        childFragmentManager.fragments.count {
            it is ErrorDialog && !it.isRemoving
        }

    private fun record(
        contract: TransferValidationDialogContract,
        action: ErrorDialog.Action,
        payload: TransferValidationResult
    ) {
        ErrorDialogRestorationTestController.results +=
            ObservedTransferDialogResult(contract, action, payload)
    }

    companion object {
        private const val TEST_TITLE = "Transfer warning"
        private const val TEST_MESSAGE = "Review this transfer"
        private const val UNRELATED_DIALOG_TAG = "unrelated_error_dialog"
        private const val UNRELATED_BUTTON = "Dismiss unrelated error"

        private fun positiveOnlyContracts() = listOf(
            TransferValidationDialogContract.CONFIRM_SEND,
            TransferValidationDialogContract.CBDC_SEND_SETUP,
            TransferValidationDialogContract.CROSS_CHAIN_CONFIRM,
            TransferValidationDialogContract.CROSS_CHAIN_SETUP
        )

        private fun testWarning(
            contract: TransferValidationDialogContract,
            includeSecondPositiveButton: Boolean
        ) = ValidationWarning(
            TEST_TITLE,
            TEST_MESSAGE,
            ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON,
            if (
                contract == TransferValidationDialogContract.SEND_SETUP &&
                includeSecondPositiveButton
            ) {
                ErrorDialogRestorationActivityTestHost.NEGATIVE_BUTTON
            } else {
                "Dismiss transfer warning"
            },
            if (
                contract == TransferValidationDialogContract.SEND_SETUP &&
                includeSecondPositiveButton
            ) {
                ErrorDialogRestorationActivityTestHost
                    .SECOND_POSITIVE_BUTTON
            } else {
                null
            }
        )
    }
}

data class ObservedTransferDialogResult(
    val contract: TransferValidationDialogContract,
    val action: ErrorDialog.Action,
    val payload: TransferValidationResult
)

object ErrorDialogRestorationTestController {
    val results = CopyOnWriteArrayList<ObservedTransferDialogResult>()
    val restoredActivityCount = AtomicInteger()
    val ephemeralCallbackCount = AtomicInteger()

    fun reset() {
        results.clear()
        restoredActivityCount.set(0)
        ephemeralCallbackCount.set(0)
    }
}
