package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import io.reactivex.Single
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class SocketSingleRequestExecutor(
    private val jsonMapper: Gson,
    private val logger: Logger,
    private val wsFactory: WebSocketFactory,
    private val resourceManager: ResourceManager
) {
    fun <R> executeRequest(
        request: RpcRequest,
        url: String,
        mapper: ResponseMapper<R>
    ): Single<R> {
        return executeRequest(request, url)
            .map { mapper.map(it, jsonMapper) }
    }

    fun executeRequest(
        request: RpcRequest,
        url: String
    ): Single<RpcResponse> {
        val webSocket: WebSocket = wsFactory.createSocket(url)

        return Single.create<RpcResponse> { emitter ->
            webSocket.addListener(object : WebSocketAdapter() {
                override fun onTextMessage(websocket: WebSocket, text: String) {
                    logger.log("[RECEIVED] $text")

                    val response = jsonMapper.fromJson(text, RpcResponse::class.java)

                    emitter.onSuccess(response)

                    webSocket.disconnect()
                }

                override fun onError(websocket: WebSocket, cause: WebSocketException) {
                    emitter.tryOnError(cause)
                }
            })

            webSocket.connect()

            webSocket.sendText(jsonMapper.toJson(request))
        }.doOnDispose { webSocket.disconnect() }
            .onErrorResumeNext {
                val errorWrapper = FearlessException.networkError(resourceManager, it)
                Single.error(errorWrapper)
            }
    }
}