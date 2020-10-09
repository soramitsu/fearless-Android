package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.util.format
import java.math.BigDecimal

data class TransactionModel(
    val hash: String,
    val token: Asset.Token,
    val senderAddress: String,
    val recipientAddress: String,
    val amount: BigDecimal,
    val date: Long,
    val status: Transaction.Status,
    val fee: Fee,
    val isIncome: Boolean
) {
    val displayAddress = if (isIncome) senderAddress else recipientAddress

    val statusIcon = when(status) {
        Transaction.Status.COMPLETED -> R.drawable.ic_transaction_valid
        Transaction.Status.FAILED -> R.drawable.ic_transaction_failed
        Transaction.Status.PENDING -> R.drawable.ic_transaction_pending
    }

    val formattedAmount = createFormattedAmount()

    val amountColorRes = when {
        status == Transaction.Status.FAILED -> R.color.gray2
        isIncome -> R.color.green
        else -> R.color.white
    }

    private fun createFormattedAmount(): String {
        val withoutSign = "${amount.format()} ${token.displayName}"
        val sign = if (isIncome) '+' else '-'

        return sign + withoutSign
    }
}