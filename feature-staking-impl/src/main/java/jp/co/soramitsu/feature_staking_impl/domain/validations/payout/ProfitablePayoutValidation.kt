package jp.co.soramitsu.feature_staking_impl.domain.validations.payout

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus

class ProfitablePayoutValidation : Validation<MakePayoutPayload, PayoutValidationFailure> {

    override suspend fun validate(value: MakePayoutPayload): ValidationStatus<PayoutValidationFailure> {
        return if (value.fee < value.totalReward) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.WARNING, reason = PayoutValidationFailure.UnprofitablePayout)
        }
    }
}
