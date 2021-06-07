package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select

import android.os.Parcelable
import jp.co.soramitsu.common.navigation.PendingNavigationAction
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import kotlinx.android.parcel.Parcelize

@Parcelize
class SelectBondMorePayload(val overrideFinishAction: PendingNavigationAction<StakingRouter>?) : Parcelable
