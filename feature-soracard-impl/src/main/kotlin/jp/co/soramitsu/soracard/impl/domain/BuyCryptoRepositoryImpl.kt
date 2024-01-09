package jp.co.soramitsu.soracard.impl.domain

import jp.co.soramitsu.soracard.api.domain.BuyCryptoDataSource
import jp.co.soramitsu.soracard.api.domain.BuyCryptoRepository
import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrder
import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrderInfo
import kotlinx.coroutines.flow.Flow

class BuyCryptoRepositoryImpl(
    private val buyCryptoDataSource: BuyCryptoDataSource
) : BuyCryptoRepository {

    override suspend fun requestPaymentOrderStatus(paymentOrder: PaymentOrder) {
        buyCryptoDataSource.requestPaymentOrderStatus(paymentOrder)
    }

    override fun subscribePaymentOrderInfo(): Flow<PaymentOrderInfo> =
        buyCryptoDataSource.subscribePaymentOrderInfo()
}
