package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindActiveEra
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

fun RuntimeMetadata.activeEraStorageKey() = staking().storage("ActiveEra").storageKey()

suspend fun StorageCache.observeActiveEraIndex(runtime: RuntimeSnapshot, networkType: Node.NetworkType): Flow<BigInteger> {
    return observeEntry(runtime.metadata.activeEraStorageKey(), networkType)
        .map { bindActiveEra(it.content!!, runtime) }
}

suspend fun BulkRetriever.fetchValuesToCache(keys: List<String>, storageCache: StorageCache) {
    val allValues = queryKeys(keys)

    val runtimeVersion = storageCache.currentRuntimeVersion()

    val toInsert = allValues.map { (key, value) -> StorageEntry(key, value, runtimeVersion) }

    storageCache.insert(toInsert)
}

suspend fun BulkRetriever.fetchPrefixValuesToCache(prefix: String, storageCache: StorageCache) {
    val allKeys = retrieveAllKeys(prefix)

    fetchValuesToCache(allKeys, storageCache)
}
