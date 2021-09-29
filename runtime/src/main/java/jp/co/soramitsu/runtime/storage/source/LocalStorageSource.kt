package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalStorageSource(
    chainRegistry: ChainRegistry,
    private val storageCache: StorageCache,
) : BaseStorageSource(chainRegistry) {

    override suspend fun query(key: String, chainId: String, at: BlockHash?): String? {
        requireWithoutAt(at)

        return storageCache.getEntry(key, chainId).content
    }

    override suspend fun queryKeys(keys: List<String>, chainId: String, at: BlockHash?): Map<String, String?> {
        requireWithoutAt(at)

        return storageCache.getEntries(keys, chainId).associateBy(
            keySelector = StorageEntry::storageKey,
            valueTransform = StorageEntry::content
        )
    }

    override suspend fun observe(key: String, chainId: String): Flow<String?> {
        return storageCache.observeEntry(key, chainId)
            .map { it.content }
    }

    override suspend fun queryByPrefix(prefix: String, chainId: String): Map<String, String?> {
        return storageCache.getEntries(prefix, chainId).associateBy(
            keySelector = StorageEntry::storageKey,
            valueTransform = StorageEntry::content
        )
    }

    override suspend fun queryChildState(storageKey: String, childKey: String, chainId: String): String? {
        throw NotImplementedError("Child state queries are not yet supported in local storage")
    }

    private fun requireWithoutAt(at: BlockHash?) = require(at == null) {
        "`At` parameter is not supported in local storage"
    }
}
