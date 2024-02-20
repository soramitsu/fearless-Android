package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.median
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.EraRewardPoints
import jp.co.soramitsu.staking.impl.data.repository.HistoricalMapping
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SoraRewardCalculator(
    private val validators: List<RewardCalculationTarget>,
    private val xorValRate: Double,
    private val averageValidatorPayout: Double,
    private val asset: Asset,
    calculationTargets: List<String>,
    private val historicalRewardDistribution: HistoricalMapping<EraRewardPoints>
) : RewardCalculator {
    companion object {
        private const val ERAS_PER_DAY = 4
    }

    private val averageTotalPoints =
        historicalRewardDistribution.values.map { it.totalPoints.toDouble() }.average()

    private val apyByValidator = validators.associateBy(
        keySelector = RewardCalculationTarget::accountIdHex,
        valueTransform = ::calculateValidatorAPY
    )

    private val apyByCalculationTargets =
        calculationTargets.associateWith { apyByValidator[it] }.filterValues { it != null }
            .cast<Map<String, Double>>()

    private val maxAPY = apyByCalculationTargets.values.maxOrNull() ?: 0.0
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
        if(validator.accountIdHex == "e2fe9aa30575261dfed2f83a95f30bdc1e1cd8c5813de31d0b8ee1071c1f4d10"){
            hashCode()
        }
        val averageValidatorRewardPoints =
            historicalRewardDistribution.values.asSequence().map { it.individual }.flatten()
                .filter { it.accountId.toHexString(false) == validator.accountIdHex }
                .map { it.rewardPoints.toDouble() }.average()

        val validatorOwnStake = asset.amountFromPlanks(validator.totalStake).toDouble()

        val portion = averageValidatorRewardPoints / averageTotalPoints
        val averageValidatorRewardInVal = averageValidatorPayout * portion
        val ownStakeInVal = validatorOwnStake * xorValRate
        val result =
            averageValidatorRewardInVal / ownStakeInVal * (1 - validator.commission.toDouble())

        return result * ERAS_PER_DAY * DAYS_IN_YEAR
    }

    override suspend fun calculateMaxAPY(chainId: ChainId): BigDecimal {
        return calculateReturns(
            amount = BigDecimal.ONE,
            days = DAYS_IN_YEAR,
            isCompound = false,
            chainId = chainId
        ).gainPercentage
    }

    override suspend fun calculateAvgAPY(): BigDecimal {
        val average = apyByCalculationTargets.values.average()
        val dailyPercentage = average / DAYS_IN_YEAR
        return calculateReward(
            amount = BigDecimal.ONE.toDouble(),
            days = DAYS_IN_YEAR,
            dailyPercentage = dailyPercentage
        ).gainPercentage
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
        calculateReward(amount.toDouble(), days, dailyPercentage)
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

        calculateReward(amount, days, dailyPercentage)
    }

    private fun calculateReward(
        amount: Double,
        days: Int,
        dailyPercentage: Double
    ): PeriodReturns {
        val gainAmount =
            amount.toBigDecimal() * dailyPercentage.toBigDecimal() * days.toBigDecimal()
        val gainPercentage = if (amount == 0.0) {
            BigDecimal.ZERO
        } else {
            (gainAmount / amount.toBigDecimal()).fractionToPercentage()
        }
        return PeriodReturns(
            gainAmount = gainAmount * xorValRate.toBigDecimal(),
            gainPercentage = gainPercentage
        )
    }
}
