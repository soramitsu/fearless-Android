package jp.co.soramitsu.soracard.api.domain

import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrder
import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrderInfo
import kotlinx.coroutines.flow.Flow

interface BuyCryptoDataSource {

    suspend fun requestPaymentOrderStatus(paymentOrder: PaymentOrder)
    suspend fun subscribePaymentOrderInfo(): Flow<PaymentOrderInfo>
}
