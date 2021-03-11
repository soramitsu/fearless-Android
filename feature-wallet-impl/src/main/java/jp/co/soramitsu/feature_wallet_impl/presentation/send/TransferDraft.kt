package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize

@Parcelize
class TransferDraft(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val type: Token.Type,
    val recipientAddress: String
) : Parcelable {
    @IgnoredOnParcel
    val totalTransaction = amount + fee

    fun totalAfterTransfer(currentTotal: BigDecimal) = currentTotal - totalTransaction
}
