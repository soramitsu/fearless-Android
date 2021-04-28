package jp.co.soramitsu.feature_staking_impl.domain.validations.unbond

import java.math.BigDecimal

sealed class UnbondValidationFailure {

    object CannotPayFees : UnbondValidationFailure()

    object NotEnoughBonded : UnbondValidationFailure()

    object ZeroUnbond : UnbondValidationFailure()

    class BondedWillCrossExistential(val willBeUnbonded: BigDecimal) : UnbondValidationFailure()

    class UnbondLimitReached(val limit: Int) : UnbondValidationFailure()

    object ElectionIsOpen : UnbondValidationFailure()
}
