package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
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

@OptIn(ExperimentalMaterialApi::class)
@AndroidEntryPoint
class AssetDetailsFragment : BaseComposeFragment<AssetDetailsViewModel>() {

    companion object {
        const val KEY_ASSET_ID = "KEY_ASSET_ID"

        fun getBundle(assetId: String) = bundleOf(KEY_ASSET_ID to assetId)
    }

    override val viewModel: AssetDetailsViewModel by viewModels()


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