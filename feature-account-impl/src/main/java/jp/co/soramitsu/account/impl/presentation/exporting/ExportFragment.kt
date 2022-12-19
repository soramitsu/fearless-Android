package jp.co.soramitsu.account.impl.presentation.exporting

import android.content.Intent
import androidx.annotation.CallSuper
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.ErrorDialog

abstract class ExportFragment<V : ExportViewModel> : BaseFragment<V>() {

    companion object {
        const val CHOOSER_REQUEST_CODE = 101
    }

    @CallSuper
    override fun subscribe(viewModel: V) {
        viewModel.showSecurityWarningEvent.observeEvent {
            showSecurityWarning()
        }

        viewModel.exportEvent.observeEvent(::shareText)
    }

    private fun shareText(text: String) {
        val title = getString(R.string.common_share)

        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, text)
            .setType("text/plain")

        val chooser = Intent.createChooser(intent, title)

        startActivityForResult(chooser, CHOOSER_REQUEST_CODE)
    }

    private fun showSecurityWarning() {
        val res = requireContext().resources
        ErrorDialog(
            isHideable = false,
            title = res.getString(R.string.account_export_warning_title),
            message = res.getString(R.string.account_export_warning_message),
            positiveButtonText = res.getString(R.string.common_proceed),
            negativeButtonText = res.getString(R.string.common_cancel),
            negativeClick = viewModel::securityWarningCancel,
            onBackClick = viewModel::securityWarningCancel
        ).show(childFragmentManager)
    }
}
