package jp.co.soramitsu.wallet.impl.presentation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import java.util.UUID
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import kotlinx.parcelize.Parcelize

private const val TRANSFER_VALIDATION_DIALOG_TAG_PREFIX =
    "${ErrorDialog.TAG}:transfer-validation:"

/**
 * The production result contracts are centralized so production screens and
 * restoration tests cannot silently drift onto parallel request keys.
 */
enum class TransferValidationDialogContract(
    val requestKey: String,
    val resultActions: Set<ErrorDialog.Action>,
    val callbackActions: Set<ErrorDialog.Action>
) {
    SEND_SETUP(
        "send_setup_validation_dialog_result",
        setOf(
            ErrorDialog.Action.POSITIVE,
            ErrorDialog.Action.SECOND_POSITIVE,
            ErrorDialog.Action.NEGATIVE
        ),
        setOf(
            ErrorDialog.Action.POSITIVE,
            ErrorDialog.Action.SECOND_POSITIVE,
            ErrorDialog.Action.NEGATIVE
        )
    ),
    CONFIRM_SEND(
        "confirm_send_validation_dialog_result",
        setOf(ErrorDialog.Action.POSITIVE, ErrorDialog.Action.NEGATIVE),
        setOf(ErrorDialog.Action.POSITIVE)
    ),
    CBDC_SEND_SETUP(
        "cbdc_send_setup_validation_dialog_result",
        setOf(ErrorDialog.Action.POSITIVE, ErrorDialog.Action.NEGATIVE),
        setOf(ErrorDialog.Action.POSITIVE)
    ),
    CROSS_CHAIN_CONFIRM(
        "cross_chain_confirm_validation_dialog_result",
        setOf(ErrorDialog.Action.POSITIVE, ErrorDialog.Action.NEGATIVE),
        setOf(ErrorDialog.Action.POSITIVE)
    ),
    CROSS_CHAIN_SETUP(
        "cross_chain_setup_validation_dialog_result",
        setOf(ErrorDialog.Action.POSITIVE, ErrorDialog.Action.NEGATIVE),
        setOf(ErrorDialog.Action.POSITIVE)
    )
}

@Parcelize
data class PendingTransferValidationDialog(
    val contract: TransferValidationDialogContract,
    val correlationId: String,
    val validationResult: TransferValidationResult,
    val title: String,
    val message: String,
    val positiveButtonText: String,
    val secondPositiveButtonText: String?,
    val negativeButtonText: String
) : Parcelable {

    fun matches(result: TransferValidationDialogResult): Boolean =
        correlationId == result.correlationId &&
            validationResult == result.validationResult &&
            validationResult.isApprovableTransferWarning()
}

@Parcelize
data class TransferValidationDialogResult(
    val correlationId: String,
    val validationResult: TransferValidationResult
) : Parcelable

/**
 * Fragment-scoped state holder for a warning that has not yet been acted on.
 *
 * SavedStateHandle keeps the warning available across view/configuration and
 * process restoration. The warning is cleared only by a correlated, semantically
 * valid dialog result; a temporarily saved FragmentManager or another dialog
 * therefore cannot consume and lose it.
 */
class TransferValidationDialogCoordinator(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val pendingDialog: LiveData<PendingTransferValidationDialog?> =
        savedStateHandle.getLiveData(PENDING_DIALOG_KEY)

    fun enqueue(
        contract: TransferValidationDialogContract,
        validationResult: TransferValidationResult,
        warning: ValidationWarning
    ): Boolean {
        if (!validationResult.isApprovableTransferWarning()) {
            return false
        }

        val current = pendingDialog.value
        if (current != null) {
            val isDuplicate =
                current.contract == contract &&
                    current.validationResult == validationResult &&
                    current.title == warning.message &&
                    current.message == warning.explanation &&
                    current.positiveButtonText == warning.positiveButtonText &&
                    current.secondPositiveButtonText ==
                    warning.secondPositiveButtonText &&
                    current.negativeButtonText == warning.negativeButtonText
            if (isDuplicate) {
                return false
            }
        }

        // A genuinely newer warning supersedes the pending request. An already
        // visible old dialog now carries a stale correlation and cannot execute;
        // after it closes the lifecycle retry presents this newest warning.
        savedStateHandle[PENDING_DIALOG_KEY] =
            PendingTransferValidationDialog(
                contract = contract,
                correlationId = UUID.randomUUID().toString(),
                validationResult = validationResult,
                title = warning.message,
                message = warning.explanation,
                positiveButtonText = warning.positiveButtonText,
                secondPositiveButtonText = warning.secondPositiveButtonText,
                negativeButtonText = warning.negativeButtonText
            )
        return true
    }

    fun consumeIfMatches(
        contract: TransferValidationDialogContract,
        action: ErrorDialog.Action,
        result: TransferValidationDialogResult
    ): PendingTransferValidationDialog? {
        val pending = pendingDialog.value ?: return null
        if (
            pending.contract != contract ||
            !pending.matches(result) ||
            (
                action == ErrorDialog.Action.SECOND_POSITIVE &&
                    pending.secondPositiveButtonText == null
                )
        ) {
            return null
        }

        // Clear before invoking a wallet transition. A duplicate FragmentResult
        // delivered reentrantly or after recreation then fails closed.
        savedStateHandle[PENDING_DIALOG_KEY] = null
        return pending
    }

    private companion object {
        const val PENDING_DIALOG_KEY = "pending_transfer_validation_dialog"
    }
}

fun TransferValidationResult.isApprovableTransferWarning(): Boolean =
    this is TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning ||
        this is TransferValidationResult.ExistentialDepositWarning ||
        this is TransferValidationResult.UtilityExistentialDepositWarning

