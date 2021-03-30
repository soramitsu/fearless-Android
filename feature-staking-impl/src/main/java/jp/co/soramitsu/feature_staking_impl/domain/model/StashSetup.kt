package jp.co.soramitsu.feature_staking_impl.domain.model

import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination

class StashSetup(
    val rewardDestination: RewardDestination,
    val controllerAddress: String,
    val alreadyHasStash: Boolean,
)
