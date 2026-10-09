package jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogContract
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogCoordinator
import jp.co.soramitsu.wallet.impl.presentation.bindTransferValidationDialog
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft

@AndroidEntryPoint
class CrossChainConfirmFragment : BaseComposeFragment<CrossChainConfirmViewModel>() {

    companion object {

        const val KEY_DRAFT = "KEY_DRAFT"
        const val KEY_PHISHING_TYPE = "KEY_PHISHING_TYPE"
        fun getBundle(transferDraft: CrossChainTransferDraft, phishingType: PhishingType?) = bundleOf(
            KEY_DRAFT to transferDraft,
            KEY_PHISHING_TYPE to phishingType
        )
    }

    override val viewModel: CrossChainConfirmViewModel by viewModels()
    private val validationDialogCoordinator:
        TransferValidationDialogCoordinator by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupExternalActions(viewModel)

        childFragmentManager.bindTransferValidationDialog(
            TransferValidationDialogContract.CROSS_CHAIN_CONFIRM,
            validationDialogCoordinator,
            viewLifecycleOwner,
            onPositive = viewModel::warningConfirmed
        )
        viewModel.openValidationWarningEvent.observeEvent { (result, warning) ->
            validationDialogCoordinator.enqueue(
                TransferValidationDialogContract.CROSS_CHAIN_CONFIRM,
                result,
                warning
            )
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state by viewModel.state.collectAsState()
        CrossChainConfirmContent(
            state = state,
            callback = viewModel
        )
    }

}
