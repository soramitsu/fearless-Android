package jp.co.soramitsu.feature_wallet_impl.data.buyToken.query

import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.SubstrateApi
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.rpc.getStorage
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.rpc.state
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.rpc.subscribeStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

abstract class StorageEntryBase<R>(
    protected val runtime: RuntimeSnapshot,
    protected val storageEntryMetadata: StorageEntry,
    private val api: SubstrateApi,
    val binder: (Any?) -> R,
) {

    protected suspend fun query(key: String): R? {
        val result: String = api.rpc.state.getStorage(key) ?: return null

        val decoded = storageEntryMetadata.type.value!!.fromHexOrIncompatible(result, runtime)

        return binder(decoded)
    }

    protected fun subscribe(key: String): Flow<R?> {
        return api.rpc.state.subscribeStorage(key)
            .map {
                it?.let {
                    val decoded = storageEntryMetadata.type.value!!.fromHexOrIncompatible(it, runtime)

                    binder(decoded)
                }
            }
    }
}

class PlainStorageEntry<R>(
    runtime: RuntimeSnapshot,
    storageEntryMetadata: StorageEntry,
    api: SubstrateApi,
    binder: (Any?) -> R,
) : StorageEntryBase<R>(runtime, storageEntryMetadata, api, binder) {

    suspend operator fun invoke(): R? {
        return query(storageEntryMetadata.storageKey())
    }

    fun subscribe(): Flow<R?> = subscribe(storageEntryMetadata.storageKey())
}

class SingleMapStorageEntry<K, R>(
    runtime: RuntimeSnapshot,
    storageEntryMetadata: StorageEntry,
    api: SubstrateApi,
    binder: (Any?) -> R,
) : StorageEntryBase<R>(runtime, storageEntryMetadata, api, binder) {

    suspend operator fun invoke(key: K): R? {
        return query(storageEntryMetadata.storageKey(runtime, key))
    }

    fun subscribe(key: K): Flow<R?> = subscribe(storageEntryMetadata.storageKey(runtime, key))
}
