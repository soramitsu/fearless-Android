package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.BannerJoinSubstrateEvm
import jp.co.soramitsu.common.compose.component.BannerJoinTon
import jp.co.soramitsu.common.compose.component.BannerWalletRecovery
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.utils.rememberForeverLazyListState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.NFTScreen
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsList
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsListInterface
import jp.co.soramitsu.wallet.impl.presentation.common.NetworkIssue

@Stable
interface WalletScreenInterface : AssetsListInterface {
    fun onAddressClick()
    fun onBalanceClicked()
    fun onBackupClicked()
    fun onBackupCloseClick()
    fun onJoinSubOrEvmClicked()
    fun onJoinSubOrEvmCloseClick()
    fun onJoinTonClicked()
    fun onJoinTonCloseClick()
    fun assetTypeChanged(type: AssetType)
    fun onRefresh()
    fun onManageAssetClick()
    fun onRetry()
}

@Composable
fun WalletScreen(
    data: WalletState,
    callback: WalletScreenInterface
) {
    val listState = rememberForeverLazyListState("wallet_screen")

    val scale = remember { Animatable(initialValue = 1f) }

    LaunchedEffect(data.scrollToTopEvent) {
        data.scrollToTopEvent?.getContentIfNotHandled()?.let {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(data.scrollToBottomEvent) {
        data.scrollToBottomEvent?.getContentIfNotHandled()?.let {
            if (data.assetsState is WalletAssetsState.Assets && data.assetsState.assets is AssetsLoadingState.Loaded) {

                val items = data.assetsState.assets.assets.size + listOf("header", "footer").size
                val lastItemIndex = items - 1
                listState.animateScrollToItem(lastItemIndex)

                scale.animateTo(
                    targetValue = 1.2f,
                    animationSpec = tween(durationMillis = 600)
                )
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 600)
                )
            }
        }
    }


    val largeText = LocalDensity.current.fontScale > 1.3f
    val scrollHeaderWithAssets = largeText && data.assetsState is WalletAssetsState.Assets
    val walletHeader: @Composable () -> Unit = {
        Column {
            MarginVertical(margin = 16.dp)
            AssetBalance(
                state = data.balance,
                onAddressClick = callback::onAddressClick,
                onBalanceClick = callback::onBalanceClicked
            )

            MarginVertical(margin = 16.dp)
            when {
                data.isRecoveryRequired -> BannerWalletRecovery()
                !data.isBackedUp -> BackgroundCornered(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        B1(text = stringResource(R.string.ux_backup_description))
                        MarginVertical(8.dp)
                        GrayButton(
                            text = stringResource(R.string.ux_backup_status),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = callback::onBackupClicked
                        )
                    }
                }
            }
            MarginVertical(8.dp)
            AnimatedVisibility(
                visible = data.showCurrenciesOrNftSelector,
                enter = fadeIn(),
                exit = fadeOut()
            ) {

                MultiToggleButton(
                    state = data.multiToggleButtonState,
                    onToggleChange = callback::assetTypeChanged,
                    stacked = largeText
                )
            }

        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (!scrollHeaderWithAssets && !(largeText && data.assetsState is WalletAssetsState.NetworkIssue)) walletHeader()
        when (data.assetsState) {
            is WalletAssetsState.NftAssets -> {
                NFTScreen(collectionsScreen = data.assetsState.collectionScreenModel)
            }

            is WalletAssetsState.Assets -> {
                val header: @Composable () -> Unit = { if (scrollHeaderWithAssets) walletHeader() }
                val footer: @Composable () -> Unit =
                    {
                        WalletScreenFooter(scale.value, callback::onManageAssetClick)
                        MarginVertical(16.dp)
                        Banners(data, callback)
                    }
                AssetsList(
                    data = data.assetsState,
                    callback = callback,
                    header = header,
                    listState = listState,
                    footer = footer
                )
            }

            is WalletAssetsState.NetworkIssue -> {
                if (largeText) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        walletHeader()
                        NetworkIssue(data.assetsState.retryButtonLoading, callback::onRetry)
                    }
                } else {
                    NetworkIssue(data.assetsState.retryButtonLoading, callback::onRetry)
                }
            }
        }
    }
}

