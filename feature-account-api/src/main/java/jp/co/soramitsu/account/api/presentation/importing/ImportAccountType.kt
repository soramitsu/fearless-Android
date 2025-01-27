package jp.co.soramitsu.account.api.presentation.importing

import jp.co.soramitsu.core.models.Ecosystem

enum class ImportAccountType {
    Substrate, Ethereum, Ton
}

fun Ecosystem.toAccountType() = when (this) {
    Ecosystem.Substrate -> ImportAccountType.Substrate
    Ecosystem.Ton -> ImportAccountType.Ton
    Ecosystem.EthereumBased,
    Ecosystem.Ethereum -> ImportAccountType.Ethereum
}