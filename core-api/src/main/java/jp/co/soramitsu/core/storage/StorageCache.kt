package jp.co.soramitsu.core.storage

import jp.co.soramitsu.core.model.StorageEntry
import kotlinx.coroutines.flow.Flow

interface StorageCache {

    suspend fun isPrefixInCache(prefixKey: String, chainId: String): Boolean

    suspend fun isFullKeyInCache(fullKey: String, chainId: String): Boolean

    suspend fun insert(entry: StorageEntry, chainId: String)

    suspend fun insert(entries: List<StorageEntry>, chainId: String)

    suspend fun observeEntry(key: String, chainId: String): Flow<StorageEntry>

    /**
     * Should be not empty
     */
    suspend fun observeEntries(keyPrefix: String, chainId: String): Flow<List<StorageEntry>>

    /**
     * Should suspend until any matched result found
     */
    suspend fun getEntry(key: String, chainId: String): StorageEntry

    suspend fun filterKeysInCache(keys: List<String>, chainId: String): List<String>

    /**
     * Should suspend until any matched result found
     * Thus, will not be empty
     */
    suspend fun getEntries(keyPrefix: String, chainId: String): List<StorageEntry>

    /**
     * Should suspend until all keys will be found
     * Thus, result.size == fullKeys.size
     */
    suspend fun getEntries(fullKeys: List<String>, chainId: String): List<StorageEntry>
}
