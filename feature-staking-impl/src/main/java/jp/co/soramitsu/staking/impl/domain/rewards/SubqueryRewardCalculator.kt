package jp.co.soramitsu.staking.impl.domain.rewards

import android.util.Log
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.percentageToFraction
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingAllCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingLastRoundIdRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidCollatorsApyRequest
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import kotlinx.coroutines.flow.first

class SubqueryRewardCalculator(
    private val stakingRepository: StakingRepository,
    private val stakingParachainScenarioInteractor: StakingParachainScenarioInteractor?,
    private val stakingApi: StakingApi
) : RewardCalculator {

    private var avgApr = BigDecimal.ZERO

    override suspend fun calculateMaxAPY(chainId: ChainId): BigDecimal {
        val chain = stakingParachainScenarioInteractor?.stakingStateFlow?.first()?.chain
        val stakingUrl = chain?.externalApi?.staking?.url
        val stakingType = chain?.externalApi?.staking?.type

        return when {
            stakingUrl == null -> throw Exception("Staking for this network is not supported yet")
            stakingType == Chain.ExternalApi.Section.Type.SUBQUERY -> {
                calculateSubqueryMaxAPY(stakingUrl)
            }
            stakingType == Chain.ExternalApi.Section.Type.SUBSQUID -> {
                calculateSubsquidMaxAPY(stakingUrl)
            }
            else -> throw Exception("Staking for this network is not supported yet")
        }
    }

    private suspend fun calculateSubsquidMaxAPY(stakingUrl: String): BigDecimal {
        return getSubsquidRewards(stakingUrl).maxOf { it.value.orZero() }.fractionToPercentage()
    }

    private suspend fun calculateSubqueryMaxAPY(stakingUrl: String): BigDecimal {
        val roundId =
            kotlin.runCatching { stakingApi.getLastRoundId(stakingUrl, StakingLastRoundIdRequest()).data.rounds.nodes.firstOrNull()?.id?.toIntOrNull() }
                .getOrNull()
        val previousRoundId = roundId?.dec()
        val collatorsApyRequest = StakingAllCollatorsApyRequest(previousRoundId)

        val response = runCatching { stakingApi.getAllCollatorsApy(stakingUrl, collatorsApyRequest) }
        return response.fold({
            it.data.collatorRounds.nodes.mapNotNull { element ->
                element.collatorId?.let { it.fromHex().toHexString(false) to element.apr }
            }.toMap().maxOf { it.value ?: BigDecimal.ZERO }.fractionToPercentage()
        }, {
            Log.e(
                "SubqueryRewardCalculator::calculateMaxAPY",
                "SubqueryRewardCalculator::calculateMaxAPY error: ${it.localizedMessage ?: it.message}"
            )
            BigDecimal.ZERO
        })
    }

    override fun calculateAvgAPY(): BigDecimal {
        return avgApr.fractionToPercentage()
    }

    override suspend fun getApyFor(targetId: ByteArray): BigDecimal {
        return getApy(listOf(targetId))[targetId.toHexString()].orZero()
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
        val apyByPeriod = currentApy * days.toBigDecimal() / DAYS_IN_YEAR.toBigDecimal()
        val gainAmount = amount * apyByPeriod

        return PeriodReturns(gainAmount, apyByPeriod.fractionToPercentage())
    }

    override suspend fun calculateReturns(amount: Double, days: Int, isCompound: Boolean, targetIdHex: String): PeriodReturns {
        return PeriodReturns(BigDecimal.ZERO, BigDecimal.ZERO)
    }

    suspend fun getApy(selectedCandidates: List<ByteArray>): Map<String, BigDecimal?> {
        val chain = stakingParachainScenarioInteractor?.stakingStateFlow?.first()?.chain
        val stakingUrl = chain?.externalApi?.staking?.url
        val stakingType = chain?.externalApi?.staking?.type

        return when {
            stakingUrl == null -> throw Exception("Staking for this network is not supported yet")
            stakingType == Chain.ExternalApi.Section.Type.SUBSQUID -> {
                getSubsquidRewards(stakingUrl, selectedCandidates)
            }
            stakingType == Chain.ExternalApi.Section.Type.SUBQUERY -> {
                getSubqueryRewards(stakingUrl, selectedCandidates)
            }
            else -> throw Exception("Staking for this network is not supported yet")
        }
    }

    private suspend fun getSubsquidRewards(stakingUrl: String, selectedCandidates: List<ByteArray>? = null): Map<String, BigDecimal?> {
        val collatorsApyRequest = SubsquidCollatorsApyRequest()
        val response = runCatching {
            stakingApi.getCollatorsApy(stakingUrl, collatorsApyRequest)
        }
        val collatorApyMap = response.fold({
            it.data.stakers.mapNotNull { element ->
                element.stashId?.let { it.fromHex().toHexString(false) to element.apr24h?.percentageToFraction() }
            }.toMap()
        }, {
            Log.e(
                "GetSubsquidRewards",
                "GetSubsquidRewards::getApy error: ${it.localizedMessage ?: it.message}"
            )
            emptyMap()
        })

        return if (selectedCandidates == null) {
            collatorApyMap
        } else {
            val candidateAddresses = selectedCandidates.map { it.toHexString(false) }
            collatorApyMap.filter { it.key in candidateAddresses }
        }
    }

    private suspend fun getSubqueryRewards(
        stakingUrl: String,
        selectedCandidates: List<ByteArray>
    ): Map<String, BigDecimal?> {
        val roundId = runCatching {
            stakingApi.getLastRoundId(stakingUrl, StakingLastRoundIdRequest()).data.rounds.nodes.firstOrNull()?.id?.toIntOrNull()
        }.getOrNull()
        val previousRoundId = roundId?.dec()
        val collatorsApyRequest = StakingCollatorsApyRequest(selectedCandidates, previousRoundId)
        val response = runCatching { stakingApi.getCollatorsApy(stakingUrl, collatorsApyRequest) }
        return response.fold({
            it.data.collatorRounds.nodes.mapNotNull { element ->
                element.collatorId?.let { it.fromHex().toHexString(false) to element.apr }
            }.toMap()
        }, {
            Log.e(
                "SubqueryRewardCalculator::getApy",
                "SubqueryRewardCalculator::getApy error: ${it.localizedMessage ?: it.message}"
            )
            emptyMap()
        })
    }
}
