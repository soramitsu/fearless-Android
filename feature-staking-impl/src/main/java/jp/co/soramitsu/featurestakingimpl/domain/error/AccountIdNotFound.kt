package jp.co.soramitsu.featurestakingimpl.domain.error

fun accountIdNotFound(accountIdHex: String): Nothing = error("Target with account id $accountIdHex was not found")
