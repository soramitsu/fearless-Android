package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.common.data.network.runtime.binding.NonNullBinder
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream

typealias StorageKey = String
typealias ChildKeyBuilder = suspend OutputStream.(RuntimeSnapshot) -> Unit

interface StorageDataSource {

    suspend fun <T> query(
        chainId: String,
        keyBuilder: (RuntimeSnapshot) -> StorageKey,
        at: BlockHash? = null,
        binding: Binder<T>,
    ): T

    suspend fun <K, T> queryKeys(
        chainId: String,
        keysBuilder: (RuntimeSnapshot) -> Map<StorageKey, K>,
        at: BlockHash? = null,
        binding: Binder<T>,
    ): Map<K, T>

    fun <T> observe(
        chainId: String,
        keyBuilder: (RuntimeSnapshot) -> StorageKey,
        binder: Binder<T>,
    ): Flow<T>

    suspend fun <K, T> queryByPrefix(
        chainId: String,
        prefixKeyBuilder: (RuntimeSnapshot) -> StorageKey,
        keyExtractor: (String) -> K,
        binding: BinderWithKey<T, K>,
    ): Map<K, T>

    suspend fun <T> queryChildState(
        chainId: String,
        storageKeyBuilder: (RuntimeSnapshot) -> StorageKey,
        childKeyBuilder: ChildKeyBuilder,
        binder: Binder<T>
    ): T
}

suspend inline fun <T> StorageDataSource.queryNonNull(
    chainId: String,
    noinline keyBuilder: (RuntimeSnapshot) -> String,
    crossinline binding: NonNullBinder<T>,
    at: BlockHash? = null
) = query(chainId, keyBuilder, at) { scale, runtime -> binding(scale!!, runtime) }

inline fun <T> StorageDataSource.observeNonNull(
    chainId: String,
    noinline keyBuilder: (RuntimeSnapshot) -> String,
    crossinline binding: NonNullBinder<T>,
) = observe(chainId, keyBuilder) { scale, runtime -> binding(scale!!, runtime) }
