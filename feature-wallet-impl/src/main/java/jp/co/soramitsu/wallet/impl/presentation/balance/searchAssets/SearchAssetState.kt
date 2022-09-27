package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import jp.co.soramitsu.common.compose.component.HiddenItemState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

data class SearchAssetState(
    val assets: List<AssetListItemViewState>,
    val searchQuery: String? = null,
    val hiddenState: HiddenItemState
) {
    val visibleAssets = assets.filter { !it.isHidden }
    val hiddenAssets = assets.filter { it.isHidden }
}
