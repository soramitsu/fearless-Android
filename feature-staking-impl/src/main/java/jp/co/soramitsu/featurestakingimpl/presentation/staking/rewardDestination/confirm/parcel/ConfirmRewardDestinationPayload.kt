package jp.co.soramitsu.featurestakingimpl.presentation.staking.rewardDestination.confirm.parcel

import android.os.Parcelable
import java.math.BigDecimal
import kotlinx.android.parcel.Parcelize

@Parcelize
class ConfirmRewardDestinationPayload(
    val fee: BigDecimal,
    val rewardDestination: RewardDestinationParcelModel
) : Parcelable
