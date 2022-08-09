package jp.co.soramitsu.featurestakingimpl.domain.rewards

import jp.co.soramitsu.featurestakingapi.data.StakingSharedState
import jp.co.soramitsu.featurestakingapi.domain.api.AccountIdMap
import jp.co.soramitsu.featurestakingapi.domain.api.StakingRepository
import jp.co.soramitsu.featurestakingapi.domain.model.Exposure
import jp.co.soramitsu.featurestakingapi.domain.model.ValidatorPrefs
import jp.co.soramitsu.featurestakingimpl.data.network.subquery.StakingApi
import jp.co.soramitsu.featurestakingimpl.domain.error.accountIdNotFound
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.featurestakingimpl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.featurestakingimpl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.featurestakingimpl.scenarios.relaychain.getActiveElectedValidatorsExposures
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardCalculatorFactory(
    private val relayChainRepository: StakingRelayChainScenarioRepository,
    private val stakingRepository: StakingRepository,
    private val sharedState: StakingSharedState,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val stakingApi: StakingApi
) {

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

    suspend fun createManual(chainId: ChainId): ManualRewardCalculator = withContext(Dispatchers.Default) {
        val exposures = relayChainRepository.getActiveElectedValidatorsExposures(chainId)
        val validatorsPrefs = relayChainRepository.getValidatorPrefs(chainId, exposures.keys.toList())

        createManual(exposures, validatorsPrefs, chainId)
    }

    fun createSubquery(): SubqueryRewardCalculator {
        return SubqueryRewardCalculator(stakingRepository, stakingScenarioInteractor as? StakingParachainScenarioInteractor, stakingApi)
    }

    suspend fun create(stakingType: Chain.Asset.StakingType, chainId: ChainId): RewardCalculator {
        return when (stakingType) {
            Chain.Asset.StakingType.UNSUPPORTED -> error("wrong staking type")
            Chain.Asset.StakingType.RELAYCHAIN -> createManual(chainId)
            Chain.Asset.StakingType.PARACHAIN -> createSubquery()
        }
    }
}
