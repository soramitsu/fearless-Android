package jp.co.soramitsu.crowdloan.impl.domain.contribute.validations

import jp.co.soramitsu.core.models.Asset
import java.math.BigDecimal

sealed class ContributeValidationFailure {

    class LessThanMinContribution(
        val minContribution: BigDecimal,
        val chainAsset: Asset
    ) : ContributeValidationFailure()

    sealed class CapExceeded : ContributeValidationFailure() {

        class FromAmount(
            val maxAllowedContribution: BigDecimal,
            val chainAsset: Asset
        ) : CapExceeded()

        object FromRaised : CapExceeded()
    }

    object CrowdloanEnded : ContributeValidationFailure()

    object CannotPayFees : ContributeValidationFailure()

    object ExistentialDepositCrossed : ContributeValidationFailure()

    object PrivateCrowdloanNotSupported : ContributeValidationFailure()
}
