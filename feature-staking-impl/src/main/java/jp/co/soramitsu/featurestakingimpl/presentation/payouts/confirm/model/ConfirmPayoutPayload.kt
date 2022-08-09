package jp.co.soramitsu.featurestakingimpl.presentation.payouts.confirm.model

import android.os.Parcelable
import jp.co.soramitsu.featurestakingimpl.presentation.payouts.model.PendingPayoutParcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigInteger

@Parcelize
class ConfirmPayoutPayload(
    val payouts: List<PendingPayoutParcelable>,
    val totalRewardInPlanks: BigInteger
) : Parcelable
