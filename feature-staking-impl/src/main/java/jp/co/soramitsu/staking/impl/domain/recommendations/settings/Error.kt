package jp.co.soramitsu.staking.impl.domain.recommendations.settings

fun noValidatorPrefs(accountIdHex: String): Nothing = throw IllegalStateException("Sorting/Filtering validator $accountIdHex with no prefs")

fun notElected(accountIdHex: String): Nothing = throw IllegalStateException("Sorting/Filtering not elected validator $accountIdHex")
