package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.core_db.model.OperationLocal
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
        ) : TransactionType(operationAmount = BigDecimal.ZERO, operationFee = fee, Status.fromSuccess(success))

        class Reward(
            val amount: BigDecimal,
            val isReward: Boolean,
            val era: Int,
            val validator: String
        ) : TransactionType(operationAmount = amount, operationFee = BigDecimal.ZERO, Status.FAILED)

        class Transfer(
            val amount: BigDecimal,
            val receiver: String,
            val sender: String,
            val fee: BigDecimal
        ) : TransactionType(operationAmount = amount, operationFee = fee, Status.COMPLETED) {
            companion object {
                val transferCall = "Transfer"
            }
        }

        fun getHeader(): String? = when (this) {
            is Extrinsic -> call
            is Reward -> if (isReward) "Reward" else "Slash"
            is Transfer -> null
        }

        fun getSubheader(): String = when (this) {
            is Extrinsic -> module
            is Reward -> "Staking"
            is Transfer -> "Transfer"
        }

        fun getOperationType(): OperationLocal.OperationType = when (this) {
            is Extrinsic -> OperationLocal.OperationType.EXTRINSIC
            is Reward -> OperationLocal.OperationType.REWARD
            is Transfer -> OperationLocal.OperationType.TRANSFER
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
}
