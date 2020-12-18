package jp.co.soramitsu.feature_wallet_impl.presentation.model

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.util.formatAsToken
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
data class TransactionModel(
    val hash: String,
    val type: Token.Type,
    val senderAddress: String,
    val recipientAddress: String,
    val amount: BigDecimal,
    val date: Long,
    val status: Transaction.Status,
    val fee: BigDecimal?,
    val isIncome: Boolean,
    val total: BigDecimal?
) : Parcelable {
    @IgnoredOnParcel
    val displayAddress = if (isIncome) senderAddress else recipientAddress

    @IgnoredOnParcel
    val statusAppearance = when (status) {
        Transaction.Status.COMPLETED -> StatusAppearance.COMPLETED
        Transaction.Status.FAILED -> StatusAppearance.FAILED
        Transaction.Status.PENDING -> StatusAppearance.PENDING
    }

    @IgnoredOnParcel
    val formattedAmount = createFormattedAmount()

    @IgnoredOnParcel
    val amountColorRes = when {
        status == Transaction.Status.FAILED -> R.color.gray2
        isIncome -> R.color.green
        else -> R.color.white
    }

    private fun createFormattedAmount(): String {
        val withoutSign = amount.formatAsToken(type)
        val sign = if (isIncome) '+' else '-'

        return sign + withoutSign
    }

    enum class StatusAppearance(
        @DrawableRes val icon: Int,
        @StringRes val labelRes: Int
    ) {
        COMPLETED(R.drawable.ic_transaction_completed, R.string.transaction_status_completed),
        PENDING(R.drawable.ic_transaction_pending, R.string.transaction_status_pending),
        FAILED(R.drawable.ic_red_cross, R.string.transaction_status_failed),
    }
}