package jp.co.soramitsu.wallet.impl.presentation.common

import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

abstract class AssetListState(
    open val assets: List<AssetListItemViewState>
)
