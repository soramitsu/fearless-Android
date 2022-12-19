package jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel

import android.os.Parcelable
import java.math.BigDecimal
import kotlinx.parcelize.Parcelize

@Parcelize
class ConfirmRewardDestinationPayload(
    val fee: BigDecimal,
    val rewardDestination: RewardDestinationParcelModel
) : Parcelable
