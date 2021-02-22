package jp.co.soramitsu.feature_staking_impl.domain.rewards

import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RewardCalculatorFactory(
    private val stakingRepository: StakingRepository
) {

    suspend fun create(): RewardCalculator = withContext(Dispatchers.Default) {
        val activeEraIndex = stakingRepository.getActiveEraIndex()

        val exposures = stakingRepository.getElectedValidatorsExposure(activeEraIndex)
        val validatorsPrefs = stakingRepository.getElectedValidatorsPrefs((activeEraIndex))

        val totalIssuance = stakingRepository.getTotalIssuance()

        val validators = exposures.keys.map { accountIdHex ->
            val exposure = exposures[accountIdHex] ?: validatorNotFound(accountIdHex)
            val commission = validatorsPrefs[accountIdHex] ?: validatorNotFound(accountIdHex)

            RewardCalculator.Validator(
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

    private fun validatorNotFound(validatorId: String): Nothing = error("Validator with account id $validatorId was not found")
}