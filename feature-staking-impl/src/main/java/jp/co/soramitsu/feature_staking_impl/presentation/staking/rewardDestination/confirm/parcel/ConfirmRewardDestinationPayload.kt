package jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.parcel

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmRewardDestinationPayload(
    val fee: BigDecimal,
    val rewardDestination: RewardDestinationParcelModel,
) : Parcelable
