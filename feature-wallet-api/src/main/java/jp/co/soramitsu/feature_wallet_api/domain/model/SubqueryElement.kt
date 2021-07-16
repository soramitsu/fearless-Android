package jp.co.soramitsu.feature_wallet_api.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import java.math.BigDecimal

data class SubqueryElement(
    val hash: String,
    val address: String,
    val accountName: String?,
    val operation: Operation,
    val time: Long,
    val tokenType: Token.Type,
    val nextPageCursor: String? = null
) {

    val formattedAmount = createFormattedAmount()

    fun getOperationHeader() = format(operation.header) ?: accountName ?: getDisplayAddress()

    private fun format(extrinsicHeader: String?) = extrinsicHeader?.capitalize()?.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")?.joinToString()

    fun getDisplayAddress() = (operation as? Operation.Transfer)?.receiver ?: address

    fun getElementDescription() = format(operation.subheader)

    fun getOperationIcon(): Int? = when (operation) {
        is Operation.Reward -> R.drawable.ic_staking
        else -> null
    }

    fun getIsIncome() = when(operation) {
        is Operation.Extrinsic -> false
        is Operation.Reward -> operation.isReward
        is Operation.Transfer -> address == operation.receiver
    }

    val statusAppearance = when (operation.status) {
        Status.COMPLETED -> StatusAppearance.COMPLETED
        Status.FAILED -> StatusAppearance.FAILED
        Status.PENDING -> StatusAppearance.PENDING
    }

    val amountColorRes = when {
        operation.status == SubqueryElement.Status.FAILED -> R.color.gray2
        getIsIncome() -> R.color.green
        else -> R.color.white
    }

    private fun createFormattedAmount(): String {
        val withoutSign = operation.displayAmount.formatTokenAmount(tokenType)
        val sign = if (getIsIncome()) '+' else '-'

        return sign + withoutSign
    }

    sealed class Operation(
        val header: String?,
        val subheader: String?,
        val displayAmount: BigDecimal, // amount that we show on UI (might be fee)
    val status: Status
    ) {
        class Extrinsic(
            val hash: String,
            val module: String,
            val call: String,
            val fee: BigDecimal,
            val success: Boolean
        ) : Operation(call, module, fee, Status.fromSuccess(success))

        class Reward(
            val amount: BigDecimal,
            val isReward: Boolean,
            val era: Int,
            val validator: String
        ) : Operation(if (isReward) "Reward" else "Slash", "Staking", amount, Status.FAILED)

        class Transfer(
            val amount: BigDecimal,
            val receiver: String,
            val sender: String,
            val fee: BigDecimal
        ) : Operation(null, "Transfer", amount, Status.COMPLETED)

        //Real amount without fee
        fun getOperationAmount() = when (this) {
            is Reward -> amount
            is Transfer -> amount
            is Extrinsic -> null
        }

        fun getOperationFee() = when (this) {
            is Reward -> null
            is Transfer -> fee
            is Extrinsic -> fee
        }
    }

    enum class Status {
        PENDING, COMPLETED, FAILED;

        companion object {
            fun fromSuccess(success: Boolean): Status {
                return if (success) COMPLETED else FAILED
            }
        }
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
