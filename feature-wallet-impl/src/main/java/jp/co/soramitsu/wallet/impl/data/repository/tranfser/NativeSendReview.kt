package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import java.math.BigDecimal

/** Display values derived from the same immutable message/plan that confirmation will submit. */
data class NativeSendReview(
    val amount: BigDecimal,
    val networkFee: BigDecimal,
    val accountCreation: BigDecimal = BigDecimal.ZERO,
    val satPerVbyte: Double? = null,
    val lastValidBlockHeight: Long? = null
) {
    val totalFee: BigDecimal get() = networkFee + accountCreation
}
