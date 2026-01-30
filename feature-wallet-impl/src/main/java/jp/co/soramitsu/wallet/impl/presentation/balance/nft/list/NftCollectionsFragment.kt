package jp.co.soramitsu.wallet.impl.presentation.balance.nft.list

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.balance.list.BalanceListViewModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletAssetsState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.common.NetworkIssue

@AndroidEntryPoint
class NftCollectionsFragment : BaseComposeFragment<BalanceListViewModel>() {

    override val viewModel: BalanceListViewModel by activityViewModels()

    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state by viewModel.state.collectAsState()

        when (val assetsState = state.assetsState) {
            is WalletAssetsState.NftAssets -> {
                Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
                    NFTScreen(collectionsScreen = assetsState.collectionScreenModel)
                }
            }

            is WalletAssetsState.NetworkIssue -> {
                Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
                    NetworkIssue(
                        retryButtonLoading = assetsState.retryButtonLoading,
                        onRetry = viewModel::onRetry
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) {
        Toolbar(
            state = ToolbarViewState(
                title = stringResource(id = R.string.tabbar_nft_title)
            )
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.assetTypeChanged(AssetType.NFTs)
        viewModel.onResume()
    }
}
