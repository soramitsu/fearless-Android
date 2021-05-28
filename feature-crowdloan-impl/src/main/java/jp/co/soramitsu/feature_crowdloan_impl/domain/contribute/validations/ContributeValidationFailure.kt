package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations

import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

sealed class ContributeValidationFailure {

    class LessThanMinContribution(
        val minContribution: BigDecimal,
        val token: Token
    ) : ContributeValidationFailure()

    sealed class CapExceeded : ContributeValidationFailure() {

        class FromAmount(
            val maxAllowedContribution: BigDecimal,
            val token: Token
        ) : CapExceeded()

        object FromRaised : CapExceeded()
    }

    object CrowdloanEnded : ContributeValidationFailure()

    object CannotPayFees : ContributeValidationFailure()

    object ExistentialDepositCrossed : ContributeValidationFailure()
}
