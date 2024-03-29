package jp.co.soramitsu.staking.impl.domain.validations.rebond

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor

class EnoughToRebondValidation(
    private val stakingScenarioInteractor: StakingScenarioInteractor
) : RebondValidation {

    override suspend fun validate(value: RebondValidationPayload): ValidationStatus<RebondValidationFailure> {
        val isValid = stakingScenarioInteractor.checkEnoughToRebondValidation(value)
        return validOrError(isValid) {
            RebondValidationFailure.NOT_ENOUGH_UNBONDINGS
        }
    }
}
