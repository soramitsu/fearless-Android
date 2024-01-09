package jp.co.soramitsu.soracard.api.presentation.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentOrder(
    @SerialName("payment_id") val paymentId: String
)

@Serializable
data class PaymentOrderInfo(
    @SerialName("payment_id") val paymentId: String,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("deposit_transaction_number") val depositTransactionNumber: String,
    @SerialName("deposit_transaction_status") val depositTransactionStatus: String,
    @SerialName("order_transaction_number") val orderTransactionNumber: String,
    @SerialName("withdrawal_transaction_number") val withdrawalTransactionNumber: String
)
