package jp.co.soramitsu.feature_wallet_impl.data.buyToken.rpc

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class RpcCall0<R>(
    moduleName: String,
    callName: String,
    socketService: SocketService,
    binder: (Any?) -> R,
) : RpcCallBase<R>(
    moduleName,
    callName,
    socketService,
    binder
) {

    suspend operator fun invoke(): R {
        return performCall(emptyList())
    }
}

class RpcCallList<A : Any, R>(
    moduleName: String,
    callName: String,
    socketService: SocketService,
    binder: (Any?) -> R,
) : RpcCallBase<R>(
    moduleName,
    callName,
    socketService,
    binder
) {

    suspend operator fun invoke(vararg arguments: A): R {
        return performCall(arguments.toList())
    }

    suspend operator fun invoke(arguments: List<A>): R {
        return performCall(arguments)
    }
}

class RpcCall1<A : Any, R>(
    moduleName: String,
    callName: String,
    socketService: SocketService,
    binder: (Any?) -> R,
) : RpcCallBase<R>(
    moduleName,
    callName,
    socketService,
    binder
) {

    suspend operator fun invoke(argument: A): R {
        return performCall(listOf(argument))
    }
}


abstract class RpcCallBase<R>(
    private val moduleName: String,
    private val callName: String,
    private val socketService: SocketService,
    private val binder: (Any?) -> R,
) {

    protected suspend fun performCall(params: List<Any>): R {
        val method = "${moduleName}_${callName}"

        val request = RuntimeRequest(method, params)

        return binder(socketService.executeAsync(request).result)
    }
}

