package jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.view.dialog.showWarningDialog
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.PhishingWarning

fun <T> BaseFragment<T>.observePhishingCheck(
    viewModel: T
) where T : BaseViewModel, T : PhishingWarning {
    viewModel.showPhishingWarning.observeEvent {
        showPhishingWarning(viewModel, it)
    }
}

private fun <T> BaseFragment<T>.showPhishingWarning(
    viewModel: T,
    address: String
) where T : BaseViewModel, T : PhishingWarning {
    showWarningDialog({
        viewModel.proceedAddress(address)
    }) {
        setTitle(R.string.wallet_send_phishing_warning_title)
        setMessage(getString(R.string.wallet_send_phishing_warning_text, address))
    }
}