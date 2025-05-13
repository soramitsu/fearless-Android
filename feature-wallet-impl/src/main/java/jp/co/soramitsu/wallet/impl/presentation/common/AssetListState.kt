package jp.co.soramitsu.wallet.impl.presentation.common

import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState

abstract class AssetListState(
    open val assets: AssetsLoadingState
)
