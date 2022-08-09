package jp.co.soramitsu.featurestakingimpl.presentation.payouts.model

import android.os.Parcelable
import java.math.BigInteger
import kotlinx.android.parcel.Parcelize

@Parcelize
class PendingPayoutParcelable(
    val validatorInfo: ValidatorInfoParcelable,
    val era: BigInteger,
    val amountInPlanks: BigInteger,
    val createdAt: Long,
    val timeLeft: Long,
    val closeToExpire: Boolean
) : Parcelable {
    @Parcelize
    class ValidatorInfoParcelable(
        val address: String,
        val identityName: String?
    ) : Parcelable
}
