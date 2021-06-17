package jp.co.soramitsu.runtime.storage.source

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalStorageSource(
    runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val storageCache: StorageCache,
) : BaseStorageSource(runtimeProperty) {

    override suspend fun query(key: String): String? {
        return storageCache.getEntry(key).content
    }

    override suspend fun queryKeys(keys: List<String>): Map<String, String?> {
        return storageCache.getEntries(keys).associateBy(
            keySelector = StorageEntry::storageKey,
            valueTransform = StorageEntry::content
        )
    }

    override suspend fun observe(key: String, networkType: Node.NetworkType): Flow<String?> {
        return storageCache.observeEntry(key, networkType)
            .map { it.content }
    }

    override suspend fun queryByPrefix(prefix: String): Map<String, String?> {
        return storageCache.getEntries(prefix).associateBy(
            keySelector = StorageEntry::storageKey,
            valueTransform = StorageEntry::content
        )
    }

    override suspend fun queryChildState(storageKey: String, childKey: String): String? {
        throw NotImplementedError("Child state queries are not yet supported in local storage")
    }
}
