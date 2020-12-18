package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class Transaction(
    val hash: String,
    val type: Token.Type,
    val senderAddress: String,
    val recipientAddress: String,
    val amount: BigDecimal,
    val date: Long,
    val status: Status,
    val fee: BigDecimal?,
    val isIncome: Boolean
) {
    val total = fee?.plus(amount)

    enum class Status {
        PENDING, COMPLETED, FAILED;

        companion object {
            fun fromSuccess(success: Boolean): Status {
                return if (success) COMPLETED else FAILED
            }
        }
    }
}