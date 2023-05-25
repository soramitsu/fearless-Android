package jp.co.soramitsu.staking.impl.domain.rewards

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.api.domain.api.AccountIdMap
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.Exposure
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
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

    private val subqueryCalculator = SubqueryRewardCalculator(stakingRepository, stakingScenarioInteractor, stakingApi)
    private val calculators: MutableMap<ChainId, RewardCalculator> = mutableMapOf()

    suspend fun createManual(
        exposures: AccountIdMap<Exposure>,
        validatorsPrefs: AccountIdMap<ValidatorPrefs?>,
        chainId: String
    ): ManualRewardCalculator = withContext(Dispatchers.Default) {
        val totalIssuance = stakingRepository.getTotalIssuance(chainId)

        val validators = exposures.keys.mapNotNull { accountIdHex ->
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

        ManualRewardCalculator(
            validators = validators,
            totalIssuance = totalIssuance
        )
    }

    suspend fun createSoraWithCustomValidatorsSettings(
        exposures: AccountIdMap<Exposure>,
        validatorsPrefs: AccountIdMap<ValidatorPrefs?>,
        asset: Asset
    ): RewardCalculator = withContext(Dispatchers.Default) {
        val chainId = asset.chainId

        val cached = calculators[chainId]
        if (cached != null) {
            return@withContext cached
        }

        val validatorsPayouts = relayChainRepository.getErasValidatorRewards(chainId).values.filterNotNull().map { asset.amountFromPlanks(it).toDouble() }
        val averageValidatorPayout: Double = validatorsPayouts.average()

        val validators = exposures.keys.mapNotNull { accountIdHex ->
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

        val rateInPlanks = soraStakingRewardsScenario.mainAssetToRewardAssetRate()
        val rate = asset.amountFromPlanks(rateInPlanks).toDouble()
        val calculator = SoraRewardCalculator(
            validators = validators,
            xorValRate = rate,
            averageValidatorPayout = averageValidatorPayout,
            asset = asset
        )

        calculators[chainId] = calculator

        return@withContext calculator
    }

    private suspend fun createManual(chainId: ChainId): RewardCalculator = withContext(Dispatchers.Default) {
        val cached = calculators[chainId]
        if (cached != null) {
            return@withContext cached as ManualRewardCalculator
        }
        val exposures = relayChainRepository.getActiveElectedValidatorsExposures(chainId)
        val validatorsPrefs = relayChainRepository.getValidatorPrefs(chainId, exposures.keys.toList())

        val calculator = createManual(exposures, validatorsPrefs, chainId)
        calculators[chainId] = calculator
        calculator
    }

    fun createSubquery(): SubqueryRewardCalculator {
        return subqueryCalculator
    }

    private suspend fun createSora(asset: Asset): RewardCalculator {
        val chainId = asset.chainId

        val cached = calculators[chainId]
        if (cached != null) {
            return cached
        }

        val validatorsPayouts = relayChainRepository.getErasValidatorRewards(chainId).values.filterNotNull().map { asset.amountFromPlanks(it).toDouble() }
        val averageValidatorPayout: Double = validatorsPayouts.average()

        val exposures = relayChainRepository.getActiveElectedValidatorsExposures(chainId)
        val validatorsPrefs = relayChainRepository.getValidatorPrefs(chainId, exposures.keys.toList())

        val validators = exposures.keys.mapNotNull { accountIdHex ->
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

        val rateInPlanks = soraStakingRewardsScenario.mainAssetToRewardAssetRate()
        val rate = asset.amountFromPlanks(rateInPlanks).toDouble()
        val calculator = SoraRewardCalculator(
            validators = validators,
            xorValRate = rate,
            averageValidatorPayout = averageValidatorPayout,
            asset = asset
        )

        calculators[chainId] = calculator

        return calculator
    }

    suspend fun create(asset: Asset): RewardCalculator {
        val stakingType = asset.staking
        val chainId = asset.chainId
        val syntheticType = asset.syntheticStakingType()

        return when {
            syntheticType == SyntheticStakingType.SORA -> createSora(asset)
            stakingType == Asset.StakingType.RELAYCHAIN -> createManual(chainId)
            stakingType == Asset.StakingType.PARACHAIN -> createSubquery()

            stakingType == Asset.StakingType.UNSUPPORTED -> error("wrong staking type")
            else -> error("wrong staking type")
        }
    }
}
