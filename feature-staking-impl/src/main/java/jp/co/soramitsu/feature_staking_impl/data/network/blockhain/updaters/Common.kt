package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.rpc.retrieveAllValues
import jp.co.soramitsu.common.data.network.runtime.binding.bindActiveEraIndex
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

fun RuntimeMetadata.activeEraStorageKey() = module("Staking").storage("ActiveEra").storageKey()

suspend fun StorageCache.observeEraIndex(runtime: RuntimeSnapshot): Flow<BigInteger> {
    return observeEntry(runtime.metadata.activeEraStorageKey())
        .map { bindActiveEraIndex(it.content!!, runtime) }
}

suspend fun BulkRetriever.fetchPrefixValuesToCache(prefix: String, storageCache: StorageCache) {
    val allValues = retrieveAllValues(prefix)

    val runtimeVersion = storageCache.currentRuntimeVersion()

    val toInsert = allValues.map { (key, value) -> StorageEntry(key, value, runtimeVersion) }

    storageCache.insert(toInsert)
}