package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.StorageEntryLocal
import kotlinx.coroutines.flow.Flow

private const val SELECT_FULL_KEY_QUERY = "SELECT * from storage WHERE chainId = :chainId AND storageKey = :fullKey"
private const val SELECT_PREFIX_KEY_QUERY = "SELECT * from storage WHERE chainId = :chainId  AND storageKey LIKE :keyPrefix || '%'"

@Dao
abstract class StorageDao {

    @Query("SELECT EXISTS($SELECT_PREFIX_KEY_QUERY)")
    abstract suspend fun isPrefixInCache(chainId: String, keyPrefix: String): Boolean

    @Query("SELECT EXISTS($SELECT_FULL_KEY_QUERY)")
    abstract suspend fun isFullKeyInCache(chainId: String, fullKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entry: StorageEntryLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entries: List<StorageEntryLocal>)

    @Query(SELECT_FULL_KEY_QUERY)
    abstract fun observeEntry(chainId: String, fullKey: String): Flow<StorageEntryLocal?>

    @Query(SELECT_PREFIX_KEY_QUERY)
    abstract fun observeEntries(chainId: String, keyPrefix: String): Flow<List<StorageEntryLocal>>

    @Query("SELECT * from storage WHERE chainId = :chainId AND storageKey in (:fullKeys)")
    abstract fun observeEntries(chainId: String, fullKeys: List<String>): Flow<List<StorageEntryLocal>>

    @Query("SELECT storageKey from storage WHERE chainId = :chainId AND storageKey in (:keys)")
    abstract suspend fun filterKeysInCache(chainId: String, keys: List<String>): List<String>
}
