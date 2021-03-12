package jp.co.soramitsu.runtime.storage

import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.core_db.model.StorageEntryLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.ext.runtimeCacheName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigInteger

class NetworkAwareStorageCache(
    private val storageDao: StorageDao,
    private val runtimeDao: RuntimeDao,
    private val accountRepository: AccountRepository
) : StorageCache {

    private suspend fun currentNetwork() = accountRepository.getSelectedNode().networkType

    override suspend fun isPrefixInCache(prefixKey: String): Boolean {
        return storageDao.isPrefixInCache(currentNetwork(), prefixKey)
    }

    override suspend fun isFullKeyInCache(fullKey: String): Boolean {
        return storageDao.isFullKeyInCache(currentNetwork(), fullKey)
    }

    override suspend fun insert(entry: StorageEntry) = withContext(Dispatchers.IO) {
        storageDao.insert(mapStorageEntryToLocal(entry))
    }

    override suspend fun insert(entries: List<StorageEntry>) = withContext(Dispatchers.IO) {
        val mapped = entries.map { mapStorageEntryToLocal(it) }

        storageDao.insert(mapped)
    }

    override suspend fun observeEntry(key: String): Flow<StorageEntry> {
        return storageDao.observeEntry(currentNetwork(), key)
            .filterNotNull()
            .map { mapStorageEntryFromLocal(it) }
            .distinctUntilChangedBy(StorageEntry::content)
    }

    override suspend fun observeEntries(keyPrefix: String): Flow<List<StorageEntry>> {
        return storageDao.observeEntries(currentNetwork(), keyPrefix)
            .mapList { mapStorageEntryFromLocal(it) }
            .filter { it.isNotEmpty() }
    }

    override suspend fun getEntry(key: String): StorageEntry = observeEntry(key).first()

    override suspend fun getEntries(keyPrefix: String): List<StorageEntry> {
        return observeEntries(keyPrefix).first()
    }

    override suspend fun currentRuntimeVersion(): BigInteger {
        val key = currentNetwork().runtimeCacheName()

        return runtimeDao.getCacheEntry(key).latestKnownVersion.toBigInteger()
    }

    private suspend fun mapStorageEntryToLocal(storageEntry: StorageEntry): StorageEntryLocal {
        return mapStorageEntryToLocal(storageEntry, currentNetwork())
    }
}

private fun mapStorageEntryToLocal(
    storageEntry: StorageEntry,
    networkType: Node.NetworkType
) = with(storageEntry) {
    StorageEntryLocal(
        storageKey = storageKey,
        networkType = networkType,
        content = content,
        runtimeVersion = runtimeVersion.toInt()
    )
}

private fun mapStorageEntryFromLocal(
    storageEntryLocal: StorageEntryLocal
) = with(storageEntryLocal) {
    StorageEntry(
        storageKey = storageKey,
        content = content,
        runtimeVersion = runtimeVersion.toBigInteger()
    )
}
