package jp.co.soramitsu.feature_wallet_impl.presentation.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount

class OperationModel(
    val hash: String,
    val address: String,
    val accountName: String?,
    val transactionType: Operation.TransactionType,
    val time: Long,
    val tokenType: Token.Type
) {
    val formattedAmount = createFormattedAmount()

    fun getDisplayAddress() = (transactionType as? Operation.TransactionType.Transfer)?.receiver ?: address

    fun getOperationHeader() = format(transactionType.header) ?: accountName ?: getDisplayAddress()

    fun getElementDescription() = format(transactionType.subheader)

    private fun format(extrinsicHeader: String?) = extrinsicHeader?.split(regex = "(?<=[a-z])(?=[A-Z])".toRegex())?.joinToString(" ")?.capitalize()

    fun getOperationIcon(): Int? = when (transactionType) {
        is Operation.TransactionType.Reward -> R.drawable.ic_staking
        else -> null
    }

    fun getIsIncome() = when (transactionType) {
        is Operation.TransactionType.Extrinsic -> false
        is Operation.TransactionType.Reward -> transactionType.isReward
        is Operation.TransactionType.Transfer -> address == transactionType.receiver
    }

    val amountColorRes = when {
        transactionType.status == Operation.Status.FAILED -> R.color.gray2
        getIsIncome() -> R.color.green
        else -> R.color.white
    }

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
}