@Composable
private fun Banners(
    data: WalletState,
    callback: WalletScreenInterface
) {
    // Optional network discovery stays below assets, separate from wallet protection.
    if (!data.hasSubOrEvmAccounts) {
        BannerJoinSubstrateEvm(
            onClick = callback::onJoinSubOrEvmClicked,
            onCloseClick = callback::onJoinSubOrEvmCloseClick
        )
        MarginVertical(8.dp)
    }
    if (!data.hasTonAccounts) {
        BannerJoinTon(
            onClick = callback::onJoinTonClicked,
            onCloseClick = callback::onJoinTonCloseClick
        )
        MarginVertical(8.dp)
    }
}

@Composable
fun WalletScreenWithRefresh(
    data: WalletState,
    callback: WalletScreenInterface
) {
    PullRefreshBox(
        onRefresh = callback::onRefresh
    ) {
        WalletScreen(data, callback)
    }
}

@Composable
fun WalletScreenFooter(
    scale: Float,
    onManageAssetsClick: () -> Unit
) {
    GrayButton(
        text = stringResource(id = R.string.wallet_manage_assets),
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 300,
                    easing = LinearOutSlowInEasing
                )
            )
            .height(48.dp),
        onClick = onManageAssetsClick
    )
}

@Preview
@Composable
private fun PreviewWalletScreen() {
    @OptIn(ExperimentalMaterialApi::class)
    val emptyCallback = object : WalletScreenInterface {
        override fun onAddressClick() {}
        override fun onBalanceClicked() {}
        override fun onBackupClicked() {}
        override fun onBackupCloseClick() {}
        override fun onJoinSubOrEvmClicked() {}
        override fun onJoinSubOrEvmCloseClick() {}
        override fun onJoinTonClicked() {}
        override fun onJoinTonCloseClick() {}
        override fun assetTypeChanged(type: AssetType) {}
        override fun assetClicked(state: AssetListItemViewState) {}
        override fun actionItemClicked(
            actionType: ActionItemType,
            chainId: ChainId,
            chainAssetId: String,
            swipeableState: SwipeableState<SwipeState>
        ) {
        }

        override fun onRefresh() {}
        override fun onManageAssetClick() {}
        override fun onRetry() {}
    }

    val element = AssetListItemViewState(
        index = 0,
        assetIconUrl = "",
        assetChainName = "Chain",
        assetSymbol = "SMB",
        assetName = "Sora Asset",
        assetTokenFiat = null,
        assetTokenRate = null,
        assetTransferableBalance = null,
        assetTransferableBalanceFiat = null,
        assetChainUrls = emptyMap(),
        chainId = "",
        chainAssetId = "",
        isSupported = true,
        isHidden = false,
        isTestnet = false
    )
    val assets: List<AssetListItemViewState> = listOf(
        element, element, element.copy(isHidden = true)
    ).mapIndexed { index, assetListItemViewState ->
        assetListItemViewState.copy(index = index)
    }

    FearlessAppTheme(true) {
        Column {
            WalletScreen(
                data = WalletState(
                    multiToggleButtonState = MultiToggleButtonState(
                        AssetType.Currencies,
                        listOf(AssetType.Currencies, AssetType.NFTs)
                    ),
                    assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loaded(assets), isHideVisible = true),
                    balance = AssetBalanceViewState(
                        "TRANSFERABLE BALANCE",
                        "ADDRESS",
                        true,
                        ChangeBalanceViewState("+100%", "+50$")
                    ),
                    hasNetworkIssues = true,
                    isBackedUp = false,
                    isRecoveryRequired = false,
                    hasTonAccounts = false,
                    hasSubOrEvmAccounts = false,
                    showCurrenciesOrNftSelector = false,
                    scrollToTopEvent = null,
                    scrollToBottomEvent = null
                ),
                callback = emptyCallback
            )
        }
    }
}
