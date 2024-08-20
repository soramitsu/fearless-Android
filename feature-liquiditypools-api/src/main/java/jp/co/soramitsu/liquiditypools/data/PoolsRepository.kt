package jp.co.soramitsu.liquiditypools.data

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Suppress("ComplexInterface")
interface PoolsRepository {

    val poolsChainId: String

    suspend fun isPairAvailable(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        dexId: Int
    ): Boolean

    suspend fun getBasicPools(chainId: ChainId): List<BasicPoolData>

    suspend fun getBasicPool(
        chainId: ChainId,
        baseTokenId: String,
        targetTokenId: String
    ): BasicPoolData?

    suspend fun getUserPoolData(
        chainId: ChainId,
        address: String,
        baseTokenId: String,
        targetTokenId: ByteArray
    ): PoolDataDto?

    @Suppress("LongParameterList")
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

    suspend fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double?

    suspend fun observeRemoveLiquidity(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): Result<String>?

    @Suppress("LongParameterList")
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

    fun subscribePools(address: String): Flow<List<CommonPoolData>>
    fun subscribePool(
        address: String,
        baseTokenId: String,
        targetTokenId: String
    ): Flow<CommonPoolData>
}
