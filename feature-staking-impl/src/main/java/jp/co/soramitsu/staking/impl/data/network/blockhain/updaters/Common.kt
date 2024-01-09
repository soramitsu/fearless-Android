package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.shared_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindActiveEra
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

fun RuntimeMetadata.activeEraStorageKeyOrNull() = moduleOrNull(Modules.STAKING)?.storage("ActiveEra")?.storageKey()
fun RuntimeMetadata.stakingRoundKey() = parachainStaking().storage("Round").storageKey()

suspend fun StorageCache.observeActiveEraIndex(runtime: RuntimeSnapshot, chainId: String): Flow<BigInteger> {
    return observeEntry(runtime.metadata.activeEraStorageKeyOrNull(), chainId)
        .map { bindActiveEra(it.content!!, runtime) }
}

suspend fun BulkRetriever.fetchValuesToCache(
    socketService: SocketService,
    keys: List<String>,
    storageCache: StorageCache,
    chainId: String
) {
    val allValues = queryKeys(socketService, keys)

    val toInsert = allValues.map { (key, value) -> StorageEntry(key, value) }

    storageCache.insert(toInsert, chainId)
}

suspend fun BulkRetriever.fetchPrefixValuesToCache(
    socketService: SocketService,
    prefix: String,
    storageCache: StorageCache,
    chainId: String
) {
    val allKeys = retrieveAllKeys(socketService, prefix)

    fetchValuesToCache(socketService, allKeys, storageCache, chainId)
}
