package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.utils.fractionToPercentage

class SoraRewardCalculator(
    validators: List<RewardCalculationTarget>,
    totalIssuance: BigInteger,
    private val xorValRate: BigDecimal
) : ManualRewardCalculator(validators, totalIssuance) {

    override fun calculateReward(
        amount: Double,
        days: Int,
        dailyPercentage: Double,
        isCompound: Boolean
    ): PeriodReturns {
        val gainAmount = if (isCompound) {
            calculateCompoundReward(amount, days, dailyPercentage)
        } else {
            calculateSimpleReward(amount, days, dailyPercentage)
        }
        val gainPercentage = if (amount == 0.0) {
            BigDecimal.ZERO
        } else {
            (gainAmount / xorValRate / amount.toBigDecimal()).fractionToPercentage()
        }
        return PeriodReturns(
            gainAmount = gainAmount,
            gainPercentage = gainPercentage
        )
    }

    override suspend fun calculateAvgAPY(): BigDecimal {
        val dailyPercentage = maxAPY / DAYS_IN_YEAR
        return calculateReward(1.0, DAYS_IN_YEAR, dailyPercentage, false).gainPercentage
    }
}
