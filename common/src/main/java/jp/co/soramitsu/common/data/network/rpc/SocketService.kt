package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketState
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.data.network.rpc.mappers.ResponseMapper
import jp.co.soramitsu.common.data.network.rpc.recovery.ConstantReconnectStrategy
import jp.co.soramitsu.common.data.network.rpc.recovery.ExponentialReconnectStrategy
import jp.co.soramitsu.common.data.network.rpc.recovery.ReconnectStrategy
import jp.co.soramitsu.common.data.network.rpc.socket.RpcSocket
import jp.co.soramitsu.common.data.network.rpc.socket.RpcSocketListener
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.fearless_utils.wsrpc.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

enum class State {
    CONNECTED, WAITING_RECONNECT, CONNECTING, DISCONNECTED
}

class ConnectionClosedException : Exception()

enum class DeliveryType {
    /**
     * For idempotent requests will not produce error and try to to deliver after reconnect
     */
    AT_LEAST_ONCE,

    /**
     * For non-idempotent requests, will produce an error if fails to deliver/get response
     */
    AT_MOST_ONCE
}

class RequestMapEntry(
    val request: RuntimeRequest,
    val deliveryType: DeliveryType,
    val emitter: SingleEmitter<RpcResponse>
)

private val WAITING_EXECUTOR = ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, ArrayBlockingQueue(10))
private val WAITING_SCHEDULER = Schedulers.from(WAITING_EXECUTOR)

private val DEFAULT_RECONNECT_STRATEGY = ExponentialReconnectStrategy(initialTime = 300L, base = 2.0)

class SocketService(
    private val jsonMapper: Gson,
    private val logger: Logger,
    private val reconnectStrategy: ReconnectStrategy = DEFAULT_RECONNECT_STRATEGY
) : RpcSocketListener, ConnectionManager {
    private var socket: RpcSocket? = null

    private val requestsMap = ConcurrentHashMap<Int, RequestMapEntry>()

    private val pendingRequests = ArrayBlockingQueue<RpcRequest>(10)
    private val waitingForResponseRequests = ArrayBlockingQueue<RpcRequest>(10)

    private val socketFactory = WebSocketFactory()

    @Volatile
    private var currentReconnectAttempt = 0

    @Volatile
    private var reconnectWaitDisposable: Disposable? = null

    @Volatile
    private var resendPendingDisposable: Disposable? = null

    @Volatile
    private var state: State = State.DISCONNECTED

    override fun switchUrl(url: String) {
        stop()

        start(url)
    }

    override fun started() = state != State.DISCONNECTED

    @Synchronized
    override fun start(url: String) {
        if (state != State.DISCONNECTED) return

        socket = createSocket(url)

        state = State.CONNECTING

        currentReconnectAttempt = 0

        socket!!.connectAsync()
    }

    @Synchronized
    override fun stop() {
        if (state == State.DISCONNECTED) return

        resendPendingDisposable?.dispose()

        pendingRequests.clear()
        waitingForResponseRequests.clear()
        requestsMap.clear()

        socket!!.clearListeners()
        socket!!.disconnect()

        reconnectWaitDisposable?.dispose()

        socket = null

        state = State.DISCONNECTED
    }

    fun <R> executeRequest(
        request: RuntimeRequest,
        responseType: ResponseMapper<R>,
        deliveryType: DeliveryType = DeliveryType.AT_LEAST_ONCE
    ): Single<R> {
        return executeRequest(request, deliveryType)
            .map { responseType.map(it, jsonMapper) }
    }

    fun executeRequest(
        runtimeRequest: RuntimeRequest,
        deliveryType: DeliveryType = DeliveryType.AT_LEAST_ONCE
    ): Single<RpcResponse> {
        return Single.create<RpcResponse> {
            synchronized<Unit>(this) {
                requestsMap[runtimeRequest.id] = RequestMapEntry(runtimeRequest, deliveryType, it)

                if (state == State.CONNECTED) {
                    socket!!.sendRpcRequest(runtimeRequest)
                    waitingForResponseRequests.add(runtimeRequest)
                } else {
                    logger.log("[PENDING REQUEST] ${runtimeRequest.method}")

                    pendingRequests.add(runtimeRequest)

                    if (state == State.WAITING_RECONNECT) forceReconnect()
                }
            }
        }.doOnDispose { cancelRequest(runtimeRequest) }
    }

    @Synchronized
    override fun onResponse(rpcResponse: RpcResponse) {
        val requestMapEntry = requestsMap.remove(rpcResponse.id)

        requestMapEntry?.let {
            waitingForResponseRequests.remove(it.request)

            it.emitter.onSuccess(rpcResponse)
        }
    }

    @Synchronized
    override fun onStateChanged(newState: WebSocketState) {
        if (newState == WebSocketState.CLOSED) {
            reconnectDelayed()

            reportErrorToAtMostOnceAndForget()

            moveWaitingResponseToPending()
        }
    }

    override fun onConnected() {
        connectionEstablished()
    }

    private fun cancelRequest(runtimeRequest: RuntimeRequest) = synchronized<Unit>(this) {
        requestsMap.remove(runtimeRequest.id)
        waitingForResponseRequests.remove(runtimeRequest)
        pendingRequests.remove(runtimeRequest)
    }

    private fun moveWaitingResponseToPending() {
        pendingRequests.addAll(waitingForResponseRequests)
        waitingForResponseRequests.clear()
    }

    private fun reportErrorToAtMostOnceAndForget() {
        requestsMap.values.filter { it.deliveryType == DeliveryType.AT_MOST_ONCE }
            .forEach {
                it.emitter.tryOnError(ConnectionClosedException())

                waitingForResponseRequests.remove(it.request)
                requestsMap.remove(it.request.id)
            }
    }

    @Synchronized
    private fun connectionEstablished() {
        currentReconnectAttempt = 0

        state = State.CONNECTED

        resendPendingDisposable = Completable.fromAction {
            pendingRequests.forEach {
                socket!!.sendRpcRequest(it)
                waitingForResponseRequests.add(it)

                pendingRequests.remove(it)
            }
        }
            .doFinally { resendPendingDisposable = null }
            .subscribeToError {
                //ignore
            }
    }

    @Synchronized
    private fun reconnectDelayed() {
        if (reconnectWaitDisposable != null) return

        socket!!.clearListeners()

        currentReconnectAttempt++

        state = State.WAITING_RECONNECT

        val waitTime = reconnectStrategy.getTimeForReconnect(currentReconnectAttempt)

        logger.log("[WAITING FOR RECONNECT] $waitTime ms")

        reconnectWaitDisposable = Completable.timer(waitTime, TimeUnit.MILLISECONDS, WAITING_SCHEDULER)
            .subscribe {
                reconnectNow()
            }
    }

    @Synchronized
    private fun reconnectNow() {
        if (state == State.CONNECTING) return

        clearCurrentWaitingTask()

        state = State.CONNECTING

        socket = createSocket(socket!!.url)

        socket!!.connectAsync()
    }

    private fun clearCurrentWaitingTask() {
        if (reconnectWaitDisposable != null && !reconnectWaitDisposable!!.isDisposed) reconnectWaitDisposable!!.dispose()

        reconnectWaitDisposable = null
    }

    private fun forceReconnect() {
        currentReconnectAttempt = 0

        reconnectNow()
    }

    private fun createSocket(url: String) = RpcSocket(url, this, logger, socketFactory, jsonMapper)
}