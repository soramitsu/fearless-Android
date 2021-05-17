package jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.view.dialog.warningDialog
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.PhishingWarningPresentation

fun <T> BaseFragment<T>.observePhishingCheck(
    viewModel: T
) where T : BaseViewModel, T : PhishingWarningPresentation {
    viewModel.showPhishingWarning.observeEvent {
        showPhishingWarning(viewModel, it)
    }
}

private fun <T> BaseFragment<T>.showPhishingWarning(
    viewModel: T,
    address: String
) where T : BaseViewModel, T : PhishingWarningPresentation {
    warningDialog(
        requireContext(),
        { viewModel.proceedAddress(address) },
        viewModel::declinePhishingAddress
    ) {
        setTitle(R.string.wallet_send_phishing_warning_title)
        setMessage(getString(R.string.wallet_send_phishing_warning_text, address))
    }
}
