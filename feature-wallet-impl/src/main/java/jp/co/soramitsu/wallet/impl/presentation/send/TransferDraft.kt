package jp.co.soramitsu.wallet.impl.presentation.send

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize

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
