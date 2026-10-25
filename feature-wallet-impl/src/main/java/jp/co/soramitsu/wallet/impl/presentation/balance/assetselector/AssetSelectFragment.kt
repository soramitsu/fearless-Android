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
import jp.co.soramitsu.wallet.api.presentation.AssetSelectPurpose

@AndroidEntryPoint
class AssetSelectFragment : BaseComposeBottomSheetDialogFragment<AssetSelectViewModel>() {

    companion object {
        const val KEY_SELECTED_ASSET_ID = "KEY_SELECTED_ASSET_ID"
        const val KEY_SELECTED_CHAIN_ID = "KEY_SELECTED_CHAIN_ID"
        const val KEY_EXCLUDE_ASSET_ID = "KEY_EXCLUDE_ASSET_ID"
        const val KEY_FILTER_CHAIN_ID = "KEY_FILTER_CHAIN_ID"
        const val KEY_IS_FILTER_XCM_ASSETS = "KEY_IS_FILTER_XCM_ASSETS"

        /** [AssetSelectPurpose] name; a bundle without it (older build) opens a Send picker. */
        const val KEY_PURPOSE = "KEY_PURPOSE"

        fun getBundle(
            assetId: String,
            selectedChainId: ChainId? = null,
            purpose: AssetSelectPurpose = AssetSelectPurpose.SEND
        ) = bundleOf(
            KEY_SELECTED_ASSET_ID to assetId,
            KEY_SELECTED_CHAIN_ID to selectedChainId,
            KEY_PURPOSE to purpose.name
        )

        fun getBundleFilterByChain(
            chainId: ChainId,
            purpose: AssetSelectPurpose = AssetSelectPurpose.SEND
        ) = bundleOf(
            KEY_FILTER_CHAIN_ID to chainId,
            KEY_PURPOSE to purpose.name
        )

        fun getBundle(
            chainId: ChainId,
            assetId: String?,
            excludeAssetId: String?,
            purpose: AssetSelectPurpose
        ) = bundleOf(
            KEY_FILTER_CHAIN_ID to chainId,
            KEY_SELECTED_ASSET_ID to assetId,
            KEY_EXCLUDE_ASSET_ID to excludeAssetId,
            KEY_PURPOSE to purpose.name
        )

        fun getBundle(
            chainId: ChainId,
            assetId: String?,
            isFilterXcmAssets: Boolean
        ) = bundleOf(
            KEY_FILTER_CHAIN_ID to chainId,
            KEY_SELECTED_ASSET_ID to assetId,
            KEY_IS_FILTER_XCM_ASSETS to isFilterXcmAssets,
            KEY_PURPOSE to (if (isFilterXcmAssets) AssetSelectPurpose.XCM else AssetSelectPurpose.SEND).name
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
