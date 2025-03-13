package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetListItemShimmer
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletAssetsState

interface AssetsListInterface {
    @OptIn(ExperimentalMaterialApi::class)
    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>)
    fun assetClicked(state: AssetListItemViewState)
}

@Composable
fun AssetsList(
    data: AssetListState,
    callback: AssetsListInterface,
    listState: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    when (val state = data.assets) {
        is AssetsLoadingState.Loading -> {
            if (state.shimmerStates.isEmpty()) {
                AssetListShimmer()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    items(state.shimmerStates) { shimmerState ->
                        AssetListItemShimmer(
                            state = shimmerState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { MarginVertical(margin = 80.dp) }
                }
            }
        }
        is AssetsLoadingState.Loaded -> {
            AssetsList(
                data = state,
                isHideVisible = (data as? WalletAssetsState.Assets)?.isHideVisible == true,
                callback = callback,
                listState = listState,
                header = header,
                footer = footer
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AssetsList(
    data: AssetsLoadingState.Loaded,
    isHideVisible: Boolean,
    callback: AssetsListInterface,
    listState: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }

    Column(
        modifier = Modifier.alpha(alpha.value)
    ) {
        if (data.assets.isEmpty()) {
            Column {
                MarginVertical(margin = 8.dp)
                header?.invoke()
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyAssetsContent()
                }
                footer?.invoke()
                MarginVertical(margin = 80.dp)
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                if (header != null) {
                    item { header() }
                }
                items(data.assets, key = { "${it.key}$isHideVisible" }) { assetState ->
                    SwipeableAssetListItem(
                        assetState = assetState,
                        isHideVisible = isHideVisible,
                        assetClicked = callback::assetClicked,
                        actionItemClicked = callback::actionItemClicked
                    )
                }
                if (footer != null) {
                    item { footer() }
                }
                item { MarginVertical(margin = 80.dp) }
            }
        }
    }
}

@Composable
fun EmptyAssetsContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )

        H3(text = stringResource(id = R.string.common_search_assets_alert_title))
        B0(
            text = stringResource(id = R.string.wallet_all_assets_hidden),
            color = white50
        )
    }
}

@Composable
fun AssetListShimmer(
    itemsCount: Int = 6,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp)
    ) {
        items(itemsCount) {
            AssetListItemShimmer(
                state = AssetListItemShimmerViewState(
                    assetIconUrl = "",
                    assetChainUrls = List(3) { "" }
                )
            )
        }
        item { MarginVertical(margin = 80.dp) }
    }
}

@Composable
fun AssetsList(
    shimmerStates: List<AssetListItemShimmerViewState>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(shimmerStates) { shimmerState ->
            AssetListItemShimmer(
                state = shimmerState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
