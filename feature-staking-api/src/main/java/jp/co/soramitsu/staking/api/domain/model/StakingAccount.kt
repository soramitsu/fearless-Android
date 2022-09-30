package jp.co.soramitsu.staking.api.domain.model

class StakingAccount(
    val address: String,
    val name: String?,
    val isEthereumBased: Boolean
)
