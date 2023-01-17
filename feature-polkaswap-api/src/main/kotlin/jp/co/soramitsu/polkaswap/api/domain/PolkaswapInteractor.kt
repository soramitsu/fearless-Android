package jp.co.soramitsu.polkaswap.api.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetails
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow

interface PolkaswapInteractor {

    val polkaswapChainId: String

    suspend fun getAsset(assetId: String): Asset?
    suspend fun getAvailableDexes(chainId: ChainId): List<BigInteger>
    fun observePoolReserves(chainId: ChainId, fromTokenId: String, toTokenId: String, market: Market): Flow<String>

    suspend fun calcDetails(
        chainId: ChainId,
        tokenFrom: Asset,
        tokenTo: Asset,
        feeToken: Asset,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Float,
        market: Market
    ): SwapDetails?
}
