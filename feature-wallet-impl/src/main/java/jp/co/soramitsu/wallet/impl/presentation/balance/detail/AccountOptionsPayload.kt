package jp.co.soramitsu.wallet.impl.presentation.balance.detail

data class AccountOptionsPayload(
    val address: String,
    val supportClaim: Boolean,
    val isEthereum: Boolean
)