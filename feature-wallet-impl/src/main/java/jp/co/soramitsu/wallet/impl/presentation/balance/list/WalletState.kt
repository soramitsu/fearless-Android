package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.compose.component.SoraCardItemViewState
import jp.co.soramitsu.common.compose.component.SoraCardProgress
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models.NFTCollectionsScreenModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.common.AssetListState

@Stable
data class WalletState(
    val assetsState: WalletAssetsState,
    val multiToggleButtonState: MultiToggleButtonState<AssetType>,
    val balance: AssetBalanceViewState,
    val hasNetworkIssues: Boolean,
    val soraCardState: SoraCardItemViewState,
    val isBackedUp: Boolean,
    val scrollToTopEvent: Event<Unit>?,
    val scrollToBottomEvent: Event<Unit>?,
) {
    companion object {
        val default = WalletState(
            multiToggleButtonState = MultiToggleButtonState(AssetType.Currencies, listOf(AssetType.Currencies, AssetType.NFTs)),
            assetsState = WalletAssetsState.Assets(emptyList(), isHideVisible = true),
            balance = AssetBalanceViewState("", "", false, ChangeBalanceViewState("", "")),
            hasNetworkIssues = false,
            soraCardState = SoraCardItemViewState(visible = true, loading = true, success = false, iban = null, soraCardProgress = SoraCardProgress.START),
            isBackedUp = true,
            scrollToTopEvent = null,
            scrollToBottomEvent = null
        )
    }
}

@Stable
sealed interface WalletAssetsState {
    data class Assets(
        override val assets: List<AssetListItemViewState>,
        val isHideVisible: Boolean
    ): WalletAssetsState, AssetListState(assets)

    @Immutable
    data class NetworkIssue(
        val chainId: String,
        val issueType: NetworkIssueType,
        val retryButtonLoading: Boolean
    ): WalletAssetsState

    @JvmInline
    value class NftAssets(
        val collectionScreenModel: NFTCollectionsScreenModel
    ): WalletAssetsState
}
