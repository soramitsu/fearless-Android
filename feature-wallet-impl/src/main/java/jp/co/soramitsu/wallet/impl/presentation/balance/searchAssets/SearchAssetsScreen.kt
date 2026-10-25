package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.EmptyMessage
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white30
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.common.AssetListShimmer
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsList
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsListInterface
import jp.co.soramitsu.wallet.impl.presentation.common.SwipeableAssetListItem

interface SearchAssetsScreenInterface : AssetsListInterface {
    fun onAssetSearchEntered(value: String)
    fun backClicked()
}

@Composable
fun SearchAssetsScreen(
    data: SearchAssetState,
    callback: SearchAssetsScreenInterface
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarginVertical(margin = 16.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f)
            ) {
                CorneredInput(
                    state = data.searchQuery,
                    borderColor = white30,
                    hintLabel = stringResource(id = R.string.search_assets_hint),
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

        when (val assets = data.assets) {
            is AssetsLoadingState.Loading -> {
                if (assets.shimmerStates.isEmpty()) {
                    AssetListShimmer()
                } else {
                    AssetsList(
                        shimmerStates = assets.shimmerStates,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            is AssetsLoadingState.Loaded -> {
                if (data.hasResults.not()) {
                    MarginVertical(margin = 16.dp)
                    EmptyMessage(message = R.string.common_search_network_and_assets_alert_description)
                } else {
                    SearchResults(data, callback)
                }
            }
        }
    }
}

/** One list, section by section; a section without matches is not shown at all. */
@Composable
private fun SearchResults(
    data: SearchAssetState,
    callback: SearchAssetsScreenInterface
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp)
    ) {
        searchSection(
            key = "portfolio",
            title = R.string.search_assets_section_portfolio,
            assets = data.portfolioAssets,
            callback = callback
        )
        searchSection(
            key = "hidden",
            title = R.string.hidden_assets,
            assets = data.hiddenAssets,
            callback = callback
        )
        // Not in the portfolio: the action behind the row shows the asset there.
        searchSection(
            key = "other",
            title = R.string.search_assets_section_other,
            description = R.string.search_assets_section_other_description,
            assets = data.otherAssets,
            callback = callback,
            visibilityAction = ActionItemType.SHOW
        )
        item { MarginVertical(margin = 80.dp) }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Suppress("LongParameterList")
private fun LazyListScope.searchSection(
    key: String,
    @StringRes title: Int,
    assets: List<AssetListItemViewState>,
    callback: SearchAssetsScreenInterface,
    @StringRes description: Int? = null,
    visibilityAction: ActionItemType? = null
) {
    if (assets.isEmpty()) return

    item(key = "section-$key") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("SearchAssetsSection_$key")
                .semantics { heading() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            B1(text = stringResource(title))
            description?.let {
                B2(text = stringResource(it), color = white50)
            }
        }
    }
    // Rows are network-scoped, so Hide and Show act on exactly the row's network.
    items(assets, key = { "$key-${it.key}" }) { assetState ->
        SwipeableAssetListItem(
            assetState = assetState,
            isHideVisible = true,
            assetClicked = callback::assetClicked,
            actionItemClicked = callback::actionItemClicked,
            visibilityAction = visibilityAction
                ?: if (assetState.isHidden) ActionItemType.SHOW else ActionItemType.HIDE
        )
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
            SearchAssetsScreen(SearchAssetState(), empty)
        }
    }
}
