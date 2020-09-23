package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketResponseListener
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketWrapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class RxWebSocket(private val mapper: Gson) {
    fun <S : Schema<S>> requestWithScaleResponse(
        request: RpcRequest,
        url: String,
        responseSchema: S
    ): Single<ScaleRpcResponse<S>> {
        var webSocket: WebSocketWrapper? = null

        return Single.fromPublisher<ScaleRpcResponse<S>> { publisher ->
            webSocket = WebSocketWrapper(url, object : WebSocketResponseListener {
                override fun onError(error: Throwable) {
                    publisher.onError(error)
                }

                override fun onResponse(response: RpcResponse) {
                    val scaleResponse = ScaleRpcResponse.from(response, responseSchema)

                    publisher.onNext(scaleResponse)
                    publisher.onComplete()
                }
            })

            webSocket!!.connect()

            webSocket!!.sendRpcRequest(request)
        }.doOnDispose { webSocket!!.disconnect() }
    }
}