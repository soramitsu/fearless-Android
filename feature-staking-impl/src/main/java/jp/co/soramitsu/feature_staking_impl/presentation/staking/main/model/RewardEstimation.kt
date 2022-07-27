package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model

data class RewardEstimation(
    val amount: String,
    val fiatAmount: String?,
    val gain: String,
)
