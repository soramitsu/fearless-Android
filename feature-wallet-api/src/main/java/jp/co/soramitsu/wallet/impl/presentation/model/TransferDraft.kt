package jp.co.soramitsu.wallet.impl.presentation.model

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.common.utils.orZero
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class TransferDraft(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val assetPayload: AssetPayload,
    val recipientAddress: String,
    val tip: BigDecimal?
) : Parcelable {
    @IgnoredOnParcel
    val totalTransaction = amount + fee + tip.orZero()

    fun totalAfterTransfer(currentTotal: BigDecimal) = currentTotal - totalTransaction
}
