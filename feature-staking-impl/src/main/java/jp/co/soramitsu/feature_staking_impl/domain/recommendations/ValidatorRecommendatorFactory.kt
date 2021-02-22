package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.error.accountIdNotFound
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RECOMMENDATION_LIMIT = 16

class ValidatorRecommendatorFactory(
    private val stakingRepository: StakingRepository,
    private val rewardCalculatorFactory: RewardCalculatorFactory
) {

    suspend fun create(): ValidatorRecommendator = withContext(Dispatchers.IO) {
        val activeEraIndex = stakingRepository.getActiveEraIndex()

        val exposures = stakingRepository.getElectedValidatorsExposure(activeEraIndex)
        val validatorsPrefs = stakingRepository.getElectedValidatorsPrefs((activeEraIndex))

        val validatorIds = exposures.keys.toList()

        val identities = stakingRepository.getIdentities(validatorIds)
        val slashes = stakingRepository.getSlashes(validatorIds)

        val rewardCalculator = rewardCalculatorFactory.create(exposures, validatorsPrefs)

        val validators = validatorIds.map { accountIdHex ->
            val exposure = exposures[accountIdHex] ?: accountIdNotFound(accountIdHex)
            val prefs = validatorsPrefs[accountIdHex] ?: accountIdNotFound(accountIdHex)

            Validator(
                slashed = slashes.getOrDefault(accountIdHex, false),
                accountIdHex = accountIdHex,
                totalStake = exposure.total,
                ownStake = exposure.total,
                nominatorStakes = exposure.others,
                commission = prefs,
                identity = identities[accountIdHex],
                apy = rewardCalculator.getApyFor(accountIdHex)
            )
        }

        ValidatorRecommendator(validators, RECOMMENDATION_LIMIT)
    }
}