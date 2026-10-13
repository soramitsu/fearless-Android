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
    val providerId: String? = null,
    val routeId: String? = null,
    val runtimeFingerprint: String? = null,
    val executionFingerprint: String? = null,
    val originFeeInPlanks: String? = null,
    val destinationFeeInPlanks: String? = null,
    val effectiveMinimumInPlanks: String? = null
) : Parcelable {
    init {
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
