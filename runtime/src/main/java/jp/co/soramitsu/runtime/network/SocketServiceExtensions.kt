package jp.co.soramitsu.runtime.network

import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.executeAsync
import jp.co.soramitsu.shared_utils.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.shared_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.UnsubscribeMethodResolver
import jp.co.soramitsu.shared_utils.wsrpc.subscription.response.SubscriptionChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

fun SocketService.subscriptionFlowCatching(
    request: RuntimeRequest,
    unsubscribeMethod: String = UnsubscribeMethodResolver.resolve(request.method)
): Flow<Result<SubscriptionChange>> {
    return kotlin.runCatching {
        callbackFlow {
            val cancellable =
                subscribe(
                    request,
                    object : SocketService.ResponseListener<SubscriptionChange> {
                        override fun onNext(response: SubscriptionChange) {
                            trySend(Result.success(response))
                        }

                        override fun onError(throwable: Throwable) {
                            trySend(Result.failure(throwable))
                        }
                    },
                    unsubscribeMethod
                )

            awaitClose {
                cancellable.cancel()
            }
        }
    }.getOrNull() ?: emptyFlow()
}

suspend fun <R> SocketService.executeAsyncCatching(
    request: RuntimeRequest,
    deliveryType: DeliveryType = DeliveryType.AT_LEAST_ONCE,
    mapper: ResponseMapper<R>
): Result<R> {
    return runCatching {
        val response = executeAsync(request, deliveryType)

        withContext(Dispatchers.Default) {
            mapper.map(response, jsonMapper)
        }
    }
}