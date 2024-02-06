package jp.co.soramitsu.staking.impl.domain.rewards

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.domain.error.accountIdNotFound
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.getActiveElectedValidatorsExposures
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardCalculatorFactory(
    private val relayChainRepository: StakingRelayChainScenarioRepository,
    private val stakingRepository: StakingRepository,
    private val soraStakingRewardsScenario: SoraStakingRewardsScenario,
    stakingScenarioInteractor: StakingParachainScenarioInteractor,
    stakingApi: StakingApi
) {

    private val subqueryCalculator =
        SubqueryRewardCalculator(stakingRepository, stakingScenarioInteractor, stakingApi)

    suspend fun createManual(
        chainId: String,
        calculationTargets: List<RewardCalculationTarget>
    ): ManualRewardCalculator = withContext(Dispatchers.Default) {
        val totalIssuance = stakingRepository.getTotalIssuance(chainId)

        ManualRewardCalculator(
            validators = calculationTargets,
            totalIssuance = totalIssuance
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

        val validatorsPayouts =
            relayChainRepository.getErasValidatorRewards(chainId).values.filterNotNull()
                .map { asset.amountFromPlanks(it).toDouble() }

        val averageValidatorPayout: Double = validatorsPayouts.average()
        val rateInPlanks = soraStakingRewardsScenario.mainAssetToRewardAssetRate()
        val rate = asset.amountFromPlanks(rateInPlanks).toDouble()
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
        val electedExposures = relayChainRepository.getActiveElectedValidatorsExposures(chainId)

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
