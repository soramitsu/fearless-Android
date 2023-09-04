package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light

import android.os.Bundle
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
import jp.co.soramitsu.common.compose.component.WalletSelectorScreen
import jp.co.soramitsu.wallet.impl.presentation.model.WalletSelectionMode

@AndroidEntryPoint
class WalletSelectorFragment : BaseComposeBottomSheetDialogFragment<WalletSelectorViewModel>() {

    companion object {
        const val TAG_ARGUMENT_KEY = "tag"
        const val SELECTED_WALLET_ID = "SELECTED_WALLET_ID"
        const val WALLET_SELECTION_MODE = "WALLET_SELECTION_MODE"
        const val RESULT_ADDRESS = "RESULT_ADDRESS"

        fun buildArguments(
            tag: String,
            selectedWalletId: Long? = null,
            walletSelectionMode: WalletSelectionMode? = null
        ): Bundle {
            return bundleOf(
                TAG_ARGUMENT_KEY to tag,
                SELECTED_WALLET_ID to selectedWalletId,
                WALLET_SELECTION_MODE to walletSelectionMode
            )
        }
    }

    override val viewModel: WalletSelectorViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        WalletSelectorScreen(
            state = state,
            onWalletSelected = viewModel::onWalletSelected,
            onBackClicked = viewModel::onBackClicked
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
