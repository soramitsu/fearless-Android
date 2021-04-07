package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigInteger

class PendingPayoutsStatistics(
    val payouts: List<PendingPayout>,
    val totalAmount: BigInteger,
)

data class PendingPayout(
    val validatorInfo: ValidatorInfo,
    val era: BigInteger,
    val amount: BigInteger,
    val daysLeft: Int,
    val closeToExpire: Boolean,
) {
    data class ValidatorInfo(val address: String, val identityName: String?)
}
