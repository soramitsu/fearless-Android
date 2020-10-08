package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketResponseListener
import jp.co.soramitsu.fearless_utils.wsrpc.WebSocketWrapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import java.util.concurrent.ConcurrentHashMap

class RxWebSocketCreator(
    private val jsonMapper: Gson,
    private val logger: Logger,
    private val resourceManager: ResourceManager
) {
    fun createSocket(url: String) = RxWebSocket(jsonMapper, logger, url, resourceManager)
}

fun <T> Single<T>.provideLifecycleFor(webSocket: RxWebSocket) = doFinally { webSocket.disconnect() }
fun Completable.provideLifecycleFor(webSocket: RxWebSocket) = doFinally { webSocket.disconnect() }

class RxWebSocket(
    private val jsonMapper: Gson,
    private val logger: Logger,
    private val url: String,
    private val resourceManager: ResourceManager
) {
    private lateinit var socket: WebSocketWrapper
    private var responseSubject = BehaviorSubject.create<RpcResponse>()

    private val requestsMap = ConcurrentHashMap<Int, SingleEmitter<RpcResponse>>()

    private lateinit var disposable: Disposable

    init {
        createSocket()
    }

    fun connect(): Completable = Completable.fromAction(socket::connect)

    fun disconnect() {
        disposable.dispose()
        socket.disconnect()
        requestsMap.clear()
    }

    fun <R> executeRequest(
        request: RuntimeRequest,
        mapper: ResponseMapper<R>
    ): Single<Mapped<R>> {
        return executeRequest(request)
            .map {
                val mapped = mapper.map(it, jsonMapper)

                Mapped(mapped)
            }
    }

    fun executeRequest(
        runtimeRequest: RuntimeRequest
    ): Single<RpcResponse> {
        return Single.create<RpcResponse> {
            socket.sendRpcRequest(runtimeRequest)

            requestsMap[runtimeRequest.id] = it
        }.onErrorResumeNext {
            val errorWrapper = FearlessException.networkError(resourceManager, it)
            Single.error(errorWrapper)
        }
    }

    private fun createSocket() {
        socket = WebSocketWrapper(url, object : WebSocketResponseListener {
            override fun onError(error: Throwable) {
                responseSubject.onError(error)
            }

            override fun onResponse(response: RpcResponse) {
                responseSubject.onNext(response)
            }
        }, logger = logger, singleResponse = false)

        disposable = responseSubject.subscribe({
            val emitter = requestsMap.remove(it.id)

            emitter?.onSuccess(it)
        }, { error ->
            requestsMap.values.forEach { it.onError(error) }
        })
    }
}