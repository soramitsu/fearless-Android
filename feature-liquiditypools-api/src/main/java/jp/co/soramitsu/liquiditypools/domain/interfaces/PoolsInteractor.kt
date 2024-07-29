package jp.co.soramitsu.liquiditypools.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.polkaswap.api.data.PoolDataDto
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.CommonPoolData
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface PoolsInteractor {
    suspend fun getBasicPools(chainId: ChainId): List<BasicPoolData>

    //    suspend fun getPoolCacheOfCurAccount(tokenFromId: String, tokenToId: String): CommonUserPoolData?
    fun subscribePoolsCacheOfAccount(address: String): Flow<List<CommonPoolData>>
    suspend fun getPoolData(
        chainId: ChainId,
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
        tokenFrom: Asset,
        tokenTo: Asset,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal?

    suspend fun calcRemoveLiquidityNetworkFee(
        chainId: ChainId,
        tokenFrom: Asset,
        tokenTo: Asset,
    ): BigDecimal?

    suspend fun isPairEnabled(
        chainId: ChainId,
        inputTokenId: String,
        outputTokenId: String
    ): Boolean

//    suspend fun updateApy()

    fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double?

    suspend fun observeAddLiquidity(
        chainId: ChainId,
        tokenFrom: Asset,
        tokenTo: Asset,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double
    ): String

    suspend fun updatePools(chainId: ChainId)

    suspend fun observeRemoveLiquidity(
        chainId: ChainId,
        tokenFrom: Asset,
        tokenTo: Asset,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        networkFee: BigDecimal
    ): String
}
