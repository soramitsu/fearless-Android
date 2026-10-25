package jp.co.soramitsu.wallet.impl.presentation.cross_chain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
class CrossChainTransferDraft(
    val amount: BigDecimal,
    val originChainId: String,
    val destinationChainId: String,
    val originFee: BigDecimal,
    val destinationFee: BigDecimal,
    val chainAssetId: String,
    val recipientAddress: String,
    val tip: BigDecimal?,
    val transferableTokenSymbol: String,
    /** The wallet whose balances were validated at setup; only this wallet may sign the transfer. */
    val metaAccountId: Long,
    /** The destination deducts its fee from the amount instead of the zero [destinationFee] added on top. */
    val destinationFeeIncluded: Boolean = false,
    val providerId: String? = null,
    val routeId: String? = null,
    val runtimeFingerprint: String? = null,
    val executionFingerprint: String? = null,
    val originFeeInPlanks: String? = null,
    val destinationFeeInPlanks: String? = null,
    val effectiveMinimumInPlanks: String? = null
) : Parcelable {
    init {
        require(metaAccountId > 0) { "Cross-chain confirmation requires the validated wallet" }
        val bridgeFields = listOf(
            providerId,
            routeId,
            runtimeFingerprint,
            executionFingerprint,
            originFeeInPlanks,
            destinationFeeInPlanks,
            effectiveMinimumInPlanks
        )
        require(bridgeFields.all { it == null } || bridgeFields.all { !it.isNullOrBlank() }) {
            "Cross-chain bridge confirmation context must be complete"
        }
    }

    val isReviewedBridge: Boolean get() = providerId != null
}
