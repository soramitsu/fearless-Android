package jp.co.soramitsu.featurestakingimpl.presentation.staking.bond.select

import android.os.Parcelable
import jp.co.soramitsu.common.navigation.PendingNavigationAction
import jp.co.soramitsu.featurestakingimpl.presentation.StakingRouter
import kotlinx.android.parcel.Parcelize

@Parcelize
class SelectBondMorePayload(
    val overrideFinishAction: PendingNavigationAction<StakingRouter>?,
    val collatorAddress: String?,
    val oneScreenConfirmation: Boolean = false
) : Parcelable
