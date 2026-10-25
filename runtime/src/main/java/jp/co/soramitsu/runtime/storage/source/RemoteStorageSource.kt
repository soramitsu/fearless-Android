package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.rpc.QueryStorageAtRequest
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.rpc.queryKey
import jp.co.soramitsu.common.data.network.rpc.retrieveAllValues
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.catch
import jp.co.soramitsu.core.runtime.models.requests.GetChildStateRequest
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.subscriptionFlowCatching
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RemoteStorageSource(
    chainRegistry: ChainRegistry,
    private val bulkRetriever: BulkRetriever
) : BaseStorageSource(chainRegistry) {

    override suspend fun query(key: String, chainId: String, at: BlockHash?): String? {
        return kotlin.runCatching { bulkRetriever.queryKey(getSocketService(chainId), key, at) }
            .getOrNull()
    }

    override suspend fun queryKeys(
        keys: List<String>,
        chainId: String,
        at: BlockHash?
    ): Map<String, String?> {
        return kotlin.runCatching { bulkRetriever.queryKeys(getSocketService(chainId), keys, at) }
            .getOrDefault(
                emptyMap()
            )
    }

    override suspend fun observe(key: String, chainId: String): Flow<String?> {
        return getSocketService(chainId).subscriptionFlowCatching(SubscribeStorageRequest(key))
            .map { result ->
                result.getOrNull()?.storageChange()?.getSingleChange()
            }
    }

    override suspend fun <T> queryResult(
        chainId: String,
        keyBuilder: (RuntimeSnapshot) -> StorageKey?,
        at: BlockHash?,
        binding: Binder<T>
    ): Result<T> = try {
        withContext(Dispatchers.Default) {
            val runtime = chainRegistry.getRuntime(chainId)
            val key = requireNotNull(keyBuilder(runtime)) { "Storage key is unavailable" }
            val response = getSocketService(chainId).executeAsync(QueryStorageAtRequest(listOf(key), at))
            require(response.error == null) { "Storage query failed" }
            val changes = response.result as? List<*> ?: error("Invalid storage query response")
            require(changes.size == 1) { "Storage query must contain exactly one change set" }
            Result.success(binding(acknowledgedValue(changes.single(), key, at), runtime))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    override fun <T> observeResult(
        chainId: String,
        keyBuilder: (RuntimeSnapshot) -> StorageKey?,
        binder: Binder<T>
    ): Flow<Result<T>> = flow {
        val runtime = chainRegistry.getRuntime(chainId)
        val key = requireNotNull(keyBuilder(runtime)) { "Storage key is unavailable" }
        emitAll(getSocketService(chainId).subscriptionFlowCatching(SubscribeStorageRequest(key)).map { response ->
            try {
                val value = acknowledgedValue(response.getOrThrow().params.result, key)
                Result.success(binder(value, runtime))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        })
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(Result.failure(error))
    }

    // Parse the raw response before any mapping can erase duplicate rows or coerce values.
    private fun acknowledgedValue(result: Any?, key: String, at: BlockHash? = null): String? {
        val change = result as? Map<*, *> ?: error("Invalid storage change set")
        val block = change["block"] as? String ?: error("Missing storage block hash")
        require(block.matches(Regex("0x[0-9a-fA-F]{64}"))) { "Invalid storage block hash" }
        require(at == null || block.equals(at, ignoreCase = true)) { "Storage response is for a different block" }
        val changes = change["changes"] as? List<*> ?: error("Missing storage changes")
        require(changes.size == 1) { "Storage response must contain the requested key" }
        val entry = changes.single() as? List<*> ?: error("Invalid storage change")
        require(entry.size == 2 && entry[0] == key) { "Storage response does not acknowledge the requested key" }
        val value = entry[1]
        require(value == null || value is String && value.matches(Regex("0x(?:[0-9a-fA-F]{2})*"))) {
            "Storage value must be encoded bytes or null"
        }
        return value as String?
    }

    override suspend fun queryByPrefix(prefix: String, chainId: String): Map<String, String?> {
        return kotlin.runCatching {
            bulkRetriever.retrieveAllValues(
                getSocketService(chainId),
                prefix
            )
        }.getOrDefault(
            emptyMap()
        )
    }

    override suspend fun queryChildState(
        storageKey: String,
        childKey: String,
        chainId: String
    ): String? {
        val response =
            kotlin.runCatching { getSocketService(chainId).executeAsync(GetChildStateRequest(storageKey, childKey)) }.getOrNull()

        return response?.result as? String?
    }

    private suspend fun getSocketService(chainId: String) =
        chainRegistry.awaitConnection(chainId).socketService
}
