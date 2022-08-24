package jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model

import android.os.Parcelable
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigInteger

@Parcelize
class ConfirmPayoutPayload(
    val payouts: List<PendingPayoutParcelable>,
    val totalRewardInPlanks: BigInteger
) : Parcelable
