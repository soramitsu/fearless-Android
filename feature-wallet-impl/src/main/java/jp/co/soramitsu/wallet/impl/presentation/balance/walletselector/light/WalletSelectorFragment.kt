package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light

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
import jp.co.soramitsu.common.compose.component.WalletSelectorLight

@AndroidEntryPoint
class WalletSelectorFragment : BaseComposeBottomSheetDialogFragment<WalletSelectorViewModel>() {

    companion object {
        const val TAG_ARGUMENT_KEY = "tag"
        fun buildArguments(tag: String) = bundleOf(TAG_ARGUMENT_KEY to tag)
    }

    override val viewModel: WalletSelectorViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        WalletSelectorLight(
            state = state,
            onWalletSelected = viewModel::onWalletSelected,
            onBackClicked = viewModel::onBackClicked
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
