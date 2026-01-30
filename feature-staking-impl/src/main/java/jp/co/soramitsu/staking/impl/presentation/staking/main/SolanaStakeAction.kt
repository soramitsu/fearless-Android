package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.annotation.StringRes
import jp.co.soramitsu.feature_staking_impl.R

enum class SolanaStakeAction(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val docsUrl: String
) {
    Delegate(
        titleRes = R.string.staking_solana_action_delegate_title,
        descriptionRes = R.string.staking_solana_action_delegate_description,
        docsUrl = "https://docs.solana.com/staking/stake-program"
    ),
    Deactivate(
        titleRes = R.string.staking_solana_action_deactivate_title,
        descriptionRes = R.string.staking_solana_action_deactivate_description,
        docsUrl = "https://docs.solana.com/cli/examples/deactivate-stake"
    ),
    Withdraw(
        titleRes = R.string.staking_solana_action_withdraw_title,
        descriptionRes = R.string.staking_solana_action_withdraw_description,
        docsUrl = "https://docs.solana.com/cli/examples/withdraw-stake"
    );

    fun commandTemplate(stakeAccount: String): String {
        return when (this) {
            Delegate -> "solana delegate-stake $stakeAccount <VOTE_ACCOUNT>"
            Deactivate -> "solana deactivate-stake $stakeAccount <VOTE_ACCOUNT>"
            Withdraw -> "solana withdraw-stake $stakeAccount <RECIPIENT_ADDRESS> <AMOUNT>"
        }
    }
}
