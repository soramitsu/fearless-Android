package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

sealed class AssetsLoadingState {
    class Loading(val shimmerStates: List<AssetListItemShimmerViewState> = emptyList()) : AssetsLoadingState()
    class Loaded(val assets: List<AssetListItemViewState>) : AssetsLoadingState()
} 