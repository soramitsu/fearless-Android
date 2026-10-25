package jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.MainToolbar
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload

@AndroidEntryPoint
class LegacyCrowdloanFragment : BaseComposeFragment<LegacyCrowdloanViewModel>() {

    companion object {
        const val KEY_ASSET_PAYLOAD = "legacy_crowdloan_asset_payload"

        fun getBundle(assetPayload: AssetPayload) = bundleOf(KEY_ASSET_PAYLOAD to assetPayload)
    }

    override val viewModel: LegacyCrowdloanViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) {
        val toolbarState by viewModel.toolbarState.collectAsStateWithLifecycle()
        MainToolbar(
            state = toolbarState,
            onChangeChainClick = null,
            onNavigationClick = viewModel::backClicked
        )
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LegacyCrowdloanContent(
            state = state,
            onOpenLockedBalance = viewModel::openLockedBalance
        )
    }

    @Composable
    override fun Background() {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(id = R.drawable.drawable_background_image),
                contentScale = ContentScale.FillBounds,
                contentDescription = null
            )
        }
    }
}
