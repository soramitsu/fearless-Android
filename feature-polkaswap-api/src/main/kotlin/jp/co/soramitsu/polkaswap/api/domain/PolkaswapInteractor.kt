package jp.co.soramitsu.polkaswap.api.domain

import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset

interface PolkaswapInteractor {

    val polkaswapChainId: String

    suspend fun getAsset(assetId: String): Asset?
    suspend fun getAvailableDexes(chainId: ChainId): List<BigInteger>
}
