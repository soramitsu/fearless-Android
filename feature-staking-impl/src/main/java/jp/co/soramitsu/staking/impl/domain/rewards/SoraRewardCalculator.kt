package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.median
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
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

    init {
        require(xorValRate.isFinite() && xorValRate > 0.0) { "SORA conversion rate is invalid" }
        require(averageValidatorPayout.isFinite() && averageValidatorPayout >= 0.0) { "SORA validator payout is invalid" }
        require(asset.precision in 0..255) { "SORA asset precision is invalid" }
        require(validators.isNotEmpty() && calculationTargets.isNotEmpty()) { "SORA calculation targets are unavailable" }
        require(historicalRewardDistribution.isNotEmpty()) { "SORA reward history is unavailable" }
        historicalRewardDistribution.values.forEach { era ->
            require(era.totalPoints.signum() >= 0 && era.totalPoints.toDouble().isFinite()) { "SORA total reward points are invalid" }
            require(era.individual.all { it.rewardPoints.signum() >= 0 && it.rewardPoints.toDouble().isFinite() }) {
                "SORA individual reward points are invalid"
            }
        }
        validators.forEach { validator ->
            require(validator.commission >= BigDecimal.ZERO && validator.commission <= BigDecimal.ONE) { "SORA commission is invalid" }
            require(validator.totalStake.signum() > 0) { "SORA validator stake is unavailable" }
        }
    }

    private val averageTotalPoints =
        historicalRewardDistribution.values.map { it.totalPoints.toDouble() }.average().also {
            require(it.isFinite() && it > 0.0) { "SORA average reward points are unavailable" }
        }

    private val apyByValidator = validators.associateBy(
        keySelector = RewardCalculationTarget::accountIdHex,
        valueTransform = ::calculateValidatorAPY
    )

    private val apyByCalculationTargets =
        calculationTargets.associateWith { apyByValidator[it] }.filterValues { it != null }
            .cast<Map<String, Double>>()

    private val maxAPY = requireNotNull(apyByCalculationTargets.values.maxOrNull()) { "SORA matched calculation targets are unavailable" }
    private val expectedAPY = calculateExpectedAPY()

    private fun calculateExpectedAPY(): Double {
        val prices = validators.map { it.commission.toDouble() }.filter { it < 1.0 }

        val medianCommission = when {
            prices.isEmpty() -> 0.0
            else -> prices.median()
        }
        val averageValidatorRewardPercentage = apyByCalculationTargets.values.average()
        return (averageValidatorRewardPercentage * (1 - medianCommission)).also {
            require(it.isFinite() && it >= 0.0) { "SORA expected return is not representable" }
        }
    }

    private fun calculateValidatorAPY(validator: RewardCalculationTarget): Double {
        val validatorHistoricalRewardDistribution = historicalRewardDistribution.values.asSequence().map { it.individual }
            .flatten()
            .filter { it.accountId.contentEquals(validator.accountIdHex.fromHex()) }
            .toList()

        val averageValidatorRewardPoints = if(validatorHistoricalRewardDistribution.isEmpty()){
            0.0
        } else {
            validatorHistoricalRewardDistribution.map { it.rewardPoints.toDouble() }.average()
        }

        require(averageValidatorRewardPoints.isFinite()) { "SORA validator reward points are not representable" }
        val validatorOwnStake = asset.amountFromPlanks(validator.totalStake).toDouble()
        require(validatorOwnStake.isFinite() && validatorOwnStake > 0.0) { "SORA validator stake is not representable" }

        val portion = averageValidatorRewardPoints / averageTotalPoints
        val averageValidatorRewardInVal = averageValidatorPayout * portion
        val ownStakeInVal = validatorOwnStake * xorValRate
        require(ownStakeInVal.isFinite() && ownStakeInVal > 0.0) { "SORA converted stake is not representable" }
        val result =
            averageValidatorRewardInVal / ownStakeInVal * (1 - validator.commission.toDouble())

        return (result * ERAS_PER_DAY * DAYS_IN_YEAR).also {
            require(it.isFinite() && it >= 0.0) { "SORA validator return is not representable" }
        }
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
        require(amount.signum() >= 0) { "SORA estimate amount is invalid" }
        val convertedAmount = amount.toDouble()
        require(amount.signum() == 0 || convertedAmount > 0.0) { "SORA estimate amount is not representable" }
        val dailyPercentage = maxAPY / DAYS_IN_YEAR
        calculateReward(convertedAmount, days, dailyPercentage)
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
        require(amount.isFinite() && amount >= 0.0) { "SORA estimate amount is invalid" }
        require(days >= 0) { "SORA estimate period is invalid" }
        require(dailyPercentage.isFinite() && dailyPercentage >= 0.0) { "SORA daily return is invalid" }
        val gainAmount = amount.toBigDecimal() * dailyPercentage.toBigDecimal() * days.toBigDecimal()

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
