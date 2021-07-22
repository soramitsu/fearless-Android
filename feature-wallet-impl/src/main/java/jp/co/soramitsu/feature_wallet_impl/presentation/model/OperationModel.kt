package jp.co.soramitsu.feature_wallet_impl.presentation.model

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.RawValue
import java.math.BigDecimal

@Parcelize
class OperationModel(
    val hash: String,
    val address: String,
    val accountName: String?,
    val transactionType: TransactionModelType,
    val time: Long,
    val tokenType: Token.Type
): Parcelable {
    @IgnoredOnParcel
    val formattedAmount = createFormattedAmount()

    fun getDisplayAddress() = (transactionType as? TransactionModelType.Transfer)?.receiver ?: address

    fun getOperationHeader() = format(transactionType.header) ?: accountName ?: getDisplayAddress()

    fun getElementDescription() = format(transactionType.subheader)

    private fun format(extrinsicHeader: String?) = extrinsicHeader?.split(regex = "(?<=[a-z])(?=[A-Z])".toRegex())?.joinToString(" ")?.capitalize()

    fun getOperationIcon(): Int? = when (transactionType) {
        is TransactionModelType.Reward -> R.drawable.ic_staking
        else -> null
    }

    fun getIsIncome() = when (transactionType) {
        is TransactionModelType.Extrinsic -> false
        is TransactionModelType.Reward -> transactionType.isReward
        is TransactionModelType.Transfer -> address == transactionType.receiver
    }

    @IgnoredOnParcel
    val amountColorRes = when {
        transactionType.status == Operation.Status.FAILED -> R.color.gray2
        getIsIncome() -> R.color.green
        else -> R.color.white
    }

    @IgnoredOnParcel
    val statusAppearance = when (transactionType.status) {
        Operation.Status.COMPLETED -> StatusAppearance.COMPLETED
        Operation.Status.FAILED -> StatusAppearance.FAILED
        Operation.Status.PENDING -> StatusAppearance.PENDING
    }

    enum class StatusAppearance(
        @DrawableRes val icon: Int,
        @StringRes val labelRes: Int
    ) {
        COMPLETED(R.drawable.ic_transaction_completed, R.string.transaction_status_completed),
        PENDING(R.drawable.ic_transaction_pending, R.string.transaction_status_pending),
        FAILED(R.drawable.ic_red_cross, R.string.transaction_status_failed),
    }

    private fun createFormattedAmount(): String {
        val withoutSign = transactionType.operationAmount.formatTokenAmount(tokenType)
        val sign = if (getIsIncome()) '+' else '-'

        return sign + withoutSign
    }

    sealed class TransactionModelType(
        val header: String?,
        val subheader: String?,
        val operationAmount: BigDecimal,
        val operationFee: BigDecimal,
        val status: Operation.Status
    ) : Parcelable{
        @Parcelize
        class Extrinsic(
            val hash: String,
            val module: String,
            val call: String,
            val fee: BigDecimal,
            val success: Boolean
        ) : TransactionModelType(call, module, operationAmount = BigDecimal.ZERO, operationFee = fee, Operation.Status.fromSuccess(success))

        @Parcelize
        class Reward(
            val amount: BigDecimal,
            val isReward: Boolean,
            val era: Int,
            val validator: String
        ) : TransactionModelType(if (isReward) "Reward" else "Slash", "Staking", operationAmount = amount, operationFee = BigDecimal.ZERO, Operation.Status.FAILED)

        @Parcelize
        class Transfer(
            val amount: BigDecimal,
            val receiver: String,
            val sender: String,
            val fee: BigDecimal
        ) : TransactionModelType(null, "Transfer", operationAmount = amount, operationFee = fee, Operation.Status.COMPLETED)
    }

}
