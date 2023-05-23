package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssuesBadge
import jp.co.soramitsu.common.compose.component.NftStub
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
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
    fun assetTypeChanged(type: AssetType)
    fun onRefresh()
}

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
            val header: @Composable (() -> Unit)? = when {
                data.soraCardState?.visible != true -> null
                else -> {
                    {
                        SoraCardItem(
                            state = data.soraCardState,
                            onClose = callback::soraCardClose,
                            onClick = callback::soraCardClicked
                        )
                    }
                }
            }
            AssetsList(
                data = data,
                callback = callback,
                header = null
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
            displayName = "Sora",
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
                        soraCardState = SoraCardItemViewState(null, null, null, true)
                    ),
                    callback = emptyCallback
                )
            }
        }
    }
}
