package jp.co.soramitsu.staking.impl.presentation.staking.main

data class SolanaStakingViewState(
    val apy: String,
    val totalStake: String,
    val validatorsTitle: String,
    val validators: List<SolanaValidatorViewState>
)

data class SolanaValidatorViewState(
    val rankLabel: String,
    val identity: String,
    val stake: String,
    val commission: String,
    val voteAccount: String
)
