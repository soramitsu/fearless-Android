package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType

data class WalletState(
    val multiToggleButtonState: MultiToggleButtonState<AssetType>,
    val assets: List<AssetListItemViewState>
)
