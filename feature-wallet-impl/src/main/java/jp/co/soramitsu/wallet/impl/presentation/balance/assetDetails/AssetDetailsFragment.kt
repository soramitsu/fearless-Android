package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload

/**
 * The old "networks of this asset" screen. It stays in the navigation graph so a restored back
 * stack still resolves, but nothing opens it: Search and the portfolio list open the exact
 * (network, asset) detail.
 */
@Deprecated("Open the exact network-scoped asset with WalletRouter.openAssetDetails")
@AndroidEntryPoint
class AssetDetailsFragment : BaseComposeFragment<AssetDetailsViewModel>() {

    companion object {
        const val KEY_ASSET_PAYLOAD = "KEY_ASSET_PAYLOAD"

        fun getBundle(assetPayload: AssetPayload) = bundleOf(KEY_ASSET_PAYLOAD to assetPayload)
    }

    override val viewModel: AssetDetailsViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.onLegacyDestinationResumed()
    }


    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) {
        super.Toolbar(modalBottomSheetState)
        val state = viewModel.toolbarState.collectAsState()

        AssetDetailsToolbar(
            state = state.value,
            callback = viewModel
        )
    }

    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state = viewModel.contentState.collectAsState()

        AssetDetailsContent(
            state = state.value,
            callback = viewModel
        )
    }

    @Composable
    override fun Background() {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(id = R.drawable.drawable_background_image),
                contentScale = ContentScale.FillBounds,
                contentDescription = null
            )
        }
    }

}
