package jp.co.soramitsu.feature_staking_impl.domain.error

fun accountIdNotFound(accountIdHex: String): Nothing = error("Target with account id $accountIdHex was not found")
