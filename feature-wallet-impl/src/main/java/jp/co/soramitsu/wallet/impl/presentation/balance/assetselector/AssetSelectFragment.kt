package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import android.content.DialogInterface
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
class AssetSelectFragment : BaseComposeBottomSheetDialogFragment<AssetSelectViewModel>() {
    companion object {
        const val KEY_SELECTED_ASSET_ID = "KEY_SELECTED_ASSET_ID"
        const val KEY_FILTER_CHAIN_ID = "KEY_FILTER_CHAIN_ID"

        fun getBundle(assetId: String) = bundleOf(KEY_SELECTED_ASSET_ID to assetId)

        fun getBundleFilterByChain(chainId: ChainId) = bundleOf(KEY_FILTER_CHAIN_ID to chainId)

        fun getBundle(chainId: ChainId, assetId: String?) = bundleOf(
            KEY_FILTER_CHAIN_ID to chainId,
            KEY_SELECTED_ASSET_ID to assetId
        )
    }

    override val viewModel: AssetSelectViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        BottomSheetScreen {
            AssetSelectContent(
                state = state,
                callback = viewModel
            )
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.onDialogClose()
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
