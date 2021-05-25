package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations

import java.math.BigDecimal

sealed class ContributeValidationFailure {

    class LessThanMinContribution(val minContribution: BigDecimal) : ContributeValidationFailure()

    sealed class CapExceeded : ContributeValidationFailure() {

        class FromAmount(val maxAllowedContribution: BigDecimal) : CapExceeded()

        object FromRaised : CapExceeded()
    }

    object CrowdloanEnded : ContributeValidationFailure()

    object CannotPayFees : ContributeValidationFailure()

    object ExistentialDepositCrossed : ContributeValidationFailure()
}
