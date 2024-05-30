package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.median
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.EraRewardPoints
import jp.co.soramitsu.staking.impl.data.repository.HistoricalMapping
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReefRewardCalculator(
    private val amount: BigDecimal,
    private val validators: List<RewardCalculationTarget>,
    private val erasPayouts: Map<BigInteger, Double>,
    private val asset: Asset,
    calculationTargets: List<String>,
    private val historicalRewardDistribution: HistoricalMapping<EraRewardPoints>
) : RewardCalculator {

    private val apyByValidator = validators.associateBy(
        keySelector = RewardCalculationTarget::accountIdHex,
        valueTransform = ::calculateValidatorAPY
    )

    private val apyByCalculationTargets =
        calculationTargets.associateWith { apyByValidator[it] }.filterValues { it != null }
            .cast<Map<String, Double>>()

    private val maxAPY = apyByCalculationTargets.values.filter { it.isNaN().not() }.maxOrNull() ?: 0.0
    private val expectedAPY = calculateExpectedAPY()

    private fun calculateExpectedAPY(): Double {
        val prices = validators.map { it.commission.toDouble() }.filter { it < 1.0 }

        val medianCommission = when {
            prices.isEmpty() -> 0.0
            else -> prices.median()
        }
        val averageValidatorRewardPercentage = apyByCalculationTargets.values.average()
        return averageValidatorRewardPercentage * (1 - medianCommission)
    }

    private fun calculateValidatorAPY(validator: RewardCalculationTarget): Double {
        return runCatching {
            val validatorHistoricalRewardDistribution = historicalRewardDistribution.values.asSequence().map { it.individual }
                .flatten()
                .filter { it.accountId.contentEquals(validator.accountIdHex.fromHex()) }
                .toList()

            val averageValidatorRewardPoints = if(validatorHistoricalRewardDistribution.isEmpty()){
                0.0
            } else {
                validatorHistoricalRewardDistribution.map { it.rewardPoints.toDouble() }.average()
            }

            val rewardDistributionForLastEra = historicalRewardDistribution.maxBy { it.key }.value

            val lastEraPayout = erasPayouts.maxBy { it.key }

            val portion =
                averageValidatorRewardPoints / rewardDistributionForLastEra.totalPoints.toDouble()
            val validatorTotalStake = asset.amountFromPlanks(validator.totalStake)
            val userPortion = amount.toDouble() / validatorTotalStake.toDouble()
            val validatorReward = (lastEraPayout.value * portion)

            val rewardForAmountInCurrentEra =
                userPortion * validatorReward - (validator.commission.toDouble() * userPortion * validatorReward)

            (rewardForAmountInCurrentEra / amount.toDouble())  * DAYS_IN_YEAR
        }.getOrNull() ?: 0.0
    }

    override suspend fun calculateMaxAPY(chainId: ChainId): BigDecimal {
        val dailyPercentage = maxAPY / DAYS_IN_YEAR

        return calculateReward(
            amount = BigDecimal.ONE.toDouble(),
            days = DAYS_IN_YEAR,
            dailyPercentage = dailyPercentage,
            isCompound = true
        ).gainPercentage
    }

    override suspend fun calculateAvgAPY(): BigDecimal {
        return apyByCalculationTargets.values.map {
            val dailyPercentage = it / DAYS_IN_YEAR
            calculateReward(
                amount = BigDecimal.ONE.toDouble(),
                days = DAYS_IN_YEAR,
                dailyPercentage = dailyPercentage,
                isCompound = true
            ).gainPercentage.toDouble()
        }.average().toBigDecimal()
    }

    override suspend fun getApyFor(targetId: ByteArray): BigDecimal {
        val apy = apyByValidator[targetId.toHexString()] ?: expectedAPY

        return apy.toBigDecimal()
    }

    override suspend fun calculateReturns(
        amount: BigDecimal,
        days: Int,
        isCompound: Boolean,
        chainId: ChainId
    ) = withContext(Dispatchers.Default) {
        val dailyPercentage = maxAPY / DAYS_IN_YEAR

        calculateReward(amount.toDouble(), days, dailyPercentage, isCompound)
    }

    override suspend fun calculateReturns(
        amount: Double,
        days: Int,
        isCompound: Boolean,
        targetIdHex: String
    ) = withContext(Dispatchers.Default) {
        val validatorAPY =
            apyByValidator[targetIdHex] ?: error("Validator with $targetIdHex was not found")
        val dailyPercentage = validatorAPY / DAYS_IN_YEAR

        calculateReward(amount, days, dailyPercentage, isCompound)
    }

    private fun calculateReward(
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
            (gainAmount / amount.toBigDecimal()).fractionToPercentage()
        }

        return PeriodReturns(
            gainAmount = gainAmount,
            gainPercentage = gainPercentage
        )
    }

    private fun calculateSimpleReward(
        amount: Double,
        days: Int,
        dailyPercentage: Double
    ): BigDecimal {
        return amount.toBigDecimal() * dailyPercentage.toBigDecimal() * days.toBigDecimal()
    }

    private fun calculateCompoundReward(
        amount: Double,
        days: Int,
        dailyPercentage: Double
    ): BigDecimal {
        return amount.toBigDecimal() * ((1 + dailyPercentage).toBigDecimal()
            .pow(days)) - amount.toBigDecimal()
    }
}
