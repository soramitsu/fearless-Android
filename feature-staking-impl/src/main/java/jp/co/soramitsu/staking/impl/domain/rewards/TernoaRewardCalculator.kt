package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TernoaRewardCalculator(
    private val validators: List<RewardCalculationTarget>,
    private val averageValidatorPayout: Double,
    private val asset: Asset
) : RewardCalculator {

    companion object {
        private const val ERAS_PER_DAY = 1
    }

    private val apyByValidatorWithCompound = validators.associateBy(
        keySelector = RewardCalculationTarget::accountIdHex,
        valueTransform = ::calculateValidatorAPY
    )

    private val apyByValidatorWithoutCompound = validators.associateBy(
        keySelector = RewardCalculationTarget::accountIdHex,
        valueTransform = { validator ->
            val validatorOwnStake = asset.amountFromPlanks(validator.totalStake).toDouble()

            val averageValidatorReward = averageValidatorPayout / validators.size
            val result = averageValidatorReward / validatorOwnStake * (1 - validator.commission.toDouble())

            result * ERAS_PER_DAY * DAYS_IN_YEAR
        }
    )

    private val maxAPY = apyByValidatorWithCompound.values.maxOrNull() ?: 0.0

    private fun calculateValidatorAPY(validator: RewardCalculationTarget): Double {
        val validatorOwnStake = asset.amountFromPlanks(validator.totalStake).toDouble()

        val averageValidatorReward = averageValidatorPayout / validators.size
        val dailyPercentage = averageValidatorReward / validatorOwnStake * (1 - validator.commission.toDouble())
        return calculateYearlyCompoundReward(dailyPercentage).toDouble()
    }

    override suspend fun calculateMaxAPY(chainId: ChainId): BigDecimal {
        return maxAPY.toBigDecimal().fractionToPercentage()
    }

    override suspend fun calculateAvgAPY(): BigDecimal {
        return calculateExpectedAPY().toBigDecimal().fractionToPercentage()
    }

    override suspend fun getApyFor(targetId: ByteArray): BigDecimal {
        val apy = apyByValidatorWithCompound[targetId.toHexString()] ?: calculateExpectedAPY()

        return apy.toBigDecimal()
    }

    private fun calculateExpectedAPY(): Double {
        val prices = validators.map { it.commission.toDouble() }
        val averageStake = validators.map { asset.amountFromPlanks(it.totalStake).toDouble() }.average()

        val medianCommission = when {
            prices.isEmpty() -> 0.0
            else -> prices.average()
        }

        val averageValidatorReward = averageValidatorPayout / validators.size
        val dailyPercentage = averageValidatorReward / averageStake * (1 - medianCommission)
        return calculateYearlyCompoundReward(dailyPercentage).toDouble()
    }

    override suspend fun calculateReturns(amount: BigDecimal, days: Int, isCompound: Boolean, chainId: ChainId) = withContext(Dispatchers.Default) {
        val maxApy = if (isCompound) {
            maxAPY
        } else {
            apyByValidatorWithoutCompound.values.maxOrNull() ?: 0.0
        }
        val dailyPercentage = maxApy / DAYS_IN_YEAR
        calculateReward(amount.toDouble(), days, dailyPercentage)
    }

    override suspend fun calculateReturns(amount: Double, days: Int, isCompound: Boolean, targetIdHex: String) = withContext(Dispatchers.Default) {
        val validatorAPY =
            apyByValidatorWithCompound[targetIdHex] ?: error("Validator with $targetIdHex was not found")
        val dailyPercentage = validatorAPY / DAYS_IN_YEAR

        calculateReward(amount, days, dailyPercentage)
    }

    private fun calculateReward(
        amount: Double,
        days: Int,
        dailyPercentage: Double
    ): PeriodReturns {
        val gainAmount = calculateSimpleReward(amount, days, dailyPercentage)

        val gainPercentage = if (amount == 0.0) {
            BigDecimal.ZERO
        } else {
            (gainAmount / amount.toBigDecimal()).fractionToPercentage()
        }

        return PeriodReturns(
            gainAmount = gainAmount,
            gainPercentage = gainPercentage
        )
    }

    private fun calculateSimpleReward(amount: Double, days: Int, dailyPercentage: Double): BigDecimal {
        return amount.toBigDecimal() * dailyPercentage.toBigDecimal() * days.toBigDecimal()
    }

    private fun calculateYearlyCompoundReward(dailyPercentage: Double): BigDecimal {
        val defaultAmount = BigDecimal.ONE
        return defaultAmount * ((1 + dailyPercentage).toBigDecimal().pow(DAYS_IN_YEAR)) - defaultAmount
    }
}
