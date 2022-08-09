package jp.co.soramitsu.staking.impl.presentation.staking.main.model

data class RewardEstimation(
    val amount: String,
    val fiatAmount: String?,
    val gain: String
)
