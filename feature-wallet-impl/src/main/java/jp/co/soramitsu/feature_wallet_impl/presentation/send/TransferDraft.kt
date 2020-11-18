package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.os.Parcelable
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class TransferDraft(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val token: Asset.Token,
    val recipientAddress: String
) : Parcelable {
    @IgnoredOnParcel
    val totalTransaction = amount + fee

    fun totalAfterTransfer(currentTotal: BigDecimal) = currentTotal - totalTransaction
}