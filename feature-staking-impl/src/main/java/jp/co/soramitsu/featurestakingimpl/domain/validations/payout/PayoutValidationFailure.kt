package jp.co.soramitsu.featurestakingimpl.domain.validations.payout

sealed class PayoutValidationFailure {
    object CannotPayFee : PayoutValidationFailure()

    object UnprofitablePayout : PayoutValidationFailure()
}
