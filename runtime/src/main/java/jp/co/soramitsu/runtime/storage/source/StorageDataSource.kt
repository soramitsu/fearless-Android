package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.NonNullBinder
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.flow.Flow

interface StorageDataSource {

    suspend fun <T> query(
        keyBuilder: (RuntimeSnapshot) -> String,
        binding: Binder<T>,
    ): T

    suspend fun <T> observe(
        networkType: Node.NetworkType,
        keyBuilder: (RuntimeSnapshot) -> String,
        binder: Binder<T>,
    ): Flow<T>
}

suspend inline fun <T> StorageDataSource.queryNonNull(
    noinline keyBuilder: (RuntimeSnapshot) -> String,
    crossinline binding: NonNullBinder<T>,
) = query(keyBuilder) { scale, runtime -> binding(scale!!, runtime) }

suspend inline fun <T> StorageDataSource.observeNonNull(
    networkType: Node.NetworkType,
    noinline keyBuilder: (RuntimeSnapshot) -> String,
    crossinline binding: NonNullBinder<T>,
) = observe(networkType, keyBuilder) { scale, runtime -> binding(scale!!, runtime) }
