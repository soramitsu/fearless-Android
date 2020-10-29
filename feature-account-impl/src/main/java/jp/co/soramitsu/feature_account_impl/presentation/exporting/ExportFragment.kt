package jp.co.soramitsu.feature_account_impl.presentation.exporting

import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.shareText

abstract class ExportFragment<V: ExportViewModel> : BaseFragment<V>() {

    @CallSuper
    override fun subscribe(viewModel: V) {
        viewModel.showSecurityWarningEvent.observeEvent {
            showSecurityWarning()
        }

        viewModel.exportEvent.observeEvent(::shareText)
    }

    private fun showSecurityWarning() {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.account_export_warning_title)
            .setMessage(R.string.account_export_warning_message)
            .setPositiveButton(R.string.common_ok) { _, _ -> viewModel.securityWarningConfirmed() }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }
}