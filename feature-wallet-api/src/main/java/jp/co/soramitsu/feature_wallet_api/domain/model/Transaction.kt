package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

class Transaction(
    val hash: String,
    val token: Asset.Token,
    val senderAddress: String,
    val recipientAddress: String,
    val amount: BigDecimal,
    val date: Long,
    val status: Status,
    val fee: Fee,
    val isIncome: Boolean
) {
    val total = amount + fee.amount!!

    enum class Status {
        PENDING, COMPLETED, FAILED;

        companion object {
            fun fromSuccess(success: Boolean): Status {
                return if (success) COMPLETED else FAILED
            }
        }
    }
}

class TransactionsPage(val transactions: List<Transaction>?)