package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

fun noValidatorPrefs(accountIdHex: String): Nothing = throw IllegalStateException("Sorting/Filtering validator $accountIdHex with no prefs")

fun notElected(accountIdHex: String): Nothing = throw IllegalStateException("Sorting/Filtering not elected validator $accountIdHex")
