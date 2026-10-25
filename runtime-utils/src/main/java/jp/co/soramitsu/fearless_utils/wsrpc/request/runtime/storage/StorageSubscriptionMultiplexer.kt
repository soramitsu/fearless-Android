package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.UnsubscribeMethodResolver
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange

typealias MultiplexerCallback = SocketService.ResponseListener<StorageSubscriptionMultiplexer.Change>

class StorageSubscriptionMultiplexer(
    private val callbacks: Map<String, List<SocketService.ResponseListener<Change>>>
) : SocketService.ResponseListener<SubscriptionChange> {

    class Change(val block: String, val key: String, val value: String?)

    fun createRequest(): RuntimeRequest {
        return SubscribeStorageRequest(callbacks.keys.toList())
    }

    override fun onNext(response: SubscriptionChange) {
        val storageChange = response.storageChange()

        storageChange.changes.forEach { (key, changeValue) ->
            val change = Change(storageChange.block, key!!, changeValue)

            val keyCallbacks = callbacks[key]

            keyCallbacks?.forEach { it.onNext(change) }
        }
    }

    override fun onError(throwable: Throwable) {
        callbacks.values.flatten().onEach { it.onError(throwable) }
    }

    class Builder {
        private val callbacks = mutableMapOf<String, MutableList<MultiplexerCallback>>()

        fun subscribe(key: String, callback: MultiplexerCallback): Builder {
            val currentList = callbacks.getOrPut(key) { mutableListOf() }

            currentList.add(callback)

            return this
        }

        fun build() = StorageSubscriptionMultiplexer(callbacks)
    }
}

fun SocketService.subscribeUsing(multiplexer: StorageSubscriptionMultiplexer): SocketService.Cancellable {
    val request = multiplexer.createRequest()

    return subscribe(request, multiplexer, UnsubscribeMethodResolver.resolve(request.method))
}
