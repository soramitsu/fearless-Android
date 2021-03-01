package jp.co.soramitsu.feature_staking_impl.domain.setup.validations

import java.math.BigDecimal

sealed class StakingValidationFailure {
    object CannotPayFee : StakingValidationFailure()

    class TooSmallAmount(val threshold: BigDecimal) : StakingValidationFailure()
}