package jp.co.soramitsu.feature_staking_impl.domain.validations.unbond

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError

class EnoughToUnbondValidation : UnbondValidation {

    override suspend fun validate(value: UnbondValidationPayload): ValidationStatus<UnbondValidationFailure> {
        return validOrError(value.amount <= value.bonded) {
            UnbondValidationFailure.NotEnoughBonded
        }
    }
}
