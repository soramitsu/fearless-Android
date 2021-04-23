package jp.co.soramitsu.feature_staking_impl.domain.validations.bond

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import java.math.BigDecimal

class NotZeroBondValidation : Validation<BondMoreValidationPayload, BondMoreValidationFailure> {

    override suspend fun validate(value: BondMoreValidationPayload): ValidationStatus<BondMoreValidationFailure> {
        return if (value.amount > BigDecimal.ZERO) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, BondMoreValidationFailure.ZERO_BOND)
        }
    }
}
