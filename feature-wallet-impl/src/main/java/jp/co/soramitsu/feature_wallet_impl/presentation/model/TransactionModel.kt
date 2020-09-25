package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
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
    val isIncome: Boolean
) {
    val displayAddress = if (isIncome) senderAddress else recipientAddress

    val formattedAmount = createFormattedAmount()

    val amountColorRes = if (isIncome) R.color.green else R.color.white

    private fun createFormattedAmount(): String {
        val withoutSign = "${amount.format()} ${token.displayName}"
        val sign = if (isIncome) '+' else '-'

        return sign + withoutSign
    }
}

fun Transaction.toUI() : TransactionModel {
    return TransactionModel(
        hash = hash,
        token = token,
        senderAddress = senderAddress,
        recipientAddress = recipientAddress,
        isIncome = isIncome,
        date = date,
        amount = amount
    )
}