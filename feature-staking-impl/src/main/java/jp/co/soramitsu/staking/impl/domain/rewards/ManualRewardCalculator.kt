package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.median
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PARACHAINS_ENABLED = false

private const val MINIMUM_INFLATION = 0.025

private val INFLATION_IDEAL = if (PARACHAINS_ENABLED) 0.2 else 0.1
private val STAKED_PORTION_IDEAL = if (PARACHAINS_ENABLED) 0.5 else 0.75

private val INTEREST_IDEAL = INFLATION_IDEAL / STAKED_PORTION_IDEAL

private const val DECAY_RATE = 0.05

const val DAYS_IN_YEAR = 365

/**
 * [annualStakerRewards] replaces the relay inflation model with the observed era budget, annualized: an Asset Hub
 * pays stakers from what the relay chain sends it, not from inflation of its own issuance.
 */
open class ManualRewardCalculator(
    val validators: List<RewardCalculationTarget>,
    val totalIssuance: BigInteger,
    private val annualStakerRewards: BigDecimal? = null
) : RewardCalculator {

    private val totalStaked =
        validators.sumByBigInteger(RewardCalculationTarget::totalStake).toDouble()

    private val stakedPortion = totalStaked / totalIssuance.toDouble()

    private val yearlyInflation = if (annualStakerRewards == null) calculateYearlyInflation() else 0.0

    private val averageValidatorStake = totalStaked / validators.size

    private val averageValidatorRewardPercentage = (
        annualStakerRewards?.let {
            require(it.signum() >= 0) { "Negative staking reward budget" }
            it.toDouble() / totalStaked
        } ?: (yearlyInflation / stakedPortion)
    ).validRewardRateOrZero()

    private val apyByValidator = validators.associateBy(
        keySelector = RewardCalculationTarget::accountIdHex,
        valueTransform = ::calculateValidatorAPY
    )

    private val expectedAPY = calculateExpectedAPY()

    private fun calculateExpectedAPY(): Double {
        val prices = validators.map { it.commission.toDouble() }.filter { it < 1.0 }

        val medianCommission = when {
            prices.isEmpty() -> 0.0
            else -> prices.median()
        }

        return annualStakerRewards?.let {
            observedRewardRate(validators.sumByBigInteger(RewardCalculationTarget::totalStake), medianCommission.toBigDecimal())
        } ?: (averageValidatorRewardPercentage * (1 - medianCommission)).validRewardRateOrZero()
    }

    private fun calculateValidatorAPY(validator: RewardCalculationTarget): Double {
        if (annualStakerRewards != null) {
            return observedRewardRate(validator.totalStake * validators.size.toBigInteger(), validator.commission)
        }
        val yearlyRewardPercentage =
            averageValidatorRewardPercentage * averageValidatorStake / validator.totalStake.toDouble()

        return (yearlyRewardPercentage * (1 - validator.commission.toDouble())).validRewardRateOrZero()
    }

    private fun observedRewardRate(stake: BigInteger, commission: BigDecimal): Double {
        if (stake.signum() <= 0) return 0.0
        return requireNotNull(annualStakerRewards).divide(stake.toBigDecimal(), java.math.MathContext.DECIMAL128)
            .multiply(BigDecimal.ONE - commission).toDouble().validRewardRateOrZero()
    }

    private fun calculateYearlyInflation(): Double {
        return MINIMUM_INFLATION + if (stakedPortion in 0.0..STAKED_PORTION_IDEAL) {
            stakedPortion * (INTEREST_IDEAL - MINIMUM_INFLATION / STAKED_PORTION_IDEAL)
        } else {
            (INTEREST_IDEAL * STAKED_PORTION_IDEAL - MINIMUM_INFLATION) * 2.0.pow((STAKED_PORTION_IDEAL - stakedPortion) / DECAY_RATE)
        }
    }

    private val maxAPY = apyByValidator.values.maxOrNull() ?: 0.0

    override suspend fun calculateMaxAPY(chainId: ChainId) = calculateReturns(
        amount = BigDecimal.ONE,
        days = DAYS_IN_YEAR,
        isCompound = true,
        chainId = chainId
    ).gainPercentage

    override suspend fun calculateAvgAPY(): BigDecimal {
        return runCatching { expectedAPY.toBigDecimal().fractionToPercentage() }.getOrNull()
            .orZero()
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

    protected open fun calculateReward(
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
