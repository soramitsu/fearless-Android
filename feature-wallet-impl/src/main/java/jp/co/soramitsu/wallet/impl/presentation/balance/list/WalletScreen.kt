package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Surface
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.AssetListItem
import jp.co.soramitsu.common.compose.component.AssetListItemShimmer
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.HiddenAssetsItem
import jp.co.soramitsu.common.compose.component.HiddenItemState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssuesBadge
import jp.co.soramitsu.common.compose.component.NftStub
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.SwipeBox
import jp.co.soramitsu.common.compose.component.SwipeBoxViewState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.SelectChainContent
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun WalletScreen(
    viewModel: BalanceListViewModel = hiltViewModel(),
    modalBottomSheetState: ModalBottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
) {
    val state by viewModel.state.collectAsState()
    val shimmerItems by viewModel.assetShimmerItems.collectAsState()
    val chainsState by viewModel.chainsState.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    ModalBottomSheetLayout(
        sheetShape = RoundedCornerShape(topEnd = 24.dp, topStart = 24.dp),
        sheetBackgroundColor = black4,
        sheetState = modalBottomSheetState,
        sheetContent = {
            SelectChainContent(
                state = chainsState,
                onChainSelected = { item ->
                    scope.launch {
                        viewModel.onChainSelected(item)
                        modalBottomSheetState.hide()
                    }
                    keyboardController?.hide()
                },
                onInput = viewModel::onChainSearchEntered
            )
        },
        content = {
            when (state) {
                is LoadingState.Loading<WalletState> -> {
                    ShimmerWalletScreen(shimmerItems)
                }
                is LoadingState.Loaded<WalletState> -> {
                    val data = (state as LoadingState.Loaded<WalletState>).data
                    ContentWalletScreen(viewModel, data)
                }
            }
        },
        scrimColor = Color.Black.copy(alpha = 0.32f) // https://issuetracker.google.com/issues/183697056
    )
}

@Composable
private fun ContentWalletScreen(
    viewModel: BalanceListViewModel,
    data: WalletState
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MarginVertical(margin = 16.dp)
        AssetBalance(
            state = data.balance,
            onAddressClick = { },
            onBalanceClick = { viewModel.onBalanceClicked() }
        )
        if (data.hasNetworkIssues) {
            NetworkIssuesBadge { viewModel.onNetworkIssuesClicked() }
        }
        MarginVertical(margin = 24.dp)
        MultiToggleButton(
            state = data.multiToggleButtonState,
            onToggleChange = viewModel::assetTypeChanged
        )
        MarginVertical(margin = 16.dp)
        if (data.multiToggleButtonState.currentSelection == AssetType.NFTs) {
            NftStub(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            )
        } else {
            AssetsList(data, viewModel)
        }
    }
}

@Composable
private fun AssetsList(
    data: WalletState,
    viewModel: BalanceListViewModel
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(data.visibleAssets) { assetState ->
            SwipableBalanceListItem(viewModel, assetState)
        }
        if (data.hiddenAssets.isNotEmpty()) {
            item {
                HiddenAssetsItem(
                    state = data.hiddenState,
                    onClick = { viewModel.onHiddenAssetClicked() }
                )
            }
            if (data.hiddenState.isExpanded) {
                items(data.hiddenAssets) { assetState ->
                    SwipableBalanceListItem(viewModel, assetState)
                }
            }
        }
        item { MarginVertical(margin = 80.dp) }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipableBalanceListItem(
    viewModel: BalanceListViewModel,
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

@Composable
fun ShimmerWalletScreen(items: List<AssetListItemShimmerViewState>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MarginVertical(margin = 16.dp)
        Shimmer(
            Modifier
                .height(12.dp)
                .padding(horizontal = 124.dp)
        )
        MarginVertical(margin = 10.dp)
        Shimmer(
            Modifier
                .height(26.dp)
                .padding(horizontal = 93.dp)
        )
        MarginVertical(margin = 21.dp)
        Shimmer(
            Modifier
                .height(11.dp)
                .padding(horizontal = 133.dp)
        )

        MarginVertical(margin = 40.dp)
        BackgroundCornered(backgroundColor = MaterialTheme.customColors.white08) {
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .fillMaxWidth()
                    .align(CenterHorizontally)
            ) {
                Shimmer(
                    Modifier
                        .height(12.dp)
                        .weight(1f)
                        .padding(horizontal = 44.dp)
                        .align(CenterVertically)
                )
                Shimmer(
                    Modifier
                        .height(12.dp)
                        .weight(1f)
                        .padding(horizontal = 68.dp)
                        .align(CenterVertically)
                )
            }
        }

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
//            ShimmerWalletScreen(defaultWalletShimmerItems())
            ContentWalletScreen(
                viewModel = hiltViewModel(),
                data = WalletState(
                    multiToggleButtonState = MultiToggleButtonState(AssetType.Currencies, listOf(AssetType.Currencies, AssetType.NFTs)),
                    assets = emptyList(),
                    balance = AssetBalanceViewState("BALANCE", "ADDRESS", true, ChangeBalanceViewState("+100%", "+50$")),
                    hiddenState = HiddenItemState(true),
                    hasNetworkIssues = true
                )
            )
        }
    }
}

private fun defaultWalletShimmerItems(): List<AssetListItemShimmerViewState> = listOf(
    "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
    "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/SORA.svg",
    "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
    "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/kilt.svg",
    "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Bifrost.svg",
    "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg"
).map { iconUrl ->
    AssetListItemShimmerViewState(
        assetIconUrl = iconUrl,
        assetChainUrls = listOf(iconUrl)
    )
}
