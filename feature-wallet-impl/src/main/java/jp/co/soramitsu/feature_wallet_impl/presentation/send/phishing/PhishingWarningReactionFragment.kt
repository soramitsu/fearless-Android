package jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.view.dialog.showWarningDialog
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.PhishingWarning

abstract class PhishingWarningReactionFragment<T> : BaseFragment<T>() where T : BaseViewModel, T : PhishingWarning {

    override fun subscribe(viewModel: T) {
        viewModel.showPhishingWarning.observeEvent(::showPhishingWarning)
    }

    private fun showPhishingWarning(address: String) {
        showWarningDialog({
            viewModel.proceedAddress(address)
        }) {
            setTitle(R.string.wallet_send_phishing_warning_title)
            setMessage(getString(R.string.wallet_send_phishing_warning_text, address))
        }
    }
}