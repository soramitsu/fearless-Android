package jp.co.soramitsu.common.model

import jp.co.soramitsu.core.models.Ecosystem

enum class WalletEcosystem {
    Substrate, Ethereum, Ton
}

fun Ecosystem.toAccountType() = when (this) {
    Ecosystem.Substrate -> WalletEcosystem.Substrate
    Ecosystem.Ton -> WalletEcosystem.Ton
    Ecosystem.EthereumBased,
    Ecosystem.Ethereum -> WalletEcosystem.Ethereum
}