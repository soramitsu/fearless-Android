package jp.co.soramitsu.wallet.impl.presentation.common

import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

abstract class AssetListState(
    open val assets: List<AssetListItemViewState>
) {
    val visibleAssets: List<AssetListItemViewState>
        get() = assets.filter { !it.isHidden }
    val hiddenAssets: List<AssetListItemViewState>
        get() = assets.filter { it.isHidden }
}
