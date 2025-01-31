package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.feature_wallet_impl.R

@AndroidEntryPoint
class OptionsWalletFragment : BaseComposeBottomSheetDialogFragment<OptionsWalletViewModel>() {

    companion object {
        const val KEY_WALLET_ID = "id"
        const val KEY_ALLOW_WALLET_DETAILS = "key_allow_wallet_details"

        fun getBundle(walletId: Long, allowDetails: Boolean = true) = bundleOf(
            KEY_WALLET_ID to walletId,
            KEY_ALLOW_WALLET_DETAILS to allowDetails
        )
    }

    override val viewModel: OptionsWalletViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        OptionsWalletContent(
            state = state,
            callback = viewModel
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.deleteWalletConfirmation.observeEvent(::showDeleteWalletConfirmation)
    }

    private fun showDeleteWalletConfirmation(metaId: Long) {
        val res = requireContext().resources
        ErrorDialog(
            title = res.getString(R.string.account_delete_confirmation_title),
            message = res.getString(R.string.account_delete_confirmation_description),
            positiveButtonText = res.getString(R.string.account_delete_confirm),
            negativeButtonText = res.getString(R.string.common_cancel),
            positiveClick = viewModel::deleteWalletConfirmed
        ).show(childFragmentManager)
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
