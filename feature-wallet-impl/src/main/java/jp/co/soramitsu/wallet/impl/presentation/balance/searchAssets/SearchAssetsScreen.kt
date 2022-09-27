package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.rememberSwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetListItem
import jp.co.soramitsu.common.compose.component.AssetListItemShimmer
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.SwipeBox
import jp.co.soramitsu.common.compose.component.SwipeBoxViewState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.white30
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R

@Composable
fun SearchAssetsScreen(
    viewModel: SearchAssetsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val shimmerItems by viewModel.assetShimmerItems.collectAsState()

    when (state) {
        is LoadingState.Loading<SearchAssetState> -> {
            ShimmerSearchAssetsScreen(shimmerItems)
        }
        is LoadingState.Loaded<SearchAssetState> -> {
            val data = (state as LoadingState.Loaded<SearchAssetState>).data
            ContentSearchAssetsScreen(
                viewModel = viewModel,
                data = data,
                onInput = viewModel::onAssetSearchEntered,
                onNavigationClick = viewModel::backClicked
            )
        }
    }
}

@Composable
private fun ContentSearchAssetsScreen(
    viewModel: SearchAssetsViewModel,
    data: SearchAssetState,
    onInput: (String) -> Unit,
    onNavigationClick: () -> Unit
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
                    .weight(5f)
            ) {
                CorneredInput(
                    state = data.searchQuery,
                    onInput = onInput,
                    borderColor = white30
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.Button }
                    .clickable {
                        onNavigationClick()
                    }
            ) {
                Text(
                    text = stringResource(id = R.string.common_cancel),
                    style = MaterialTheme.customTypography.header4,
                    maxLines = 1
                )
            }
        }
        MarginVertical(margin = 16.dp)
        if (data.assets.isNotEmpty()) {
            AssetsList(data, viewModel)
        } else {
            AssetListEmpty()
        }
    }
}

@Composable
private fun AssetsList(
    data: SearchAssetState,
    viewModel: SearchAssetsViewModel
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(data.assets) { assetState ->
            SwipableAssetListItem(viewModel, assetState)
        }
        item { MarginVertical(margin = 80.dp) }
    }
}

@Composable
private fun AssetListEmpty() {
    MarginVertical(margin = 16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_alert),
            contentDescription = null
        )
        H3(text = stringResource(id = R.string.common_search_assets_alert_title))
        B0(
            text = stringResource(id = R.string.common_search_assets_alert_description),
            color = gray2
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipableAssetListItem(
    viewModel: SearchAssetsViewModel,
    assetState: AssetListItemViewState
) {
    val swipeableState = rememberSwipeableState(initialValue = SwipeState.INITIAL)
    SwipeBox(
        swipeableState = swipeableState,
        state = SwipeBoxViewState(
            leftStateWidth = 180.dp,
            rightStateWidth = 90.dp
        ),
        initialContent = {
            AssetListItem(
                state = assetState,
                onClick = viewModel::assetClicked
            )
        },
        leftContent = {
            ActionBar(
                state = getLeftActionBarViewState(assetState)
            ) { actionType, chainId, chainAssetId ->
                viewModel.actionItemClicked(actionType, chainId, chainAssetId, swipeableState)
            }
        },
        rightContent = {
            ActionBar(
                state = getRightActionBarViewState(assetState)
            ) { actionType, chainId, chainAssetId ->
                viewModel.actionItemClicked(actionType, chainId, chainAssetId, swipeableState)
            }
        }
    )
}

private fun getLeftActionBarViewState(asset: AssetListItemViewState) = ActionBarViewState(
    chainId = asset.chainId,
    chainAssetId = asset.chainAssetId,
    actionItems = listOf(
        ActionItemType.SEND,
        ActionItemType.RECEIVE
    )
)

private fun getRightActionBarViewState(asset: AssetListItemViewState) = ActionBarViewState(
    chainId = asset.chainId,
    chainAssetId = asset.chainAssetId,
    actionItems = listOf(
        when {
            asset.isHidden -> ActionItemType.SHOW
            else -> ActionItemType.HIDE
        }
    )
)

@Composable
fun ShimmerSearchAssetsScreen(items: List<AssetListItemShimmerViewState>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MarginVertical(margin = 16.dp)
        Shimmer(
            Modifier.height(26.dp)
        )
        MarginVertical(margin = 16.dp)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { asset ->
                AssetListItemShimmer(asset)
            }
            item { MarginVertical(margin = 80.dp) }
        }
    }
}

@Preview
@Composable
private fun PreviewWalletScreen() {
    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            SearchAssetsScreen()
        }
    }
}
