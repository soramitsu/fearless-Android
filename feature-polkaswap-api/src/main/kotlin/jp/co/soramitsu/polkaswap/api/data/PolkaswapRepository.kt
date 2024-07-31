package jp.co.soramitsu.polkaswap.api.data

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.CommonPoolData
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
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

    suspend fun getBasicPools(chainId: ChainId): List<BasicPoolData>

    suspend fun getBasicPool(chainId: ChainId, baseTokenId: String, targetTokenId: String): BasicPoolData?

    suspend fun getUserPoolData(
        chainId: ChainId,
        address: String,
        baseTokenId: String,
        targetTokenId: ByteArray
    ): PoolDataDto?

    suspend fun calcAddLiquidityNetworkFee(
        chainId: ChainId,
        address: String,
        tokenBase: Asset,
        tokenTarget: Asset,
        tokenBaseAmount: BigDecimal,
        tokenTargetAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal?

    suspend fun calcRemoveLiquidityNetworkFee(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
    ): BigDecimal?

    suspend fun getPoolBaseTokenDexId(chainId: ChainId, tokenId: String?): Int

    fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double?

    suspend fun observeRemoveLiquidity(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): Result<String>?

    suspend fun observeAddLiquidity(
        chainId: ChainId,
        address: String,
        keypair: Keypair,
        tokenBase: Asset,
        tokenTarget: Asset,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): Result<String>?

    suspend fun updateAccountPools(chainId: ChainId, address: String)
    suspend fun updateBasicPools(chainId: ChainId)

//    fun subscribePools(): Flow<List<BasicPoolData>>
    fun subscribePools(address: String): Flow<List<CommonPoolData>>
    fun subscribePool(address: String, baseTokenId: String, targetTokenId: String): Flow<CommonPoolData>

}
