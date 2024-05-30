package jp.co.soramitsu.staking.impl.domain.validations.payout

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus

class ProfitablePayoutValidation : Validation<MakePayoutPayload, PayoutValidationFailure> {

    override suspend fun validate(value: MakePayoutPayload): ValidationStatus<PayoutValidationFailure> {
        return if (value is SoraPayoutsPayload) {
            val feeFiat = value.token.fiatAmount(value.fee)
            val totalRewardFiat = value.rewardToken.fiatAmount(value.totalReward)
            if (feeFiat == null || totalRewardFiat == null) {
                return ValidationStatus.Valid()
            }
            if (feeFiat < totalRewardFiat) {
                ValidationStatus.Valid()
            } else {
                ValidationStatus.NotValid(
                    DefaultFailureLevel.WARNING,
                    reason = PayoutValidationFailure.UnprofitablePayout
                )
            }
        } else {
            if (value.fee < value.totalReward) {
                ValidationStatus.Valid()
            } else {
                ValidationStatus.NotValid(
                    DefaultFailureLevel.WARNING,
                    reason = PayoutValidationFailure.UnprofitablePayout
                )
            }
        }
    }
}
