package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

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
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@AndroidEntryPoint
class ChainSelectFragment : BaseComposeBottomSheetDialogFragment<ChainSelectViewModel>() {
    companion object {
        const val KEY_SELECTED_CHAIN_ID = "KEY_SELECTED_CHAIN_ID"
        const val KEY_SELECTED_ASSET_ID = "KEY_SELECTED_ASSET_ID"

        fun getBundle(assetId: String, chainId: ChainId? = null) = bundleOf(
            KEY_SELECTED_ASSET_ID to assetId,
            KEY_SELECTED_CHAIN_ID to chainId
        )
    }

    override val viewModel: ChainSelectViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        BottomSheetScreen {
            ChainSelectContent(
                state = state,
                onChainSelected = viewModel::onChainSelected,
                onSearchInput = viewModel::onSearchInput
            )
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
