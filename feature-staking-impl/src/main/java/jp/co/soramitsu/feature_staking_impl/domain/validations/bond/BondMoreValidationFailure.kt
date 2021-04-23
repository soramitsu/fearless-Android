package jp.co.soramitsu.feature_staking_impl.domain.validations.bond

enum class BondMoreValidationFailure {
    NOT_ENOUGH_TO_PAY_FEES, ZERO_BOND, ELECTION_IS_OPEN
}
