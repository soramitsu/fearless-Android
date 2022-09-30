package jp.co.soramitsu.staking.impl.domain.validations.payout

sealed class PayoutValidationFailure {
    object CannotPayFee : PayoutValidationFailure()

    object UnprofitablePayout : PayoutValidationFailure()
}
