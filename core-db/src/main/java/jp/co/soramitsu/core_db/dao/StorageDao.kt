package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.model.StorageEntryLocal
import kotlinx.coroutines.flow.Flow

private const val SELECT_FULL_KEY_QUERY = "SELECT * from storage WHERE networkType = :networkType AND storageKey = :fullKey"
private const val SELECT_PREFIX_KEY_QUERY = "SELECT * from storage WHERE networkType = :networkType  AND storageKey LIKE :keyPrefix || '%'"

@Dao
abstract class StorageDao {

    @Query("SELECT EXISTS($SELECT_PREFIX_KEY_QUERY)")
    abstract suspend fun isPrefixInCache(networkType: Node.NetworkType, keyPrefix: String): Boolean

    @Query("SELECT EXISTS($SELECT_FULL_KEY_QUERY)")
    abstract suspend fun isFullKeyInCache(networkType: Node.NetworkType, fullKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entry: StorageEntryLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entries: List<StorageEntryLocal>)

    @Query(SELECT_FULL_KEY_QUERY)
    abstract fun observeEntry(networkType: Node.NetworkType, fullKey: String): Flow<StorageEntryLocal?>

    @Query(SELECT_PREFIX_KEY_QUERY)
    abstract fun observeEntries(networkType: Node.NetworkType, keyPrefix: String): Flow<List<StorageEntryLocal>>
}
