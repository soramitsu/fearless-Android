package jp.co.soramitsu.runtime.storage

import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.core_db.model.StorageEntryLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DbStorageCache(
    private val storageDao: StorageDao
) : StorageCache {

    override suspend fun isPrefixInCache(prefixKey: String, chainId: String): Boolean {
        return storageDao.isPrefixInCache(chainId, prefixKey)
    }

    override suspend fun isFullKeyInCache(fullKey: String, chainId: String): Boolean {
        return storageDao.isFullKeyInCache(chainId, fullKey)
    }

    override suspend fun insert(entry: StorageEntry, chainId: String) = withContext(Dispatchers.IO) {
        storageDao.insert(mapStorageEntryToLocal(entry, chainId))
    }

    override suspend fun insert(entries: List<StorageEntry>, chainId: String) = withContext(Dispatchers.IO) {
        val mapped = entries.map { mapStorageEntryToLocal(it, chainId) }

        storageDao.insert(mapped)
    }

    override suspend fun observeEntry(key: String, chainId: String): Flow<StorageEntry> {
        return storageDao.observeEntry(chainId, key)
            .filterNotNull()
            .map { mapStorageEntryFromLocal(it) }
            .distinctUntilChangedBy(StorageEntry::content)
    }

    override suspend fun observeEntries(keyPrefix: String, chainId: String): Flow<List<StorageEntry>> {
        return storageDao.observeEntries(chainId, keyPrefix)
            .mapList { mapStorageEntryFromLocal(it) }
            .filter { it.isNotEmpty() }
    }

    override suspend fun getEntry(key: String, chainId: String): StorageEntry = observeEntry(key, chainId).first()

    override suspend fun filterKeysInCache(keys: List<String>, chainId: String): List<String> {
        return storageDao.filterKeysInCache(chainId, keys)
    }

    override suspend fun getEntries(keyPrefix: String, chainId: String): List<StorageEntry> {
        return observeEntries(keyPrefix, chainId).first()
    }

    override suspend fun getEntries(fullKeys: List<String>, chainId: String): List<StorageEntry> {
        return storageDao.observeEntries(chainId, fullKeys)
            .filter { it.size == fullKeys.size }
            .mapList { mapStorageEntryFromLocal(it) }
            .first()
    }


}

private fun mapStorageEntryToLocal(
    storageEntry: StorageEntry,
    chainId: String
) = with(storageEntry) {
    StorageEntryLocal(
        storageKey = storageKey,
        content = content,
        chainId = chainId
    )
}

private fun mapStorageEntryFromLocal(
    storageEntryLocal: StorageEntryLocal
) = with(storageEntryLocal) {
    StorageEntry(
        storageKey = storageKey,
        content = content
    )
}
