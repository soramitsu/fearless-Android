package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.rpc.childStateKey
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

abstract class BaseStorageSource(
    protected val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
) : StorageDataSource {

    protected abstract suspend fun query(key: String): String?

    protected abstract suspend fun queryKeys(keys: List<String>): Map<String, String?>

    protected abstract suspend fun observe(key: String, networkType: Node.NetworkType): Flow<String?>

    protected abstract suspend fun queryByPrefix(prefix: String): Map<String, String?>

    protected abstract suspend fun queryChildState(storageKey: String, childKey: String): String?

    override suspend fun <K, T> queryByPrefix(
        prefixKeyBuilder: (RuntimeSnapshot) -> StorageKey,
        keyExtractor: (String) -> K,
        binding: BinderWithKey<T, K>
    ): Map<K, T> {
        val runtime = getRuntime()

        val prefix = prefixKeyBuilder(runtime)

        val rawResults = queryByPrefix(prefix)

        return rawResults.mapKeys { (fullKey, _) -> keyExtractor(fullKey) }
            .mapValues { (key, hexRaw) -> binding(hexRaw, runtime, key) }
    }

    override suspend fun <K, T> queryKeys(
        keysBuilder: (RuntimeSnapshot) -> Map<StorageKey, K>,
        binding: Binder<T>,
    ): Map<K, T> = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val storageKeyToMapId = keysBuilder(runtime)

        val queryResults = queryKeys(storageKeyToMapId.keys.toList())

        queryResults.mapKeys { (fullKey, _) -> storageKeyToMapId[fullKey]!! }
            .mapValues { (_, hexRaw) -> binding(hexRaw, runtime) }
    }

    override suspend fun <T> query(
        keyBuilder: (RuntimeSnapshot) -> String,
        binding: Binder<T>,
    ) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val key = keyBuilder(runtime)
        val rawResult = query(key)

        binding(rawResult, runtime)
    }

    override fun <T> observe(
        networkType: Node.NetworkType,
        keyBuilder: (RuntimeSnapshot) -> String,
        binder: Binder<T>,
    ) = flow {
        val runtime = getRuntime()
        val key = keyBuilder(runtime)

        emitAll(
            observe(key, networkType).map { binder(it, runtime) }
        )
    }

    private suspend fun getRuntime() = runtimeProperty.get()

    override suspend fun <T> queryChildState(
        storageKeyBuilder: (RuntimeSnapshot) -> StorageKey,
        childKeyBuilder: ChildKeyBuilder,
        binder: Binder<T>
    ) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val storageKey = storageKeyBuilder(runtime)

        val childKey = childStateKey {
            childKeyBuilder(runtime)
        }

        val scaleResult = queryChildState(storageKey, childKey)

        binder(scaleResult, runtime)
    }
}
