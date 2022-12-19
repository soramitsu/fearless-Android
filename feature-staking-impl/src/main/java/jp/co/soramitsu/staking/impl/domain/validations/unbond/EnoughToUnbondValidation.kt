package jp.co.soramitsu.staking.impl.domain.validations.unbond

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor

class EnoughToUnbondValidation(
    private val stakingScenarioInteractor: StakingScenarioInteractor
) : UnbondValidation {

    override suspend fun validate(value: UnbondValidationPayload): ValidationStatus<UnbondValidationFailure> {
        val isValid = stakingScenarioInteractor.checkEnoughToUnbondValidation(value)
        return validOrError(isValid) {
            UnbondValidationFailure.NotEnoughBonded
        }
    }
}
