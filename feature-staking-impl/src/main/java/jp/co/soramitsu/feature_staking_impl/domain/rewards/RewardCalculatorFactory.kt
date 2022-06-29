package jp.co.soramitsu.feature_staking_impl.domain.rewards

import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.getActiveElectedValidatorsExposures
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.error.accountIdNotFound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardCalculatorFactory(
    private val stakingRepository: StakingRepository,
    private val sharedState: StakingSharedState,
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

        val exposures = stakingRepository.getActiveElectedValidatorsExposures(chainId)
        val validatorsPrefs = stakingRepository.getValidatorPrefs(chainId, exposures.keys.toList())

        createManual(exposures, validatorsPrefs)
    }

    fun createSubquery(): SubqueryRewardCalculator {
        return SubqueryRewardCalculator()
    }
}
