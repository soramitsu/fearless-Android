package jp.co.soramitsu.feature_staking_impl.domain.rewards

import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.median
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingAllCollatorsApyRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingCollatorsApyRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingLastRoundIdRequest
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.pow

private const val PARACHAINS_ENABLED = false

private const val MINIMUM_INFLATION = 0.025

private val INFLATION_IDEAL = if (PARACHAINS_ENABLED) 0.2 else 0.1
private val STAKED_PORTION_IDEAL = if (PARACHAINS_ENABLED) 0.5 else 0.75

private val INTEREST_IDEAL = INFLATION_IDEAL / STAKED_PORTION_IDEAL

private const val DECAY_RATE = 0.05

const val DAYS_IN_YEAR = 365

class PeriodReturns(
    val gainAmount: BigDecimal,
    val gainPercentage: BigDecimal
)

class ManualRewardCalculator(
    val validators: List<RewardCalculationTarget>,
    val totalIssuance: BigInteger
) : RewardCalculator {

    private val totalStaked = validators.sumByBigInteger(RewardCalculationTarget::totalStake).toDouble()

    private val stakedPortion = totalStaked / totalIssuance.toDouble()

    private val yearlyInflation = calculateYearlyInflation()

    private val averageValidatorStake = totalStaked / validators.size

    private val averageValidatorRewardPercentage = yearlyInflation / stakedPortion

    private val apyByValidator = validators.associateBy(
        keySelector = RewardCalculationTarget::accountIdHex,
        valueTransform = ::calculateValidatorAPY
    )

    private val expectedAPY = calculateExpectedAPY()

    private fun calculateExpectedAPY(): Double {
        val prices = validators.map { it.commission.toDouble() }

        val medianCommission = when {
            prices.isEmpty() -> 0.0
            else -> prices.median()
        }

        return averageValidatorRewardPercentage * (1 - medianCommission)
    }

    private fun calculateValidatorAPY(validator: RewardCalculationTarget): Double {
        val yearlyRewardPercentage = averageValidatorRewardPercentage * averageValidatorStake / validator.totalStake.toDouble()

        return yearlyRewardPercentage * (1 - validator.commission.toDouble())
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

    override fun calculateAvgAPY() = expectedAPY.toBigDecimal().fractionToPercentage()

    override fun getApyFor(targetIdHex: String): BigDecimal {
        val apy = apyByValidator[targetIdHex] ?: expectedAPY

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
        val validatorAPY = apyByValidator[targetIdHex] ?: error("Validator with $targetIdHex was not found")
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

    private fun calculateSimpleReward(amount: Double, days: Int, dailyPercentage: Double): BigDecimal {
        return amount.toBigDecimal() * dailyPercentage.toBigDecimal() * days.toBigDecimal()
    }

    private fun calculateCompoundReward(amount: Double, days: Int, dailyPercentage: Double): BigDecimal {
        return amount.toBigDecimal() * ((1 + dailyPercentage).toBigDecimal().pow(days)) - amount.toBigDecimal()
    }
}

interface RewardCalculator {

    suspend fun calculateMaxAPY(chainId: ChainId): BigDecimal

    fun calculateAvgAPY(): BigDecimal

    fun getApyFor(targetIdHex: String): BigDecimal

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

class SubqueryRewardCalculator(
    private val stakingRepository: StakingRepository,
    private val stakingParachainScenarioInteractor: StakingParachainScenarioInteractor?,
    private val stakingApi: StakingApi,
) : RewardCalculator {

    private var avgApr = BigDecimal.ZERO

    override suspend fun calculateMaxAPY(chainId: ChainId): BigDecimal {
        val chain = stakingParachainScenarioInteractor?.getStakingStateFlow()?.first()?.chain
        val stakingUrl = chain?.externalApi?.staking?.url // todo add other urls to utils
        if (stakingUrl == null || chain.externalApi?.staking?.type != Chain.ExternalApi.Section.Type.SUBQUERY) {
            hashCode()
            throw Exception("Staking for this network is not supported yet")
        }
        val roundId = stakingApi.getLastRoundId(stakingUrl, StakingLastRoundIdRequest()).data.rounds.nodes.firstOrNull()?.id?.toIntOrNull()
        val previousRoundId = roundId?.dec()
        val collatorsApyRequest = StakingAllCollatorsApyRequest(previousRoundId)
        return stakingApi.getAllCollatorsApy(stakingUrl, collatorsApyRequest).data.collatorRounds.nodes.mapNotNull { element ->
            element.collatorId?.let { it.fromHex().toHexString(false) to element.apr }
        }.toMap().maxOf { it.value ?: BigDecimal.ZERO }.fractionToPercentage()
    }

    override fun calculateAvgAPY(): BigDecimal {
        return avgApr.fractionToPercentage()
    }

    override fun getApyFor(targetIdHex: String): BigDecimal {
        return BigDecimal.ZERO
    }

    override suspend fun calculateReturns(amount: BigDecimal, days: Int, isCompound: Boolean, chainId: ChainId): PeriodReturns {
        val totalIssuance = stakingRepository.getTotalIssuance(chainId)
        val staked = stakingParachainScenarioInteractor?.getStaked(chainId)?.getOrNull()
        val rewardsAmountPart = BigDecimal(0.025)
        val currentApy = if (staked != null && staked > BigInteger.ZERO) {
            totalIssuance.toBigDecimal() * rewardsAmountPart / staked.toBigDecimal()
        } else {
            BigDecimal.ZERO
        }
        avgApr = currentApy
        val gainAmount = amount * currentApy

        return PeriodReturns(gainAmount, currentApy.fractionToPercentage())
    }

    override suspend fun calculateReturns(amount: Double, days: Int, isCompound: Boolean, targetIdHex: String): PeriodReturns {
        return PeriodReturns(BigDecimal.ZERO, BigDecimal.ZERO)
    }

    suspend fun getApy(selectedCandidates: List<ByteArray>): Map<String, BigDecimal?> {
        val chain = stakingParachainScenarioInteractor?.getStakingStateFlow()?.first()?.chain
        val stakingUrl = chain?.externalApi?.staking?.url
        if (stakingUrl == null || chain.externalApi?.staking?.type != Chain.ExternalApi.Section.Type.SUBQUERY) {
            throw Exception("Staking for this network is not supported yet")
        }
        val roundId = stakingApi.getLastRoundId(stakingUrl, StakingLastRoundIdRequest()).data.rounds.nodes.firstOrNull()?.id?.toIntOrNull()
        val previousRoundId = roundId?.dec()
        val collatorsApyRequest = StakingCollatorsApyRequest(selectedCandidates, previousRoundId)
        return stakingApi.getCollatorsApy(stakingUrl, collatorsApyRequest).data.collatorRounds.nodes.mapNotNull { element ->
            element.collatorId?.let { it.fromHex().toHexString(false) to element.apr }
        }.toMap()
    }
}
