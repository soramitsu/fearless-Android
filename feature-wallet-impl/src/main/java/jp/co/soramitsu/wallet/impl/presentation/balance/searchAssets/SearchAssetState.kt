package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.common.AssetListState

/**
 * [assets] are the matching rows the portfolio shows ("In your portfolio"); [hiddenAssets] the
 * matching explicitly hidden ones and [otherAssets] matching assets the portfolio does not show.
 * The last two are filled only for a non-empty query.
 */
data class SearchAssetState(
    override val assets: AssetsLoadingState = AssetsLoadingState.Loading(),
    val searchQuery: String = "",
    val hiddenAssets: List<AssetListItemViewState> = emptyList(),
    val otherAssets: List<AssetListItemViewState> = emptyList()
) : AssetListState(assets) {

    val portfolioAssets: List<AssetListItemViewState>
        get() = (assets as? AssetsLoadingState.Loaded)?.assets.orEmpty()

    val hasResults: Boolean
        get() = portfolioAssets.isNotEmpty() || hiddenAssets.isNotEmpty() || otherAssets.isNotEmpty()
}
