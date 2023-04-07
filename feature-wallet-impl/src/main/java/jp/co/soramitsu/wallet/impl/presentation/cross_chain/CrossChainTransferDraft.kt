package jp.co.soramitsu.wallet.impl.presentation.cross_chain

import android.os.Parcelable
import jp.co.soramitsu.common.utils.orZero
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
class CrossChainTransferDraft(
    val amount: BigDecimal,
    val originalChainId: String,
    val destinationChainId: String,
    val originalFee: BigDecimal,
    val destinationFee: BigDecimal,
    val chainAssetId: String,
    val recipientAddress: String,
    val tip: BigDecimal?
) : Parcelable {
    @IgnoredOnParcel
    val totalTransaction = amount + originalFee + destinationFee + tip.orZero()

    fun totalAfterTransfer(currentTotal: BigDecimal) = currentTotal - totalTransaction
}
