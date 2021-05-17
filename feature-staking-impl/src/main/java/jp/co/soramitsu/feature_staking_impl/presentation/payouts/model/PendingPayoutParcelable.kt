package jp.co.soramitsu.feature_staking_impl.presentation.payouts.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigInteger

@Parcelize
class PendingPayoutParcelable(
    val validatorInfo: ValidatorInfoParcelable,
    val era: BigInteger,
    val amountInPlanks: BigInteger,
    val createdAt: Long,
    val daysLeft: Int,
    val closeToExpire: Boolean,
) : Parcelable {
    @Parcelize
    class ValidatorInfoParcelable(
        val address: String,
        val identityName: String?,
    ) : Parcelable
}
