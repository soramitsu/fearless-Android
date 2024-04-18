package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.BannerBackup
import jp.co.soramitsu.common.compose.component.BannerBuyXor
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssuesBadge
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.utils.rememberForeverLazyListState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItem
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItemViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.NFTScreen
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsList
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsListInterface

interface WalletScreenInterface : AssetsListInterface {
    fun onAddressClick()
    fun onBalanceClicked()
    fun soraCardClicked()
    fun soraCardClose()
    fun onNetworkIssuesClicked()
    fun onBackupClicked()
    fun onBackupCloseClick()
    fun assetTypeChanged(type: AssetType)
    fun onRefresh()
    fun onManageAssetClick()
    fun onBannerBuyXorClicked()
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
            if (data.assetsState is WalletAssetsState.Assets) {
                val items = data.assetsState.assets.size + listOf("header", "footer").size
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
        if (data.hasNetworkIssues) {
            MarginVertical(margin = 6.dp)
            NetworkIssuesBadge(onClick = callback::onNetworkIssuesClicked)
        }
        MarginVertical(margin = 16.dp)
        MultiToggleButton(
            state = data.multiToggleButtonState,
            onToggleChange = callback::assetTypeChanged
        )
        when (data.assetsState) {
            is WalletAssetsState.NftAssets -> {
                NFTScreen(collectionsScreen = data.assetsState.collectionScreenModel)
            }
            is WalletAssetsState.Assets -> {
                val header: @Composable () -> Unit = { Banners(data, callback) }
                val footer: @Composable () -> Unit = { WalletScreenFooter(scale.value, callback::onManageAssetClick) }
                AssetsList(
                    data = data.assetsState,
                    callback = callback,
                    header = header,
                    listState = listState,
                    footer = footer
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Banners(data: WalletState, callback: WalletScreenInterface) {
    val soraCardBanner: @Composable (() -> Unit)? =
        if (data.soraCardState?.visible == true) {
            {
                SoraCardItem(
                    state = data.soraCardState,
                    onClose = callback::soraCardClose,
                    onClick = callback::soraCardClicked
                )
            }
        } else {
            null
        }
    // todo what is logic for buy xor banner appearance?
    val buyXorBanner: @Composable (() -> Unit)? = if (false) {
        {
            BannerBuyXor(
                onBuyXorClick = callback::onBannerBuyXorClicked
            )
        }
    } else null

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
    val banners = listOfNotNull(buyXorBanner, backupBanner)
    val bannersCount = banners.size
    val bannersCarousel: @Composable (() -> Unit)? =
        banners.takeIf { it.isNotEmpty() }?.let {
            {
                val pagerState = rememberPagerState { bannersCount }
                HorizontalPager(
                    modifier = Modifier.fillMaxWidth(),
                    state = pagerState,
                    pageSpacing = 8.dp,
                    pageContent = { page ->
                        banners[page].invoke()
                    }
                )

                if (bannersCount > 1) {
                    BannerPageIndicator(bannersCount, pagerState)
                    MarginVertical(margin = 8.dp)
                }
            }
        }
    if (soraCardBanner != null || bannersCarousel != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            soraCardBanner?.invoke()
            bannersCarousel?.invoke()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BannerPageIndicator(
    bannersCount: Int,
    pagerState: PagerState
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(bannersCount) { iteration ->
            val color = if (pagerState.currentPage == iteration) white16 else white50
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .clip(CircleShape)
                    .background(color)
                    .size(8.dp)
            )
        }
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
        override fun soraCardClicked() {}
        override fun soraCardClose() {}
        override fun onAddressClick() {}
        override fun onBalanceClicked() {}
        override fun onNetworkIssuesClicked() {}
        override fun onBackupClicked() {}
        override fun onBackupCloseClick() {}
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
        override fun onBannerBuyXorClicked() {}
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
                    assetsState = WalletAssetsState.Assets(assets, isHideVisible = true),
                    balance = AssetBalanceViewState(
                        "TRANSFERABLE BALANCE",
                        "ADDRESS",
                        true,
                        ChangeBalanceViewState("+100%", "+50$")
                    ),
                    hasNetworkIssues = true,
                    soraCardState = SoraCardItemViewState(null, null, null, true),
                    isBackedUp = false,
                    scrollToTopEvent = null,
                    scrollToBottomEvent = null
                ),
                callback = emptyCallback
            )
        }
    }
}
