package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.wallet.impl.presentation.common.AssetListState

data class SearchAssetState(
    override val assets: List<AssetListItemViewState>,
    val searchQuery: String? = null
) : AssetListState(assets)
