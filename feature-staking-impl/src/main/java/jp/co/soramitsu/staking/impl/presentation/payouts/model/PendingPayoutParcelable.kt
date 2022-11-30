package jp.co.soramitsu.staking.impl.presentation.payouts.model

import android.os.Parcelable
import java.math.BigInteger
import kotlinx.parcelize.Parcelize

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
