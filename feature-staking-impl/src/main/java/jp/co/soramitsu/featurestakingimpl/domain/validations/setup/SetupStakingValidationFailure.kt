package jp.co.soramitsu.featurestakingimpl.domain.validations.setup

import java.math.BigDecimal

sealed class SetupStakingValidationFailure {
    object CannotPayFee : SetupStakingValidationFailure()

    class TooSmallAmount(val threshold: BigDecimal) : SetupStakingValidationFailure()

    object MaxNominatorsReached : SetupStakingValidationFailure()
}
