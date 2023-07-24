package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.BannerBackup
import jp.co.soramitsu.common.compose.component.BannerBuyXor
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssuesBadge
import jp.co.soramitsu.common.compose.component.NftStub
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItem
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItemViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsList
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsListInterface

interface WalletScreenInterface : AssetsListInterface {
    fun onAddressClick()
    fun onBalanceClicked()
    fun soraCardClicked()
    fun soraCardClose()
    fun onNetworkIssuesClicked()
    fun onBackupClicked()
    fun assetTypeChanged(type: AssetType)
    fun onRefresh()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalletScreen(
    data: WalletState,
    callback: WalletScreenInterface
) {
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
            val soraCardBanner: @Composable (() -> Unit)? = data.soraCardState?.takeIf { it.visible }?.let {
                {
                    SoraCardItem(
                        state = data.soraCardState,
                        onClose = callback::soraCardClose,
                        onClick = callback::soraCardClicked
                    )
                }
            }
            val buyXorBanner: @Composable (() -> Unit)? = takeIf { false }?.let {
                {
                    BannerBuyXor(
                        onBuyXorClick = {}
                    )
                }
            }
            val backupBanner: @Composable (() -> Unit)? = takeIf { !data.isBackedUp }?.let {
                {
                    BannerBackup(
                        onBackupClick = callback::onBackupClicked
                    )
                }
            }
            val banners = listOfNotNull(buyXorBanner, backupBanner)
            val bannersCount = banners.size
            println("!!! bannersCount = $bannersCount")
            val bannersCarousel: @Composable (() -> Unit)? = banners.takeIf { it.isNotEmpty() }?.let {
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

                    MarginVertical(margin = 8.dp)
                    if (bannersCount > 1) {
                        BannerPageIndicator(bannersCount, pagerState)
                        MarginVertical(margin = 8.dp)
                    }
                }
            }
            val header: @Composable (() -> Unit)? = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    soraCardBanner?.invoke()
                    bannersCarousel?.invoke()
                }
            }
            AssetsList(
                data = data,
                callback = callback,
                header = header
            )
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
        override fun assetTypeChanged(type: AssetType) {}
        override fun assetClicked(asset: AssetListItemViewState) {}
        override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {}
        override fun onRefresh() {}
    }

    val assets: List<AssetListItemViewState> = listOf(
        AssetListItemViewState(
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
            hasAccount = true,
            priceId = null,
            hasNetworkIssue = false,
            ecosystem = "Polkadot",
            isTestnet = false
        )
    )

    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            Column {
                WalletScreen(
                    data = WalletState(
                        multiToggleButtonState = MultiToggleButtonState(AssetType.Currencies, listOf(AssetType.Currencies, AssetType.NFTs)),
                        assets = assets,
                        balance = AssetBalanceViewState("TRANSFERABLE BALANCE", "ADDRESS", true, ChangeBalanceViewState("+100%", "+50$")),
                        hasNetworkIssues = true,
                        soraCardState = SoraCardItemViewState(null, null, null, true),
                        isBackedUp = false
                    ),
                    callback = emptyCallback
                )
            }
        }
    }
}
