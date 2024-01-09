package jp.co.soramitsu.staking.impl.domain.validations.unbond

import java.math.BigDecimal

sealed class UnbondValidationFailure {

    object CannotPayFees : UnbondValidationFailure()

    object NotEnoughBonded : UnbondValidationFailure()

    object ZeroUnbond : UnbondValidationFailure()

    object ControllerCantPayFees : UnbondValidationFailure()

    class BondedWillCrossExistential(val willBeUnbonded: BigDecimal) : UnbondValidationFailure()

    class UnbondLimitReached(val limit: Int) : UnbondValidationFailure()
}
