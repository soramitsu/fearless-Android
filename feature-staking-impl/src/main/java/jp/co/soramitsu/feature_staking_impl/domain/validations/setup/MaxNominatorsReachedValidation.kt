package jp.co.soramitsu.feature_staking_impl.domain.validations.setup

import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository

class MaxNominatorsReachedValidation(
    private val stakingRepository: StakingRepository,
) : Validation<SetupStakingPayload, SetupStakingValidationFailure> {

    override suspend fun validate(value: SetupStakingPayload): ValidationStatus<SetupStakingValidationFailure> {
        val nominatorCount = stakingRepository.nominatorsCount() ?: return ValidationStatus.Valid()
        val maxNominatorsAllowed = stakingRepository.maxNominators() ?: return ValidationStatus.Valid()

        return validOrError(nominatorCount < maxNominatorsAllowed) {
            SetupStakingValidationFailure.MaxNominatorsReached
        }
    }
}
