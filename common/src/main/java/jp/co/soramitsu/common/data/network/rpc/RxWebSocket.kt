package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import io.reactivex.Single
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketResponseListener
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketWrapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class RxWebSocket(
    private val mapper: Gson,
    private val resourceManager: ResourceManager
) {

    fun <S : Schema<S>> requestWithScaleResponse(
        request: RpcRequest,
        url: String,
        responseSchema: S
    ): Single<ScaleRpcResponse<S>> {

        return adapt(request, url)
            .map { ScaleRpcResponse.from(it, responseSchema) }
    }

    fun requestWithStringResponse(
        request: RpcRequest,
        url: String
    ): Single<String> {

        return adapt(request, url)
            .map { it.result as String }
    }

    private fun adapt(request: RpcRequest, url: String): Single<RpcResponse> {

        var webSocket: WebSocketWrapper? = null
        return Single.create<RpcResponse> { emitter ->
            webSocket = WebSocketWrapper(url, object : WebSocketResponseListener {
                override fun onError(error: Throwable) {
                    emitter.onError(error)
                }

                override fun onResponse(response: RpcResponse) {
                    emitter.onSuccess(response)
                }
            })
            webSocket!!.connect()

            webSocket!!.sendRpcRequest(request)
        }
            .doOnDispose { webSocket!!.disconnect() }
            .onErrorResumeNext {
                val errorWrapper = FearlessException.networkError(resourceManager, it)
                Single.error(errorWrapper)
            }
    }
}