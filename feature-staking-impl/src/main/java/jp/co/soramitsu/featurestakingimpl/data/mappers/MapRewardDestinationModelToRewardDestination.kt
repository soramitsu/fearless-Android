package jp.co.soramitsu.featurestakingimpl.data.mappers

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.featurestakingapi.domain.model.RewardDestination
import jp.co.soramitsu.featurestakingimpl.presentation.common.rewardDestination.RewardDestinationModel

fun mapRewardDestinationModelToRewardDestination(
    rewardDestinationModel: RewardDestinationModel
): RewardDestination {
    return when (rewardDestinationModel) {
        is RewardDestinationModel.Restake -> RewardDestination.Restake
        is RewardDestinationModel.Payout -> RewardDestination.Payout(rewardDestinationModel.destination.address.toAccountId())
    }
}
