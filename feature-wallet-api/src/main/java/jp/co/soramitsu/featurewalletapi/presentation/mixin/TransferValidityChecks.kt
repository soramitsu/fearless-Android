package jp.co.soramitsu.featurewalletapi.presentation.mixin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.dialog.DialogClickHandler
import jp.co.soramitsu.common.view.dialog.errorDialog
import jp.co.soramitsu.common.view.dialog.warningDialog
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.featurewalletapi.domain.model.TransferValidityLevel

interface TransferValidityChecks {
    val showTransferWarning: LiveData<Event<TransferValidityLevel.Warning.Status>>

    val showTransferError: LiveData<Event<TransferValidityLevel.Error.Status>>

    interface Presentation : TransferValidityChecks {
        fun showTransferWarning(warning: TransferValidityLevel.Warning.Status)

        fun showTransferError(error: TransferValidityLevel.Error.Status)
    }
}

class TransferValidityChecksProvider : TransferValidityChecks.Presentation {
    override fun showTransferWarning(warning: TransferValidityLevel.Warning.Status) {
        showTransferWarning.value = Event(warning)
    }

    override fun showTransferError(error: TransferValidityLevel.Error.Status) {
        showTransferError.value = Event(error)
    }

    override val showTransferWarning = MutableLiveData<Event<TransferValidityLevel.Warning.Status>>()

    override val showTransferError = MutableLiveData<Event<TransferValidityLevel.Error.Status>>()
}

fun <T> BaseFragment<T>.observeTransferChecks(
    viewModel: T,
    warningConfirmed: DialogClickHandler,
    errorConfirmed: DialogClickHandler? = null
) where T : BaseViewModel, T : TransferValidityChecks {
    viewModel.showTransferWarning.observeEvent {
        showTransferWarning(it, warningConfirmed)
    }

    viewModel.showTransferError.observeEvent {
        showTransferError(it, errorConfirmed)
    }
}

private fun BaseFragment<*>.showTransferError(
    status: TransferValidityLevel.Error.Status,
    errorConfirmed: DialogClickHandler?
) {
    val (titleRes, messageRes) = when (status) {
        TransferValidityLevel.Error.Status.NotEnoughFunds -> R.string.common_error_general_title to R.string.choose_amount_error_too_big
        TransferValidityLevel.Error.Status.DeadRecipient -> R.string.common_amount_low to R.string.wallet_send_dead_recipient_message
    }

    errorDialog(requireContext(), errorConfirmed) {
        setTitle(titleRes)
        setMessage(messageRes)
    }
}

private fun BaseFragment<*>.showTransferWarning(
    status: TransferValidityLevel.Warning.Status,
    warningConfirmed: DialogClickHandler
) {
    val (title, message) = when (status) {
        TransferValidityLevel.Warning.Status.WillRemoveAccount -> {
            R.string.wallet_send_existential_warning_title to R.string.wallet_send_existential_warning_message
        }
    }

    warningDialog(requireContext(), warningConfirmed) {
        setTitle(title)
        setMessage(message)
    }
}
