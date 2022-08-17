package jp.co.soramitsu.staking.api.domain.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId

sealed class RewardDestination {

    object Restake : RewardDestination()

    class Payout(val targetAccountId: AccountId) : RewardDestination()
}
