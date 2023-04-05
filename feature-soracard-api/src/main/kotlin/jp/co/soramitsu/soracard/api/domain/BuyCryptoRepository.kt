package jp.co.soramitsu.soracard.api.domain

import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrder
import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrderInfo
import kotlinx.coroutines.flow.Flow

interface BuyCryptoRepository {

    suspend fun requestPaymentOrderStatus(paymentOrder: PaymentOrder)
    fun subscribePaymentOrderInfo(): Flow<PaymentOrderInfo>
}
