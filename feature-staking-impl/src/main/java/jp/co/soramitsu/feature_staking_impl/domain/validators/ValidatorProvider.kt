package jp.co.soramitsu.feature_staking_impl.domain.validators

import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory

sealed class ValidatorSource {

    object Elected : ValidatorSource()

    class Custom(val validatorIds: List<String>) : ValidatorSource()
}

class ValidatorProvider(
    private val stakingRepository: StakingRepository,
    private val identityRepository: IdentityRepository,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
) {

    suspend fun getValidators(
        source: ValidatorSource,
    ): List<Validator> {
        val activeEraIndex = stakingRepository.getActiveEraIndex()

        val electedValidatorExposures = stakingRepository.getElectedValidatorsExposure(activeEraIndex)

        val requestedValidatorIds = when (source) {
            ValidatorSource.Elected -> electedValidatorExposures.keys.toList()
            is ValidatorSource.Custom -> source.validatorIds
        }

        val validatorIdsToQueryPrefs = electedValidatorExposures.keys + requestedValidatorIds

        val validatorPrefs = stakingRepository.getValidatorPrefs(validatorIdsToQueryPrefs.toList())

        val identities = identityRepository.getIdentitiesFromIds(requestedValidatorIds)
        val slashes = stakingRepository.getSlashes(requestedValidatorIds)

        val rewardCalculator = rewardCalculatorFactory.create(electedValidatorExposures, validatorPrefs)

        return requestedValidatorIds.map { accountIdHex ->
            val prefs = validatorPrefs[accountIdHex]

            val electedInfo = electedValidatorExposures[accountIdHex]?.let {
                Validator.ElectedInfo(
                    totalStake = it.total,
                    ownStake = it.own,
                    nominatorStakes = it.others,
                    apy = rewardCalculator.getApyFor(accountIdHex)
                )
            }

            Validator(
                slashed = slashes.getOrDefault(accountIdHex, false),
                accountIdHex = accountIdHex,
                electedInfo = electedInfo,
                prefs = prefs,
                identity = identities[accountIdHex],
            )
        }
    }
}
