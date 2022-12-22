package jp.co.soramitsu.polkaswap.api.domain

import jp.co.soramitsu.wallet.impl.domain.model.Asset

interface PolkaswapInteractor {

    val polkaswapChainId: String

    suspend fun getAsset(assetId: String): Asset?
}
