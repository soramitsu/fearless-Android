package jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem

import android.os.Parcelable
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import kotlinx.android.parcel.Parcelize

typealias PendingNavigationAction = (StakingRouter) -> Unit

@Parcelize
class RedeemPayload(val overrideFinishAction: PendingNavigationAction?) : Parcelable
