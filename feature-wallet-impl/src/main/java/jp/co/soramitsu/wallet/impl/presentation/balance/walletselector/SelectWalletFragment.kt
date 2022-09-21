package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class SelectWalletFragment : BaseComposeBottomSheetDialogFragment<SelectWalletViewModel>() {
    override val viewModel: SelectWalletViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        SelectWalletContent(
            state = state,
            onWalletSelected = viewModel::onWalletSelected,
            addNewWallet = viewModel::addNewWallet,
            importWallet = viewModel::importWallet,
            onBackClicked = viewModel::onBackClicked,
            onWalletOptionsClick = viewModel::onWalletOptionsClick
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
