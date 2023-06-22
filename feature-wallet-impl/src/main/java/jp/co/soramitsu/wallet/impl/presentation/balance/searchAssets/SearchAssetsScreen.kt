package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.EmptyMessage
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white30
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsList
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsListInterface

interface SearchAssetsScreenInterface : AssetsListInterface {
    fun onAssetSearchEntered(value: String)
    fun backClicked()
}

@Composable
fun SearchAssetsScreen(
    data: SearchAssetState?,
    callback: SearchAssetsScreenInterface
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarginVertical(margin = 16.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
            ) {
                CorneredInput(
                    state = data?.searchQuery,
                    borderColor = white30,
                    hintLabel = stringResource(id = R.string.manage_assets_search_hint),
                    onInput = callback::onAssetSearchEntered
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .wrapContentWidth()
                    .semantics { role = Role.Button }
                    .clickable(onClick = callback::backClicked)
            ) {
                Text(
                    text = stringResource(id = R.string.common_cancel),
                    style = MaterialTheme.customTypography.header4,
                    maxLines = 1
                )
            }
        }
        MarginVertical(margin = 16.dp)
        when {
            data?.assets == null -> {}
            data.assets.isEmpty() -> {
                MarginVertical(margin = 16.dp)
                EmptyMessage(message = R.string.common_search_assets_alert_description)
            }

            else -> {
                AssetsList(data, callback)
            }
        }
    }
}

@ExperimentalMaterialApi
@Preview
@Composable
private fun PreviewWalletScreen() {
    val empty = object : SearchAssetsScreenInterface {
        override fun onAssetSearchEntered(value: String) {}
        override fun backClicked() {}
        override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {}
        override fun assetClicked(state: AssetListItemViewState) {}
    }
    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            SearchAssetsScreen(null, empty)
        }
    }
}
