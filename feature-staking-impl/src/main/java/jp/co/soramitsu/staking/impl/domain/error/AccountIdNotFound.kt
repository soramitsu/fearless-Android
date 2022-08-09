package jp.co.soramitsu.staking.impl.domain.error

fun accountIdNotFound(accountIdHex: String): Nothing = error("Target with account id $accountIdHex was not found")