/**
 * Binds a screen's durable pending warning to its child FragmentManager.
 *
 * Delivery is retried when the view resumes and whenever another dialog is
 * removed. Registration is scoped to the view lifecycle and explicitly removed
 * on destruction, so no Fragment/ViewModel callbacks leak into a recreated view.
 */
fun FragmentManager.bindTransferValidationDialog(
    contract: TransferValidationDialogContract,
    coordinator: TransferValidationDialogCoordinator,
    lifecycleOwner: LifecycleOwner,
    onPositive: ((TransferValidationResult) -> Unit)? = null,
    onSecondPositive: ((TransferValidationResult) -> Unit)? = null,
    onNegative: ((TransferValidationResult) -> Unit)? = null
) {
    require(contract.callbackActions.contains(ErrorDialog.Action.POSITIVE) ==
        (onPositive != null)) {
        "Positive callback does not match ${contract.name}"
    }
    require(
        contract.callbackActions.contains(ErrorDialog.Action.SECOND_POSITIVE) ==
            (onSecondPositive != null)
    ) {
        "Second-positive callback does not match ${contract.name}"
    }
    require(contract.callbackActions.contains(ErrorDialog.Action.NEGATIVE) ==
        (onNegative != null)) {
        "Negative callback does not match ${contract.name}"
    }

    setFragmentResultListener(
        contract.requestKey,
        lifecycleOwner
    ) { _, resultBundle ->
        val action = ErrorDialog.resultAction(resultBundle)
            ?: return@setFragmentResultListener
        if (action !in contract.resultActions) {
            return@setFragmentResultListener
        }
        val result = resultBundle.transferValidationDialogResultOrNull()
            ?: return@setFragmentResultListener
        val consumed = coordinator.consumeIfMatches(contract, action, result)
            ?: return@setFragmentResultListener

        when (action) {
            ErrorDialog.Action.POSITIVE -> onPositive
            ErrorDialog.Action.SECOND_POSITIVE -> onSecondPositive
            ErrorDialog.Action.NEGATIVE -> onNegative
            ErrorDialog.Action.BACK -> null
        }?.invoke(consumed.validationResult)
    }

    fun tryShowPending() {
        val pending = coordinator.pendingDialog.value ?: return
        if (pending.contract != contract) {
            return
        }
        showTransferValidationDialogOnce(contract, pending)
    }

    val mainHandler = Handler(Looper.getMainLooper())
    fun retryAfterCurrentFragmentTransaction() {
        mainHandler.post {
            if (
                lifecycleOwner.lifecycle.currentState.isAtLeast(
                    Lifecycle.State.STARTED
                )
            ) {
                tryShowPending()
            }
        }
    }

    val fragmentCallbacks =
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentDestroyed(
                fragmentManager: FragmentManager,
                fragment: Fragment
            ) {
                if (fragment is ErrorDialog) {
                    // Fragment lifecycle callbacks run while FragmentManager is
                    // executing the removal transaction. Retry on the next main
                    // loop turn to avoid a reentrant showNow transaction.
                    retryAfterCurrentFragmentTransaction()
                }
            }
        }
    registerFragmentLifecycleCallbacks(fragmentCallbacks, false)

    val lifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                tryShowPending()
            }

            override fun onResume(owner: LifecycleOwner) {
                tryShowPending()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                mainHandler.removeCallbacksAndMessages(null)
                unregisterFragmentLifecycleCallbacks(fragmentCallbacks)
                owner.lifecycle.removeObserver(this)
            }
        }
    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    coordinator.pendingDialog.observe(lifecycleOwner) {
        tryShowPending()
    }
}

/**
 * Shows at most one transfer validation dialog in this screen's child manager.
 */
fun FragmentManager.showTransferValidationDialogOnce(
    contract: TransferValidationDialogContract,
    pending: PendingTransferValidationDialog
): Boolean {
    if (isDestroyed || isStateSaved) {
        return false
    }

    try {
        executePendingTransactions()
    } catch (_: IllegalStateException) {
        // The manager may be inside a lifecycle/restoration transaction. The
        // coordinator retains the request and retries on the next lifecycle turn.
        return false
    }
    if (isDestroyed || isStateSaved) {
        return false
    }

    val requestTag = TRANSFER_VALIDATION_DIALOG_TAG_PREFIX + contract.requestKey
    val hasActiveErrorDialog =
        findFragmentByTag(requestTag) != null ||
            fragments.any { fragment ->
                fragment is ErrorDialog && !fragment.isRemoving
            }
    if (hasActiveErrorDialog) {
        return false
    }

    return try {
        ErrorDialog(
            title = pending.title,
            message = pending.message,
            positiveButtonText = pending.positiveButtonText,
            secondPositiveButtonText = pending.secondPositiveButtonText,
            negativeButtonText = pending.negativeButtonText,
            resultRequestKey = contract.requestKey,
            resultPayload =
                TransferValidationDialogResult(
                    pending.correlationId,
                    pending.validationResult
                ),
            isHideable = false
        ).showNow(this, requestTag)
        true
    } catch (_: IllegalStateException) {
        // A concurrent lifecycle transition won the final show race. Retain and
        // retry the same correlated request rather than consuming the warning.
        false
    }
}

private fun Bundle.transferValidationDialogResultOrNull():
    TransferValidationDialogResult? {
    return try {
        BundleCompat.getParcelable(
            this,
            ErrorDialog.RESULT_PAYLOAD,
            TransferValidationDialogResult::class.java
        )
    } catch (_: RuntimeException) {
        // A corrupt or type-confused restored Bundle must not crash the wallet.
        null
    }
}
