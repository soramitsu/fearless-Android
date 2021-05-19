package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.rpc.queryKey
import jp.co.soramitsu.common.data.network.rpc.retrieveAllValues
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RemoteStorageSource(
    runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val socketService: SocketService,
    private val bulkRetriever: BulkRetriever,
) : BaseStorageSource(runtimeProperty) {

    override suspend fun query(key: String): String? {
        return bulkRetriever.queryKey(key)
    }

    override suspend fun queryKeys(keys: List<String>): Map<String, String?> {
        return bulkRetriever.queryKeys(keys)
    }

    override suspend fun observe(key: String, networkType: Node.NetworkType): Flow<String?> {
        return socketService.subscriptionFlow(SubscribeStorageRequest(key))
            .map { it.storageChange().getSingleChange() }
    }

    override suspend fun queryByPrefix(prefix: String): Map<String, String?> {
        return bulkRetriever.retrieveAllValues(prefix)
    }
}
