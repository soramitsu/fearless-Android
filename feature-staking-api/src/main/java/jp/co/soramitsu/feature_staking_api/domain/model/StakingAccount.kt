package jp.co.soramitsu.feature_staking_api.domain.model

class StakingAccount(
    val address: String,
    val name: String?,
    val isEthereumBased: Boolean
)
