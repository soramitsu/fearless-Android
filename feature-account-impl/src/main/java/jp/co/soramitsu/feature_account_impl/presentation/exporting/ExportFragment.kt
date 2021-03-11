package jp.co.soramitsu.feature_account_impl.presentation.exporting

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ShareCompletedReceiver

abstract class ExportFragment<V : ExportViewModel> : BaseFragment<V>() {

    @CallSuper
    override fun subscribe(viewModel: V) {
        viewModel.showSecurityWarningEvent.observeEvent {
            showSecurityWarning()
        }

        viewModel.exportEvent.observeEvent(::shareTextWithCallback)
    }

    private fun shareTextWithCallback(text: String) {
        val title = getString(jp.co.soramitsu.feature_account_impl.R.string.common_share)

        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, text)
            .setType("text/plain")

        val receiver = Intent(requireContext(), ShareCompletedReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, receiver, PendingIntent.FLAG_UPDATE_CURRENT)

        val chooser = Intent.createChooser(intent, title, pendingIntent.intentSender)

        startActivity(chooser)
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