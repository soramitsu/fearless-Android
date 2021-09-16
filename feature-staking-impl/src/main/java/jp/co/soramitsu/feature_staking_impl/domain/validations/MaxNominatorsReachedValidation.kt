package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository

class MaxNominatorsReachedValidation<P, E>(
    private val stakingRepository: StakingRepository,
    private val isAlreadyNominating: (P) -> Boolean,
    private val errorProducer: () -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val nominatorCount = stakingRepository.nominatorsCount() ?: return ValidationStatus.Valid()
        val maxNominatorsAllowed = stakingRepository.maxNominators() ?: return ValidationStatus.Valid()

        if (isAlreadyNominating(value)) {
            return ValidationStatus.Valid()
        }

        return validOrError(nominatorCount < maxNominatorsAllowed) {
            errorProducer()
        }
    }
}
