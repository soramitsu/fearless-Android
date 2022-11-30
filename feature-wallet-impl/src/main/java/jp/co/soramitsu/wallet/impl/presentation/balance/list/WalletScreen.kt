package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.AssetListItem
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.HiddenAssetsItem
import jp.co.soramitsu.common.compose.component.HiddenItemState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssuesBadge
import jp.co.soramitsu.common.compose.component.NftStub
import jp.co.soramitsu.common.compose.component.SwipeBox
import jp.co.soramitsu.common.compose.component.SwipeBoxViewState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import kotlinx.coroutines.launch

interface WalletScreenInterface {
    fun onBalanceClicked()
    fun onNetworkIssuesClicked()
    fun assetTypeChanged(type: AssetType)

    @OptIn(ExperimentalMaterialApi::class)
    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>)
    fun assetClicked(asset: AssetListItemViewState)
    fun onHiddenAssetClicked()
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WalletScreen(
    data: WalletState,
    callback: WalletScreenInterface
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MarginVertical(margin = 16.dp)
        AssetBalance(
            state = data.balance,
            onAddressClick = emptyClick,
            onBalanceClick = callback::onBalanceClicked
        )
        if (data.hasNetworkIssues) {
            NetworkIssuesBadge(onClick = callback::onNetworkIssuesClicked)
        }
        MarginVertical(margin = 24.dp)
        MultiToggleButton(
            state = data.multiToggleButtonState,
            onToggleChange = callback::assetTypeChanged
        )
        MarginVertical(margin = 16.dp)
        if (data.multiToggleButtonState.currentSelection == AssetType.NFTs) {
            NftStub(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            )
        } else {
            AssetsList(
                data = data,
                assetClicked = callback::assetClicked,
                actionItemClicked = callback::actionItemClicked,
                onHiddenAssetClicked = callback::onHiddenAssetClicked
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AssetsList(
    data: WalletState,
    assetClicked: (AssetListItemViewState) -> Unit,
    actionItemClicked: (actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) -> Unit,
    onHiddenAssetClicked: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(
        key1 = data.assets.size,
        block = {
            launch {
                listState.scrollToItem(0)
            }
        }
    )
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(data.visibleAssets, key = { it.displayName }) { assetState ->
            SwipeableBalanceListItem(
                assetState = assetState,
                assetClicked = assetClicked,
                actionItemClicked = actionItemClicked
            )
        }
        if (data.hiddenAssets.isNotEmpty()) {
            item {
                HiddenAssetsItem(
                    state = data.hiddenState,
                    onClick = onHiddenAssetClicked
                )
            }
            if (data.hiddenState.isExpanded) {
                items(data.hiddenAssets) { assetState ->
                    SwipeableBalanceListItem(
                        assetState = assetState,
                        assetClicked = assetClicked,
                        actionItemClicked = actionItemClicked
                    )
                }
            }
        }
        item { MarginVertical(margin = 80.dp) }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipeableBalanceListItem(
    assetState: AssetListItemViewState,
    assetClicked: (AssetListItemViewState) -> Unit,
    actionItemClicked: (actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) -> Unit
) {
    val swipeableState = rememberSwipeableState(initialValue = SwipeState.INITIAL)

    fun onItemClick(actionType: ActionItemType, chainId: ChainId, chainAssetId: String) {
        actionItemClicked(actionType, chainId, chainAssetId, swipeableState)
    }

    SwipeBox(
        swipeableState = swipeableState,
        state = SwipeBoxViewState(
            leftStateWidth = 170.dp,
            rightStateWidth = 90.dp
        ),
        initialContent = {
            AssetListItem(
                state = assetState,
                onClick = assetClicked
            )
        },
        leftContent = {
            ActionBar(
                state = getLeftActionBarViewState(assetState),
                onItemClick = ::onItemClick
            )
        },
        rightContent = {
            ActionBar(
                state = getRightActionBarViewState(assetState),
                onItemClick = ::onItemClick
            )
        }
    )
}

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

private fun getLeftActionBarViewState(asset: AssetListItemViewState) = ActionBarViewState(
    chainId = asset.chainId,
    chainAssetId = asset.chainAssetId,
    actionItems = listOf(
        ActionItemType.SEND,
        ActionItemType.RECEIVE
    )
)

@Preview
@Composable
private fun PreviewWalletScreen() {
    @OptIn(ExperimentalMaterialApi::class)
    val emptyCallback = object : WalletScreenInterface {
        override fun onBalanceClicked() {}
        override fun onNetworkIssuesClicked() {}
        override fun assetTypeChanged(type: AssetType) {}
        override fun assetClicked(asset: AssetListItemViewState) {}
        override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {}
        override fun onHiddenAssetClicked() {}
    }

    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            Column {
                WalletScreen(
                    data = WalletState(
                        multiToggleButtonState = MultiToggleButtonState(AssetType.Currencies, listOf(AssetType.Currencies, AssetType.NFTs)),
                        assets = emptyList(),
                        balance = AssetBalanceViewState("BALANCE", "ADDRESS", true, ChangeBalanceViewState("+100%", "+50$")),
                        hiddenState = HiddenItemState(true),
                        hasNetworkIssues = true
                    ),
                    callback = emptyCallback
                )
            }
        }
    }
}
