package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.common.AssetListState

data class SearchAssetState(
    override val assets: AssetsLoadingState = AssetsLoadingState.Loading(),
    val searchQuery: String = ""
) : AssetListState(assets)
