package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItemViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.common.AssetListState

data class WalletState(
    override val assets: List<AssetListItemViewState>,
    val multiToggleButtonState: MultiToggleButtonState<AssetType>,
    val balance: AssetBalanceViewState,
    val hasNetworkIssues: Boolean,
    val soraCardState: SoraCardItemViewState?,
    val isBackedUp: Boolean
) : AssetListState(assets) {
    companion object {
        val default = WalletState(
            multiToggleButtonState = MultiToggleButtonState(AssetType.Currencies, listOf(AssetType.Currencies, AssetType.NFTs)),
            assets = emptyList(),
            balance = AssetBalanceViewState("", "", false, ChangeBalanceViewState("", "")),
            hasNetworkIssues = false,
            soraCardState = null,
            isBackedUp = true
        )
    }
}
