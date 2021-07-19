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
    val type: Operation.Type,
    val time: Long,
    val tokenType: Token.Type
) {
    val formattedAmount = createFormattedAmount()

    fun getDisplayAddress() = (type as? Operation.Type.Transfer)?.receiver ?: address

    fun getOperationHeader() = format(type.header) ?: accountName ?: getDisplayAddress()

    fun getElementDescription() = format(type.subheader)

    private fun format(extrinsicHeader: String?) = (extrinsicHeader)?.split(regex = "(?<=[a-z])(?=[A-Z])".toRegex())?.joinToString(" ")?.capitalize()

    fun getOperationIcon(): Int? = when (type) {
        is Operation.Type.Reward -> R.drawable.ic_staking
        else -> null
    }

    fun getIsIncome() = when (type) {
        is Operation.Type.Extrinsic -> false
        is Operation.Type.Reward -> type.isReward
        is Operation.Type.Transfer -> address == type.receiver
    }

    val amountColorRes = when {
        type.status == Operation.Status.FAILED -> R.color.gray2
        getIsIncome() -> R.color.green
        else -> R.color.white
    }

    val statusAppearance = when (type.status) {
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
        val withoutSign = type.displayAmount.formatTokenAmount(tokenType)
        val sign = if (getIsIncome()) '+' else '-'

        return sign + withoutSign
    }
}
