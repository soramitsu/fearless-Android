package jp.co.soramitsu.feature_staking_impl.domain.validations.rebond

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError

class EnoughToRebondValidation : RebondValidation {

    override suspend fun validate(value: RebondValidationPayload): ValidationStatus<RebondValidationFailure> {
        return validOrError(value.rebondAmount <= value.controllerAsset.unbonding) {
            RebondValidationFailure.NOT_ENOUGH_UNBONDINGS
        }
    }
}
