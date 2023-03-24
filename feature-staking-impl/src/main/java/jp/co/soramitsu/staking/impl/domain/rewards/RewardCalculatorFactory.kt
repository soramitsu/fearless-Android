package jp.co.soramitsu.staking.impl.domain.rewards

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.domain.api.AccountIdMap
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.Exposure
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.domain.error.accountIdNotFound
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.getActiveElectedValidatorsExposures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardCalculatorFactory(
    private val relayChainRepository: StakingRelayChainScenarioRepository,
    private val stakingRepository: StakingRepository,
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

    suspend fun createManual(chainId: ChainId): RewardCalculator = withContext(Dispatchers.Default) {
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

    suspend fun create(stakingType: Asset.StakingType, chainId: ChainId): RewardCalculator {
        return when (stakingType) {
            Asset.StakingType.UNSUPPORTED -> error("wrong staking type")
            Asset.StakingType.RELAYCHAIN -> createManual(chainId)
            Asset.StakingType.PARACHAIN -> createSubquery()
        }
    }
}
