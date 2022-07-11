package jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem

import android.os.Parcelable
import jp.co.soramitsu.common.navigation.PendingNavigationAction
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import kotlinx.android.parcel.Parcelize

@Parcelize
class RedeemPayload(
    val collatorAddress: String?,
    val overrideFinishAction: PendingNavigationAction<StakingRouter>?
) : Parcelable
