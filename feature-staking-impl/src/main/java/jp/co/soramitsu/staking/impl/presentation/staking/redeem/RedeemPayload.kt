package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import android.os.Parcelable
import jp.co.soramitsu.common.navigation.PendingNavigationAction
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import kotlinx.parcelize.Parcelize

@Parcelize
class RedeemPayload(
    val collatorAddress: String?,
    val overrideFinishAction: PendingNavigationAction<StakingRouter>?
) : Parcelable
