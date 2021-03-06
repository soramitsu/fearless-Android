package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.data.network.runtime.binding.NonNullBinder
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream

typealias StorageKey = String
typealias ChildKeyBuilder = suspend OutputStream.(RuntimeSnapshot) -> Unit

interface StorageDataSource {

    suspend fun <T> query(
        keyBuilder: (RuntimeSnapshot) -> StorageKey,
        binding: Binder<T>,
    ): T

    suspend fun <K, T> queryKeys(
        keysBuilder: (RuntimeSnapshot) -> Map<StorageKey, K>,
        binding: Binder<T>,
    ): Map<K, T>

    fun <T> observe(
        networkType: Node.NetworkType,
        keyBuilder: (RuntimeSnapshot) -> StorageKey,
        binder: Binder<T>,
    ): Flow<T>

    suspend fun <K, T> queryByPrefix(
        prefixKeyBuilder: (RuntimeSnapshot) -> StorageKey,
        keyExtractor: (String) -> K,
        binding: BinderWithKey<T, K>,
    ): Map<K, T>

    suspend fun <T> queryChildState(
        storageKeyBuilder: (RuntimeSnapshot) -> StorageKey,
        childKeyBuilder: ChildKeyBuilder,
        binder: Binder<T>
    ): T
}

suspend inline fun <T> StorageDataSource.queryNonNull(
    noinline keyBuilder: (RuntimeSnapshot) -> String,
    crossinline binding: NonNullBinder<T>,
) = query(keyBuilder) { scale, runtime -> binding(scale!!, runtime) }

inline fun <T> StorageDataSource.observeNonNull(
    networkType: Node.NetworkType,
    noinline keyBuilder: (RuntimeSnapshot) -> String,
    crossinline binding: NonNullBinder<T>,
) = observe(networkType, keyBuilder) { scale, runtime -> binding(scale!!, runtime) }
