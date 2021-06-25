package jp.co.soramitsu.feature_staking_impl.domain.validations.rebond

enum class RebondValidationFailure {
    NOT_ENOUGH_UNBONDINGS, CANNOT_PAY_FEE, ZERO_AMOUNT
}
