package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.wallet.api.presentation.mixin.observeTransferChecks
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft

@AndroidEntryPoint
class ConfirmSendFragment : BaseComposeBottomSheetDialogFragment<ConfirmSendViewModel>() {

    companion object {
        const val KEY_DRAFT = "KEY_DRAFT"
        const val KEY_PHISHING_TYPE = "KEY_PHISHING_TYPE"

        fun getBundle(transferDraft: TransferDraft, phishingType: PhishingType?) = bundleOf(
            KEY_DRAFT to transferDraft,
            KEY_PHISHING_TYPE to phishingType
        )
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
}
