package jp.co.soramitsu.feature_wallet_impl.data.buyToken.rpc

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RpcSubscription0<R>(
    moduleName: String,
    callName: String,
    socketService: SocketService,
    binder: (SubscriptionChange) -> R,
) : RpcSubscriptionBase<R>(
    moduleName,
    callName,
    socketService,
    binder
) {

    operator fun invoke(): Flow<R> {
        return subscribe(emptyList())
    }
}

class RpcSubscriptionList<A : Any, R>(
    moduleName: String,
    callName: String,
    socketService: SocketService,
    binder: (SubscriptionChange) -> R,
) : RpcSubscriptionBase<R>(
    moduleName,
    callName,
    socketService,
    binder
) {

    operator fun invoke(vararg arguments: A): Flow<R> {
        return subscribe(arguments.toList())
    }

    operator fun invoke(arguments: List<A>): Flow<R> {
        return subscribe(arguments)
    }
}

class RpcSubscription1<A : Any, R>(
    moduleName: String,
    callName: String,
    socketService: SocketService,
    binder: (SubscriptionChange) -> R,
) : RpcSubscriptionBase<R>(
    moduleName,
    callName,
    socketService,
    binder
) {

    operator fun invoke(argument: A): Flow<R> {
        return subscribe(listOf(argument))
    }
}

abstract class RpcSubscriptionBase<R>(
    private val moduleName: String,
    private val callName: String,
    private val socketService: SocketService,
    private val binder: (SubscriptionChange) -> R,
) {

    protected fun subscribe(params: List<Any>): Flow<R> {
        val method = "${moduleName}_${callName}"

        val request = RuntimeRequest(method, params)

        return socketService.subscriptionFlow(request)
            .map { binder(it) }
    }
}
