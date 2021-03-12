package jp.co.soramitsu.feature_staking_impl.domain.rewards

import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.domain.error.accountIdNotFound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardCalculatorFactory(
    private val stakingRepository: StakingRepository
) {

    suspend fun create(
        exposures: AccountIdMap<Exposure>,
        validatorsPrefs: AccountIdMap<ValidatorPrefs>
    ): RewardCalculator = withContext(Dispatchers.Default) {
        val totalIssuance = stakingRepository.getTotalIssuance()

        val validators = exposures.keys.map { accountIdHex ->
            val exposure = exposures[accountIdHex] ?: accountIdNotFound(accountIdHex)
            val commission = validatorsPrefs[accountIdHex] ?: accountIdNotFound(accountIdHex)

            RewardCalculationTarget(
                accountIdHex = accountIdHex,
                totalStake = exposure.total,
                nominatorStakes = exposure.others,
                ownStake = exposure.own,
                commission = commission
            )
        }

        RewardCalculator(
            validators = validators,
            totalIssuance = totalIssuance
        )
    }

    suspend fun create(): RewardCalculator = withContext(Dispatchers.Default) {
        val activeEraIndex = stakingRepository.getActiveEraIndex()

        val exposures = stakingRepository.getElectedValidatorsExposure(activeEraIndex)
        val validatorsPrefs = stakingRepository.getElectedValidatorsPrefs((activeEraIndex))

        create(exposures, validatorsPrefs)
    }
}
