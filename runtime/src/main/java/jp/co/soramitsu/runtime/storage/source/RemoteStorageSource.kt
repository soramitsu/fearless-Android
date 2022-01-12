package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.rpc.queryKey
import jp.co.soramitsu.common.data.network.rpc.retrieveAllValues
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.common.data.network.runtime.calls.GetChildStateRequest
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RemoteStorageSource(
    chainRegistry: ChainRegistry,
    private val bulkRetriever: BulkRetriever,
) : BaseStorageSource(chainRegistry) {

    override suspend fun query(key: String, chainId: String, at: BlockHash?): String? {
        return bulkRetriever.queryKey(getSocketService(chainId), key, at)
    }

    override suspend fun queryKeys(keys: List<String>, chainId: String, at: BlockHash?): Map<String, String?> {
        return bulkRetriever.queryKeys(getSocketService(chainId), keys, at)
    }

    override suspend fun observe(key: String, chainId: String): Flow<String?> {
        return getSocketService(chainId).subscriptionFlow(SubscribeStorageRequest(key))
            .map { it.storageChange().getSingleChange() }
    }

    override suspend fun queryByPrefix(prefix: String, chainId: String): Map<String, String?> {
        return bulkRetriever.retrieveAllValues(getSocketService(chainId), prefix)
    }

    override suspend fun queryChildState(storageKey: String, childKey: String, chainId: String): String? {
        val response = getSocketService(chainId).executeAsync(GetChildStateRequest(storageKey, childKey))

        return response.result as? String?
    }

    private fun getSocketService(chainId: String) = chainRegistry.getConnection(chainId).socketService
}
