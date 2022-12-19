package jp.co.soramitsu.staking.impl.domain.validations

import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor

class MaxNominatorsReachedValidation<P, E>(
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val isAlreadyNominating: (P) -> Boolean,
    private val sharedState: StakingSharedState,
    private val errorProducer: () -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val chainId = sharedState.chainId()
        if (isAlreadyNominating(value)) {
            return ValidationStatus.Valid()
        }
        val maxNumberOfStakesReached = stakingScenarioInteractor.maxNumberOfStakesIsReached(chainId)
        return validOrError(maxNumberOfStakesReached.not()) {
            errorProducer()
        }
    }
}
