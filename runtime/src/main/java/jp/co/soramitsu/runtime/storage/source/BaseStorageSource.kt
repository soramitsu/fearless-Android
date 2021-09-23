package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.rpc.childStateKey
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

abstract class BaseStorageSource(
    protected val chainRegistry: ChainRegistry
) : StorageDataSource {

    protected abstract suspend fun query(key: String, chainId: String, at: BlockHash?): String?

    protected abstract suspend fun queryKeys(keys: List<String>, chainId: String, at: BlockHash?): Map<String, String?>

    protected abstract suspend fun observe(key: String, chainId: String): Flow<String?>

    protected abstract suspend fun queryByPrefix(prefix: String, chainId: String): Map<String, String?>

    protected abstract suspend fun queryChildState(storageKey: String, childKey: String, chainId: String): String?

    override suspend fun <K, T> queryByPrefix(
        chainId: String,
        prefixKeyBuilder: (RuntimeSnapshot) -> StorageKey,
        keyExtractor: (String) -> K,
        binding: BinderWithKey<T, K>
    ): Map<K, T> {
        val runtime = chainRegistry.getRuntime(chainId)

        val prefix = prefixKeyBuilder(runtime)

        val rawResults = queryByPrefix(prefix, chainId)

        return rawResults.mapKeys { (fullKey, _) -> keyExtractor(fullKey) }
            .mapValues { (key, hexRaw) -> binding(hexRaw, runtime, key) }
    }

    override suspend fun <K, T> queryKeys(
        chainId: String,
        keysBuilder: (RuntimeSnapshot) -> Map<StorageKey, K>,
        at: BlockHash?,
        binding: Binder<T>,
    ): Map<K, T> = withContext(Dispatchers.Default) {
        val runtime = chainRegistry.getRuntime(chainId)

        val storageKeyToMapId = keysBuilder(runtime)

        val queryResults = queryKeys(storageKeyToMapId.keys.toList(), chainId, at)

        queryResults.mapKeys { (fullKey, _) -> storageKeyToMapId[fullKey]!! }
            .mapValues { (_, hexRaw) -> binding(hexRaw, runtime) }
    }

    override suspend fun <T> query(
        chainId: String,
        keyBuilder: (RuntimeSnapshot) -> String,
        at: BlockHash?,
        binding: Binder<T>,
    ) = withContext(Dispatchers.Default) {
        val runtime = chainRegistry.getRuntime(chainId)

        val key = keyBuilder(runtime)
        val rawResult = query(key, chainId, at)

        binding(rawResult, runtime)
    }

    override fun <T> observe(
        chainId: String,
        keyBuilder: (RuntimeSnapshot) -> String,
        binder: Binder<T>,
    ) = flow {
        val runtime = chainRegistry.getRuntime(chainId)
        val key = keyBuilder(runtime)

        emitAll(
            observe(key, chainId).map { binder(it, runtime) }
        )
    }

    override suspend fun <T> queryChildState(
        chainId: String,
        storageKeyBuilder: (RuntimeSnapshot) -> StorageKey,
        childKeyBuilder: ChildKeyBuilder,
        binder: Binder<T>
    ) = withContext(Dispatchers.Default) {
        val runtime = chainRegistry.getRuntime(chainId)

        val storageKey = storageKeyBuilder(runtime)

        val childKey = childStateKey {
            childKeyBuilder(runtime)
        }

        val scaleResult = queryChildState(storageKey, childKey, chainId)

        binder(scaleResult, runtime)
    }
}
