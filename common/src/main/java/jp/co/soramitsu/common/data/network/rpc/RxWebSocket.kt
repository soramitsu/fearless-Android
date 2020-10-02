package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import io.reactivex.Single
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketResponseListener
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketWrapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class RxWebSocket(
    private val jsonMapper: Gson,
    private val logger: Logger,
    private val resourceManager: ResourceManager
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

        return Single.create<RpcResponse> { emitter ->
            webSocket = WebSocketWrapper(url, object : WebSocketResponseListener {
                override fun onError(error: Throwable) {
                    emitter.onError(error)
                }

                override fun onResponse(response: RpcResponse) {
                    emitter.onSuccess(response)
                }
            }, logger = logger)

            webSocket!!.connect()

            webSocket!!.sendRpcRequest(request)
        }.doOnDispose { webSocket!!.disconnect() }
            .onErrorResumeNext {
                val errorWrapper = FearlessException.networkError(resourceManager, it)
                Single.error(errorWrapper)
            }
    }
}