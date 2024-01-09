package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class PeriodReturns(
    val gainAmount: BigDecimal,
    val gainPercentage: BigDecimal
)

interface RewardCalculator {

    suspend fun calculateMaxAPY(chainId: ChainId): BigDecimal

    suspend fun calculateAvgAPY(): BigDecimal

    suspend fun getApyFor(targetId: ByteArray): BigDecimal

    suspend fun calculateReturns(
        amount: BigDecimal,
        days: Int,
        isCompound: Boolean,
        chainId: ChainId
    ): PeriodReturns

    suspend fun calculateReturns(
        amount: Double,
        days: Int,
        isCompound: Boolean,
        targetIdHex: String
    ): PeriodReturns
}
