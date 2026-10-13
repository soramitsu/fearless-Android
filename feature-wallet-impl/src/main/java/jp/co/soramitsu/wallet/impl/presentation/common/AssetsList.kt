package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.AssetListItem
import jp.co.soramitsu.common.compose.component.AssetListItemShimmer
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletAssetsState
import jp.co.soramitsu.common.R as CommonR

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
                AssetListShimmer(header = header)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    if (header != null) item { header() }
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
    var collapsedNetworkIds by remember { mutableStateOf(emptySet<String>()) }
    
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
        if (data.assets.isEmpty() && LocalDensity.current.fontScale > 1.3f) {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (header != null) item { header() }
                item { EmptyAssetsContent(allAssetsHidden = data.allAssetsHidden) }
                if (footer != null) item { footer() }
                item { MarginVertical(80.dp) }
            }
        } else if (data.assets.isEmpty()) {
            Column {
                MarginVertical(margin = 8.dp)
                header?.invoke()
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyAssetsContent(allAssetsHidden = data.allAssetsHidden)
                }
                footer?.invoke()
                MarginVertical(margin = 80.dp)
            }
        } else {
            val networkSections = data.assets.groupBy(AssetListItemViewState::chainId).values

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                if (header != null) {
                    item { header() }
                }

                networkSections.forEach { networkAssets ->
                    val network = networkAssets.first()
                    val isExpanded = network.chainId !in collapsedNetworkIds

                    item(key = "network-${network.chainId}") {
                        PortfolioNetworkHeader(
                            network = network,
                            isExpanded = isExpanded,
                            onToggle = {
                                collapsedNetworkIds = if (isExpanded) {
                                    collapsedNetworkIds + network.chainId
                                } else {
                                    collapsedNetworkIds - network.chainId
                                }
                            }
                        )
                    }

                    if (isExpanded) {
                        val verifiedAssets = networkAssets.filterNot(AssetListItemViewState::isDetected)
                        val detectedAssets = networkAssets.filter(AssetListItemViewState::isDetected)

                        items(verifiedAssets, key = { "${it.key}$isHideVisible" }) { assetState ->
                            SwipeableAssetListItem(
                                assetState = assetState,
                                isHideVisible = isHideVisible,
                                assetClicked = callback::assetClicked,
                                actionItemClicked = callback::actionItemClicked
                            )
                        }

                        if (detectedAssets.isNotEmpty()) {
                            item(key = "detected-${network.chainId}") {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    B1(
                                        text = stringResource(
                                            CommonR.string.portfolio_detected_assets,
                                            detectedAssets.size
                                        )
                                    )
                                    B2(
                                        text = stringResource(CommonR.string.portfolio_detected_assets_description),
                                        color = white50
                                    )
                                }
                            }
                            items(detectedAssets, key = { "detected-${it.key}$isHideVisible" }) { assetState ->
                                DetectedAssetReviewItem(
                                    assetState = assetState,
                                    callback = callback
                                )
                            }
                        }
                    }
                }
                if (footer != null) {
                    item { footer() }
                }
                item { MarginVertical(margin = 80.dp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DetectedAssetReviewItem(
    assetState: AssetListItemViewState,
    callback: AssetsListInterface
) {
    val actionState = rememberSwipeableState(initialValue = SwipeState.INITIAL)
    val actions = remember(assetState.chainId, assetState.chainAssetId) {
        ActionBarViewState(
            chainId = assetState.chainId,
            chainAssetId = assetState.chainAssetId,
            actionItems = listOf(ActionItemType.SHOW, ActionItemType.HIDE)
        )
    }

    Column {
        AssetListItem(state = assetState, onClick = callback::assetClicked)
        ActionBar(
            state = actions,
            fillMaxWidth = true,
            onItemClick = { action, chainId, assetId ->
                callback.actionItemClicked(action, chainId, assetId, actionState)
            }
        )
    }
}

@Composable
private fun PortfolioNetworkHeader(
    network: AssetListItemViewState,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val subtotal = network.networkFiatSubtotal?.takeIf(String::isNotBlank)
            if (LocalDensity.current.fontScale > 1.3f) {
                H3(text = network.assetChainName)
                subtotal?.let { B1(text = it) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    H3(text = network.assetChainName, modifier = Modifier.weight(1f))
                    subtotal?.let {
                        Spacer(modifier = Modifier.width(12.dp))
                        B1(text = it)
                    }
                }
            }
            networkSyncLabel(network)?.let { label ->
                B2(
                    text = label,
                    color = if (network.networkIsStale || !network.networkSyncError.isNullOrBlank()) alertYellow else white50
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(
                if (isExpanded) CommonR.drawable.ic_chevron_up_white
                else CommonR.drawable.ic_chevron_down_white
            ),
            contentDescription = stringResource(
                if (isExpanded) CommonR.string.common_collapse
                else CommonR.string.common_expand
            ),
            tint = white64
        )
    }
}

@Composable
private fun networkSyncLabel(network: AssetListItemViewState): String? {
    return when (portfolioNetworkStatus(
        lastSuccessMillis = network.networkLastSuccessMillis,
        isStale = network.networkIsStale,
        errorMessage = network.networkSyncError
    )) {
        PortfolioNetworkStatus.Failed -> stringResource(CommonR.string.portfolio_balance_update_failed)
        PortfolioNetworkStatus.Outdated -> stringResource(CommonR.string.portfolio_balances_outdated)
        PortfolioNetworkStatus.NotLoaded -> stringResource(CommonR.string.portfolio_balances_not_loaded)
        null -> null
    }
}

@Composable
fun EmptyAssetsContent(allAssetsHidden: Boolean = false) {
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
            text = stringResource(
                id = if (allAssetsHidden) R.string.wallet_all_assets_hidden else R.string.common_search_assets_alert_description
            ),
            color = white50
        )
    }
}

@Composable
fun AssetListShimmer(
    itemsCount: Int = 6,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp)
    ) {
        if (header != null) item { header() }
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
