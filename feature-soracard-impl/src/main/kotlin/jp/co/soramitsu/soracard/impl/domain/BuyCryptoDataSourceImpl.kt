package jp.co.soramitsu.soracard.impl.domain

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.soracard.api.domain.BuyCryptoDataSource
import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrder
import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrderInfo
import jp.co.soramitsu.soracard.impl.data.websocket.WebSocket
import jp.co.soramitsu.soracard.impl.data.websocket.WebSocketListener
import jp.co.soramitsu.soracard.impl.data.websocket.WebSocketRequest
import jp.co.soramitsu.soracard.impl.data.websocket.WebSocketResponse
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BuyCryptoDataSourceImpl(
    clientProvider: SoramitsuHttpClientProvider
) : BuyCryptoDataSource {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val paymentOrderFlow = MutableSharedFlow<PaymentOrderInfo>()

    private val webSocketListener = object : WebSocketListener {
        override suspend fun onResponse(response: WebSocketResponse) {
            val paymentOrderInfo = json.decodeFromString<PaymentOrderInfo>(response.json)
            paymentOrderFlow.emit(paymentOrderInfo)
            paymentOrderWebSocket.disconnect()
        }

        override fun onSocketClosed() {
        }

        override fun onConnected() {
        }
    }

    private val paymentOrderWebSocket: WebSocket = WebSocket(
        url = BuildConfig.X1_PAYMENT_STATUS_URL,
        listener = webSocketListener,
        json = json,
        logging = false,
        provider = clientProvider
    )

    override suspend fun requestPaymentOrderStatus(paymentOrder: PaymentOrder) {
        try {
            paymentOrderWebSocket.sendRequest(
                request = WebSocketRequest(json = json.encodeToString(paymentOrder))
            )
        } catch (_: Throwable) {
        }
    }

    override fun subscribePaymentOrderInfo(): Flow<PaymentOrderInfo> = paymentOrderFlow
}
