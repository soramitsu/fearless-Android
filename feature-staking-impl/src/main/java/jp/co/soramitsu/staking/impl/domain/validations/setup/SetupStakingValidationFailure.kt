package jp.co.soramitsu.staking.impl.domain.validations.setup

import java.math.BigDecimal

sealed class SetupStakingValidationFailure {
    object BalanceUnavailable : SetupStakingValidationFailure()

    object CannotPayFee : SetupStakingValidationFailure()

    class TooSmallAmount(val threshold: BigDecimal) : SetupStakingValidationFailure()

    object MaxNominatorsReached : SetupStakingValidationFailure()
}
