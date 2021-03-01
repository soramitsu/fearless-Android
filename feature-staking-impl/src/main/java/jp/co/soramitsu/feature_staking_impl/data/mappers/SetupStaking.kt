package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_impl.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.presentation.setup.RewardDestinationModel

fun mapRewardDestinationModelToRewardDestination(
    rewardDestinationModel: RewardDestinationModel
): RewardDestination {
    return when (rewardDestinationModel) {
        is RewardDestinationModel.Restake -> RewardDestination.Restake
        is RewardDestinationModel.Payout -> RewardDestination.Payout(rewardDestinationModel.destination.address.toAccountId())
    }
}