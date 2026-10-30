package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.domain.error.accountIdNotFound
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardCalculatorFactory(
    private val relayChainRepository: StakingRelayChainScenarioRepository,
    private val stakingRepository: StakingRepository,
    private val soraStakingRewardsScenario: SoraStakingRewardsScenario,
    private val stakingScenarioInteractor: StakingParachainScenarioInteractor,
    stakingApi: StakingApi
) {

    private val subqueryCalculator =
        SubqueryRewardCalculator(stakingRepository, stakingScenarioInteractor, stakingApi)

    suspend fun createManual(
        chainId: String,
        calculationTargets: List<RewardCalculationTarget>
    ): ManualRewardCalculator = withContext(Dispatchers.Default) {
        val annualRewards = if (AssetHubStaking.isHub(chainId)) {
            val era = relayChainRepository.getActiveEraIndex(chainId)
            val rewards = relayChainRepository.getErasValidatorRewards(chainId)
                .filterKeys { it < era }.toSortedMap().values.filterNotNull().takeLast(REWARD_HISTORY_ERAS)
            require(rewards.isNotEmpty()) { "Completed Asset Hub era rewards are unavailable" }
            val eraMillis = relayChainRepository.sessionLength(chainId) *
                relayChainRepository.blockCreationTime(chainId) * relayChainRepository.eraLength(chainId)
            annualStakingRewards(rewards, eraMillis)
        } else {
            null
        }
        val totalIssuance = if (annualRewards == null) stakingRepository.getTotalIssuance(chainId) else BigInteger.ONE

        ManualRewardCalculator(
            validators = calculationTargets,
            totalIssuance = totalIssuance,
            annualStakerRewards = annualRewards
        )
    }

    fun createSubquery(): SubqueryRewardCalculator {
        return subqueryCalculator
    }

    suspend fun createSora(
        asset: Asset,
        allValidators: List<RewardCalculationTarget>,
        calculationTargets: List<String>? = null
    ): RewardCalculator {
        val chainId = asset.chainId

        require(asset.precision in 0..255) { "SORA asset precision is invalid" }
        val validatorsPayouts =
            relayChainRepository.getErasValidatorRewards(chainId).values.filterNotNull()
                .map {
                    require(it.signum() >= 0) { "SORA validator payout is invalid" }
                    asset.amountFromPlanks(it).toDouble().also { payout ->
                        require(payout.isFinite()) { "SORA validator payout is not representable" }
                    }
                }
        require(validatorsPayouts.isNotEmpty()) { "SORA validator payouts are unavailable" }
        val averageValidatorPayout: Double = validatorsPayouts.average()
        require(averageValidatorPayout.isFinite()) { "SORA average validator payout is not representable" }
        val rateInPlanks = soraStakingRewardsScenario.mainAssetToRewardAssetRate()
        val rate = asset.amountFromPlanks(rateInPlanks).toDouble()
        require(rate.isFinite() && rate > 0.0) { "SORA conversion rate is invalid" }
        val historicalRewardDistribution = relayChainRepository.retrieveEraPointsDistribution(chainId)

        return SoraRewardCalculator(
            validators = allValidators,
            xorValRate = rate,
            averageValidatorPayout = averageValidatorPayout,
            asset = asset,
            calculationTargets = calculationTargets ?: allValidators.map { it.accountIdHex },
            historicalRewardDistribution
        )
    }

    suspend fun createReef(
        asset: Asset,
        allValidators: List<RewardCalculationTarget>,
        calculationTargets: List<String>? = null
    ): RewardCalculator {
        val chainId = asset.chainId

        val validatorsPayouts =
            relayChainRepository.getErasValidatorRewards(chainId)
                .filterValues { it != null }
                .cast<Map<BigInteger, BigInteger>>()
                .mapValues { asset.amountFromPlanks(it.value).toDouble() }

        val historicalRewardDistribution = relayChainRepository.retrieveEraPointsDistribution(chainId)
        val last14 = historicalRewardDistribution.keys.sortedByDescending { it.toDouble() }.take(14)
            .associateWith { requireNotNull(historicalRewardDistribution[it]) }

        val amount = BigDecimal.ONE

        return ReefRewardCalculator(
            amount = amount,
            validators = allValidators,
            erasPayouts = validatorsPayouts,
            asset = asset,
            calculationTargets = calculationTargets ?: allValidators.map { it.accountIdHex },
            last14
        )
    }

    suspend fun createTernoa(
        asset: Asset,
        calculationTargets: List<RewardCalculationTarget>
    ): RewardCalculator {
        val chainId = asset.chainId
        require(chainId == ternoaChainId)

        val validatorsPayouts =
            relayChainRepository.getErasValidatorRewards(chainId).values.filterNotNull()
                .map { asset.amountFromPlanks(it).toDouble() }

        val averageValidatorPayout: Double = validatorsPayouts.average()

        return TernoaRewardCalculator(
            validators = calculationTargets,
            averageValidatorPayout = averageValidatorPayout,
            asset = asset
        )
    }

    suspend fun create(asset: Asset): RewardCalculator {
        val stakingType = asset.staking
        val chainId = asset.chainId
        val syntheticType = asset.syntheticStakingType()
        val calculationTargets =
            getRewardCalculationTargets(asset)
        return when {
            syntheticType == SyntheticStakingType.SORA -> createSora(asset, calculationTargets)
            syntheticType == SyntheticStakingType.TERNOA -> createTernoa(asset, calculationTargets)
            syntheticType == SyntheticStakingType.REEF -> createReef(asset, calculationTargets)
            stakingType == Asset.StakingType.RELAYCHAIN -> createManual(chainId, calculationTargets)
            stakingType == Asset.StakingType.PARACHAIN -> createSubquery()

            stakingType == Asset.StakingType.UNSUPPORTED -> error("wrong staking type")
            else -> error("wrong staking type")
        }
    }

    suspend fun createWithValidators(asset: Asset, validators: List<AccountId>): RewardCalculator {
        val stakingType = asset.staking
        val chainId = asset.chainId
        val syntheticType = asset.syntheticStakingType()

        val allElectedValidators = getRewardCalculationTargets(asset)

        return when {
            syntheticType == SyntheticStakingType.SORA -> createSora(
                asset,
                allElectedValidators,
                validators.map { it.toHexString() })

            syntheticType == SyntheticStakingType.REEF -> createReef(
                asset,
                allElectedValidators,
                validators.map { it.toHexString() })

            syntheticType == SyntheticStakingType.TERNOA -> createTernoa(
                asset,
                allElectedValidators
            )

            stakingType == Asset.StakingType.RELAYCHAIN -> createManual(
                chainId,
                allElectedValidators
            )

            stakingType == Asset.StakingType.PARACHAIN -> createSubquery()

            stakingType == Asset.StakingType.UNSUPPORTED -> error("wrong staking type")
            else -> error("wrong staking type")
        }
    }

    private suspend fun getRewardCalculationTargets(
        asset: Asset,
        validators: List<String>? = null
    ): List<RewardCalculationTarget> {
        val chainId = asset.chainId
        val electedExposures = relayChainRepository.getLegacyActiveElectedValidatorsExposures(chainId)

        val exposures = validators?.let { electedExposures.filter { validators.contains(it.key) } }
            ?: electedExposures

        val prefsKeys = exposures.keys + validators.orEmpty()
        val validatorsPrefs =
            relayChainRepository.getValidatorPrefs(chainId, prefsKeys.toList())

        return exposures.keys.mapNotNull { accountIdHex ->
            val exposure = exposures[accountIdHex] ?: accountIdNotFound(accountIdHex)
            val validatorPrefs = validatorsPrefs[accountIdHex] ?: return@mapNotNull null

            RewardCalculationTarget(
                accountIdHex = accountIdHex,
                totalStake = exposure.total,
                nominatorStakes = exposure.others,
                ownStake = exposure.own,
                commission = validatorPrefs.commission
            )
        }
    }
}

private const val REWARD_HISTORY_ERAS = 14

/** Observed completed-era budget, annualized using the relay clock; not an inflation forecast. */
internal fun annualStakingRewards(rewards: List<BigInteger>, eraMillis: BigInteger): BigDecimal {
    require(rewards.isNotEmpty() && rewards.all { it.signum() >= 0 } && eraMillis.signum() > 0)
    val annualMillis = 31_536_000_000L.toBigDecimal()
    return rewards.fold(BigInteger.ZERO, BigInteger::add).toBigDecimal()
        .multiply(annualMillis)
        .divide(rewards.size.toBigDecimal().multiply(eraMillis.toBigDecimal()), java.math.MathContext.DECIMAL128)
}
