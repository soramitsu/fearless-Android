package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class Operation(
    val hash: String,
    val address: String,
    val accountName: String?,
    val transactionType: TransactionType,
    val time: Long,
    val tokenType: Token.Type,
    val nextPageCursor: String? = null
) {

    sealed class TransactionType(
        val header: String?,
        val subheader: String?,
        val operationAmount: BigDecimal,
        val operationFee: BigDecimal,
        val status: Status
    ) {
        class Extrinsic(
            val hash: String,
            val module: String,
            val call: String,
            val fee: BigDecimal,
            val success: Boolean
        ) : TransactionType(call, module, operationAmount = BigDecimal.ZERO, operationFee = fee, Status.fromSuccess(success))

        class Reward(
            val amount: BigDecimal,
            val isReward: Boolean,
            val era: Int,
            val validator: String
        ) : TransactionType(if (isReward) "Reward" else "Slash", "Staking", operationAmount = amount, operationFee = BigDecimal.ZERO, Status.FAILED)

        class Transfer(
            val amount: BigDecimal,
            val receiver: String,
            val sender: String,
            val fee: BigDecimal
        ) : TransactionType(null, "Transfer", operationAmount = amount, operationFee = fee, Status.COMPLETED)
    }

    enum class Status {
        PENDING, COMPLETED, FAILED;

        companion object {
            fun fromSuccess(success: Boolean): Status {
                return if (success) COMPLETED else FAILED
            }
        }
    }


}
