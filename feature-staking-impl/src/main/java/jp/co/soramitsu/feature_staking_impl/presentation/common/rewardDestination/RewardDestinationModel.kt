package jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination

import jp.co.soramitsu.common.address.AddressModel

sealed class RewardDestinationModel {

    object Restake : RewardDestinationModel()

    class Payout(val destination: AddressModel) : RewardDestinationModel()
}
