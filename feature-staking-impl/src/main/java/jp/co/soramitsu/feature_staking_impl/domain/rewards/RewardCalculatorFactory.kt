package jp.co.soramitsu.feature_staking_impl.domain.rewards

import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.StakingApi
import jp.co.soramitsu.feature_staking_impl.domain.error.accountIdNotFound
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.getActiveElectedValidatorsExposures
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardCalculatorFactory(
    private val relayChainRepository: StakingRelayChainScenarioRepository,
    private val stakingRepository: StakingRepository,
    private val sharedState: StakingSharedState,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val stakingApi: StakingApi,
) {

    suspend fun createManual(
        exposures: AccountIdMap<Exposure>,
        validatorsPrefs: AccountIdMap<ValidatorPrefs?>
    ): ManualRewardCalculator = withContext(Dispatchers.Default) {
        val chainId = sharedState.chainId()

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

    suspend fun createManual(): ManualRewardCalculator = withContext(Dispatchers.Default) {
        val chainId = sharedState.chainId()

        val exposures = relayChainRepository.getActiveElectedValidatorsExposures(chainId)
        val validatorsPrefs = relayChainRepository.getValidatorPrefs(chainId, exposures.keys.toList())

        createManual(exposures, validatorsPrefs)
    }

    fun createSubquery(): SubqueryRewardCalculator {
        return SubqueryRewardCalculator(stakingRepository, stakingScenarioInteractor as? StakingParachainScenarioInteractor, stakingApi)
    }

    suspend fun create(stakingType: Chain.Asset.StakingType): RewardCalculator {
        return when (stakingType) {
            Chain.Asset.StakingType.UNSUPPORTED -> error("wrong staking type")
            Chain.Asset.StakingType.RELAYCHAIN -> createManual()
            Chain.Asset.StakingType.PARACHAIN -> createSubquery()
        }
    }
}
