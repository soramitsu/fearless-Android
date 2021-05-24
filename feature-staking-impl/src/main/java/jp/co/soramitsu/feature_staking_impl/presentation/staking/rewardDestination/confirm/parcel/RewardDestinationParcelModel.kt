package jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.parcel

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

sealed class RewardDestinationParcelModel : Parcelable {

    @Parcelize
    object Restake : RewardDestinationParcelModel()

    @Parcelize
    class Payout(val targetAccountAddress: String) : RewardDestinationParcelModel()
}
