package jp.co.soramitsu.common.data.network

import jp.co.soramitsu.core.model.StorageChange
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.StorageSubscriptionMultiplexer
import jp.co.soramitsu.fearless_utils.wsrpc.subscribe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StorageSubscriptionBuilder(
    val proxy: StorageSubscriptionMultiplexer.Builder
) : SubscriptionBuilder {

    companion object {

        fun create(): StorageSubscriptionBuilder {
            val proxy = StorageSubscriptionMultiplexer.Builder()

            return StorageSubscriptionBuilder(proxy)
        }
    }

    override fun subscribe(key: String): Flow<StorageChange> {
        return proxy.subscribe(key)
            .map { StorageChange(it.block, it.key, it.value) }
    }
}