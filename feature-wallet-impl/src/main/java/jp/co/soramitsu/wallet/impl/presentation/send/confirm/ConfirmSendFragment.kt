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
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft

@AndroidEntryPoint
class ConfirmSendFragment : BaseComposeBottomSheetDialogFragment<ConfirmSendViewModel>() {

    companion object {
        const val KEY_DRAFT = "KEY_DRAFT"
        const val KEY_PHISHING_TYPE = "KEY_PHISHING_TYPE"
        const val KEY_TRANSFER_COMMENT = "KEY_TRANSFER_COMMENT"

        const val KEY_OVERRIDES = "KEY_OVERRIDES"
        const val KEY_OVERRIDE_TO_VALUE = "key_toValue"
        const val KEY_OVERRIDE_ICON_RES_ID = "key_iconResId"


        fun getBundle(transferDraft: TransferDraft, phishingType: PhishingType?, overrides: Map<String, Any?>, transferComment: String?) = bundleOf(
            KEY_DRAFT to transferDraft,
            KEY_PHISHING_TYPE to phishingType,
            KEY_OVERRIDES to overrides,
            KEY_TRANSFER_COMMENT to transferComment
        )
    }

    override val viewModel: ConfirmSendViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupExternalActions(viewModel)

        viewModel.openValidationWarningEvent.observeEvent { (result, warning) ->
            ErrorDialog(
                title = warning.message,
                message = warning.explanation,
                positiveButtonText = warning.positiveButtonText,
                negativeButtonText = warning.negativeButtonText,
                positiveClick = { viewModel.warningConfirmed(result) },
                isHideable = false
            ).show(childFragmentManager)
        }
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
