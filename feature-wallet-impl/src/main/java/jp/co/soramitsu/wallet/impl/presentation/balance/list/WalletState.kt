package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.HiddenItemState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType

data class WalletState(
    val multiToggleButtonState: MultiToggleButtonState<AssetType>,
    val assets: List<AssetListItemViewState>,
    val balance: AssetBalanceViewState,
    val hiddenState: HiddenItemState,
    val hasNetworkIssues: Boolean
) {
    companion object {
        val default = WalletState(
            multiToggleButtonState = MultiToggleButtonState(AssetType.Currencies, listOf(AssetType.Currencies, AssetType.NFTs)),
            assets = emptyList(),
            balance = AssetBalanceViewState("", "", false, ChangeBalanceViewState("", "")),
            hiddenState = HiddenItemState(),
            hasNetworkIssues = false
        )
    }
    val visibleAssets = assets.filter { !it.isHidden }
    val hiddenAssets = assets.filter { it.isHidden }
}
