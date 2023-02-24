package jp.co.soramitsu.polkaswap.api.data

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.model.QuoteResponse
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface PolkaswapRepository {
    suspend fun getAvailableDexes(chainId: ChainId): List<BigInteger>
    fun observePoolXYKReserves(chainId: ChainId, fromTokenId: String, toTokenId: String): Flow<String>
    fun observePoolTBCReserves(chainId: ChainId, tokenId: String): Flow<String>

    suspend fun getSwapQuote(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        amount: BigInteger,
        desired: WithDesired,
        curMarkets: List<Market>,
        dexId: Int
    ): QuoteResponse?

    suspend fun estimateSwapFee(
        chainId: ChainId,
        dexId: Int,
        inputAssetId: String,
        outputAssetId: String,
        amount: BigInteger,
        limit: BigInteger,
        filter: String,
        markets: List<String>,
        desired: WithDesired
    ): BigInteger

    suspend fun isPairAvailable(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        dexId: Int
    ): Boolean

    suspend fun getAvailableSources(chainId: ChainId, tokenId1: String, tokenId2: String, dexes: List<Int>): Map<Int, List<Market>>
    suspend fun swap(
        chainId: ChainId,
        dexId: Int,
        inputAssetId: String,
        outputAssetId: String,
        amount: BigInteger,
        limit: BigInteger,
        filter: String,
        markets: List<String>,
        desired: WithDesired
    ): Result<String>
}
