package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.BannerBackup
import jp.co.soramitsu.common.compose.component.BannerJoinSubstrateEvm
import jp.co.soramitsu.common.compose.component.BannerJoinTon
import jp.co.soramitsu.common.compose.component.BannerPageIndicator
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
import kotlinx.coroutines.delay

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


    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MarginVertical(margin = 16.dp)
        AssetBalance(
            state = data.balance,
            onAddressClick = callback::onAddressClick,
            onBalanceClick = callback::onBalanceClicked
        )
        if (data.showCurrenciesOrNftSelector) {
            MarginVertical(margin = 16.dp)
            MultiToggleButton(
                state = data.multiToggleButtonState,
                onToggleChange = callback::assetTypeChanged
            )
        }

        when (data.assetsState) {
            is WalletAssetsState.NftAssets -> {
                NFTScreen(collectionsScreen = data.assetsState.collectionScreenModel)
            }

            is WalletAssetsState.Assets -> {
                val header: @Composable () -> Unit = { Banners(data, callback) }
                val footer: @Composable () -> Unit =
                    { WalletScreenFooter(scale.value, callback::onManageAssetClick) }
                AssetsList(
                    data = data.assetsState,
                    callback = callback,
                    header = header,
                    listState = listState,
                    footer = footer
                )
            }

            is WalletAssetsState.NetworkIssue -> {
                NetworkIssue(data.assetsState.retryButtonLoading, callback::onRetry)
            }
        }
    }
}

@Composable
private fun Banners(
    data: WalletState,
    callback: WalletScreenInterface,
    autoPlay: Boolean = true
) {
    val backupBanner: @Composable (() -> Unit)? = if (!data.isBackedUp) {
        {
            BannerBackup(
                onBackupClick = callback::onBackupClicked,
                onCloseClick = callback::onBackupCloseClick,
            )
        }
    } else {
        null
    }

    val joinSubOrEvmBanner: @Composable (() -> Unit)? = if (!data.hasSubOrEvmAccounts) {
        {
            BannerJoinSubstrateEvm(
                onClick = callback::onJoinSubOrEvmClicked,
                onCloseClick = callback::onJoinSubOrEvmCloseClick,
            )
        }
    } else {
        null
    }

    val joinTonBanner: @Composable (() -> Unit)? = if (!data.hasTonAccounts) {
        {
            BannerJoinTon(
                onClick = callback::onJoinTonClicked,
                onCloseClick = callback::onJoinTonCloseClick,
            )
        }
    } else {
        null
    }

    val banners = listOfNotNull(backupBanner, joinSubOrEvmBanner, joinTonBanner)
    val bannersCount = banners.size
    val pagerState = rememberPagerState { bannersCount }

    if (bannersCount > 1) {
        // Auto play
        LaunchedEffect(key1 = autoPlay) {
            if (autoPlay) {
                while (true) {
                    delay(5000L)
                    with(pagerState) {
                        animateScrollToPage(
                            page = (currentPage + 1) % bannersCount,
                            animationSpec = tween(
                                durationMillis = 500,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
            }
        }
    }

    val bannersCarousel: @Composable (() -> Unit)? =
        banners.takeIf { it.isNotEmpty() }?.let {
            {
                HorizontalPager(
                    modifier = Modifier.fillMaxWidth(),
                    state = pagerState,
                    pageSpacing = 8.dp,
                    pageContent = { page ->
                        banners[page].invoke()
                    }
                )

                if (bannersCount > 1) {
                    MarginVertical(margin = 8.dp)
                    BannerPageIndicator(bannersCount, pagerState)
                }
            }
        }
    bannersCarousel?.invoke()
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
