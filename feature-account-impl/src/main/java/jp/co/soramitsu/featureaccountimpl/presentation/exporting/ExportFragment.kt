package jp.co.soramitsu.featureaccountimpl.presentation.exporting

import android.content.Intent
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseFragment

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
        val title = getString(jp.co.soramitsu.feature_account_impl.R.string.common_share)

        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, text)
            .setType("text/plain")

        val chooser = Intent.createChooser(intent, title)

        startActivityForResult(chooser, CHOOSER_REQUEST_CODE)
    }

    private fun showSecurityWarning() {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.account_export_warning_title)
            .setCancelable(false)
            .setMessage(R.string.account_export_warning_message)
            .setPositiveButton(R.string.common_proceed, null)
            .setNegativeButton(R.string.common_cancel) { _, _ -> viewModel.securityWarningCancel() }
            .show()
    }
}
