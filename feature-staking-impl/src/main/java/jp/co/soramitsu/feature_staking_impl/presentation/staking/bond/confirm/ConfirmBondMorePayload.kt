package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm

import android.os.Parcelable
import jp.co.soramitsu.common.navigation.PendingNavigationAction
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmBondMorePayload(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val stashAddress: String,
    val overrideFinishAction: PendingNavigationAction<StakingRouter>?
) : Parcelable
