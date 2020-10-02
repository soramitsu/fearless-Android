package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import io.reactivex.Single
import jp.co.soramitsu.fearless_utils.wsrpc.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketResponseListener
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketWrapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class RxWebSocket(
    private val jsonMapper: Gson,
    private val logger: Logger
) {
    fun <R> executeRequest(
        request: RpcRequest,
        url: String,
        mapper: ResponseMapper<R>
    ): Single<Mapped<R>> {
        return executeRequest(request, url)
            .map {
                val mapped = mapper.map(it, jsonMapper)

                Mapped(mapped)
            }
    }

    fun executeRequest(
        request: RpcRequest,
        url: String
    ): Single<RpcResponse> {
        var webSocket: WebSocketWrapper? = null

        return Single.fromPublisher<RpcResponse> { publisher ->
            webSocket = WebSocketWrapper(url, object : WebSocketResponseListener {
                override fun onError(error: Throwable) {
                    publisher.onError(error)
                }

                override fun onResponse(response: RpcResponse) {
                    publisher.onNext(response)
                    publisher.onComplete()
                }
            }, logger = logger)

            webSocket!!.connect()

            webSocket!!.sendRpcRequest(request)
        }.doOnDispose { webSocket!!.disconnect() }
    }
}