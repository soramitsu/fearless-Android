package jp.co.soramitsu.liquiditypools.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.polkaswap.api.data.PoolDataDto
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.CommonUserPoolData
import jp.co.soramitsu.wallet.impl.domain.model.Asset

interface PoolsInteractor {
    suspend fun getBasicPools(): List<BasicPoolData>

    suspend fun getPoolCacheOfCurAccount(tokenFromId: String, tokenToId: String): CommonUserPoolData?

    suspend fun getUserPoolData(
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): PoolDataDto?

    suspend fun calcAddLiquidityNetworkFee(
        address: String,
        tokenFrom: jp.co.soramitsu.core.models.Asset,
        tokenTo: jp.co.soramitsu.core.models.Asset,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal?

    suspend fun isPairEnabled(
        inputTokenId: String,
        outputTokenId: String,
        accountAddress: String
    ): Boolean

    suspend fun updateApy()

    fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double?

    suspend fun observeAddLiquidity(
        tokenFrom: jp.co.soramitsu.core.models.Asset,
        tokenTo: jp.co.soramitsu.core.models.Asset,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double
    ): String
}
