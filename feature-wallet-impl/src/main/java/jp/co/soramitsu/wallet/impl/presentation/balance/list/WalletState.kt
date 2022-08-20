package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

data class WalletState(
    val selectedOption: String,
    val assets: List<AssetListItemViewState>
)
