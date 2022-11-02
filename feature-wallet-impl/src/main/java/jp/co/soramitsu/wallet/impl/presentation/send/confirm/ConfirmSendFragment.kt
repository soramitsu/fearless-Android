package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.wallet.api.presentation.mixin.observeTransferChecks
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft

const val KEY_DRAFT = "KEY_DRAFT"

@AndroidEntryPoint
class ConfirmSendFragment : BaseComposeBottomSheetDialogFragment<ConfirmSendViewModel>() {

    companion object {
        fun getBundle(transferDraft: TransferDraft) = Bundle().apply {
            putParcelable(KEY_DRAFT, transferDraft)
        }
    }

    override val viewModel: ConfirmSendViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupExternalActions(viewModel)
        observeTransferChecks(viewModel, viewModel::warningConfirmed, viewModel::errorAcknowledged)
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        ConfirmSendContent(
            state = state,
            callback = viewModel
        )
    }

//    override fun buildErrorDialog(title: String, errorMessage: String): AlertDialog {
//        val base = super.buildErrorDialog(title, errorMessage)
//
//        base.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.common_ok)) { _, _ ->
//            viewModel.errorAcknowledged()
//        }
//
//        return base
//    }
}
