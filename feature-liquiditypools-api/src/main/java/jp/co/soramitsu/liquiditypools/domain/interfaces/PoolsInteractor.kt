package jp.co.soramitsu.liquiditypools.domain.interfaces

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.liquiditypools.data.PoolDataDto
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface PoolsInteractor {
    val poolsChainId: String

    suspend fun getBasicPools(): List<BasicPoolData>

    fun subscribePoolsCacheOfAccount(address: String): Flow<List<CommonPoolData>>
    fun subscribePoolsCacheCurrentAccount(): Flow<List<CommonPoolData>>
    suspend fun getPoolData(
        baseTokenId: String,
        targetTokenId: String,
    ): Flow<CommonPoolData>

    suspend fun getUserPoolData(
        chainId: ChainId,
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
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
        tokenBase: Asset,
        tokenTarget: Asset,
    ): BigDecimal?

    suspend fun isPairEnabled(
        baseTokenId: String,
        targetTokenId: String
    ): Boolean

    suspend fun observeAddLiquidity(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double
    ): String

    suspend fun syncPools(chainId: ChainId)

    suspend fun observeRemoveLiquidity(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        networkFee: BigDecimal
    ): String

    suspend fun getSbApy(id: String): Double?
}
