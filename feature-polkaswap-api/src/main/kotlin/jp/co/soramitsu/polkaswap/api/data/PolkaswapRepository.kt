package jp.co.soramitsu.polkaswap.api.data

import java.math.BigDecimal
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.CommonUserPoolData
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair

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

    suspend fun getBasicPools(): List<BasicPoolData>

    suspend fun getPoolOfAccount(
        address: String?,
        tokenFromId: String,
        tokenToId: String,
        chainId: String
    ): CommonUserPoolData?

    suspend fun getUserPoolData(
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): PoolDataDto?

    suspend fun calcAddLiquidityNetworkFee(
        address: String,
        tokenFrom: Asset,
        tokenTo: Asset,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal?

    suspend fun getPoolBaseTokenDexId(tokenId: String?): Int
    suspend fun updatePoolsSbApy()
    fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double?

    suspend fun observeAddLiquidity(
        address: String,
        keypair: Keypair,
        tokenFrom: Asset,
        tokenTo: Asset,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): Pair<String, String>?

}
