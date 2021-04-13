package jp.co.soramitsu.feature_staking_impl.domain.validations.setup

import java.math.BigDecimal

sealed class SetupStakingValidtionFailure {
    object CannotPayFee : SetupStakingValidtionFailure()

    class TooSmallAmount(val threshold: BigDecimal) : SetupStakingValidtionFailure()
}
