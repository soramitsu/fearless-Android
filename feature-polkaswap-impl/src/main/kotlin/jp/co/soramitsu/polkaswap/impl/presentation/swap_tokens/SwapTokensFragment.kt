package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

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

@AndroidEntryPoint
class SwapTokensFragment : BaseComposeBottomSheetDialogFragment<SwapTokensViewModel>() {

    companion object {

        const val KEY_SELECTED_ASSET_ID = "KEY_SELECTED_ASSET_ID"
        const val KEY_SELECTED_CHAIN_ID = "KEY_SELECTED_CHAIN_ID"

        fun getBundle(selectedAssetId: String, selectedChainId: String) = bundleOf(
            KEY_SELECTED_ASSET_ID to selectedAssetId,
            KEY_SELECTED_CHAIN_ID to selectedChainId
        )
    }

    override val viewModel: SwapTokensViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        BottomSheetScreen {
            val state by viewModel.state.collectAsState()
            SwapTokensContent(
                state = state,
                callbacks = viewModel
            )
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
