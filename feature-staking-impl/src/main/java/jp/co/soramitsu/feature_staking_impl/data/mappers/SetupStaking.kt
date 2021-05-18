package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.RewardDestinationModel

fun mapRewardDestinationModelToRewardDestination(
    rewardDestinationModel: RewardDestinationModel,
): RewardDestination {
    return when (rewardDestinationModel) {
        is RewardDestinationModel.Restake -> RewardDestination.Restake
        is RewardDestinationModel.Payout -> RewardDestination.Payout(rewardDestinationModel.destination.address.toAccountId())
    }
}
