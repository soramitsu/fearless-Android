package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft

@AndroidEntryPoint
class ConfirmTransferFragment : BaseFragment<ConfirmTransferViewModel>(R.layout.fragment_confirm_transfer) {

    override val viewModel: ConfirmTransferViewModel by viewModels()

    companion object {
        fun getBundle(transferDraft: TransferDraft) = Bundle().apply {
            putParcelable(KEY_DRAFT, transferDraft)
        }
    }

    override fun initViews() {}
    override fun subscribe(viewModel: ConfirmTransferViewModel) {}

    override fun buildErrorDialog(title: String, errorMessage: String): AlertDialog {
        val base = super.buildErrorDialog(title, errorMessage)

        base.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.common_ok)) { _, _ ->
            viewModel.errorAcknowledged()
        }

        return base
    }
}
