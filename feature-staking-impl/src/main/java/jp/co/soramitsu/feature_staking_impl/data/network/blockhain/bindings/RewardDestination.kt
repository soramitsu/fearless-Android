package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.feature_staking_impl.domain.model.RewardDestination

fun bindRewardDestination(rewardDestination: RewardDestination) = when (rewardDestination) {
    is RewardDestination.Restake -> DictEnum.Entry("Staked", null)
    is RewardDestination.Payout -> DictEnum.Entry("Account", rewardDestination.targetAccountId)
}