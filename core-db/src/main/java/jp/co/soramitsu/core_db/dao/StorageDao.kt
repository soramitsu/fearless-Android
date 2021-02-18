package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.model.StorageEntryLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StorageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entry: StorageEntryLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entries: List<StorageEntryLocal>)

    @Query("SELECT * from storage WHERE networkType = :networkType AND storageKey = :key")
    abstract fun observeEntry(networkType: Node.NetworkType, key: String): Flow<StorageEntryLocal?>

    @Query("SELECT * from storage WHERE networkType = :networkType AND storageKey LIKE :keyPrefix || '%'")
    abstract fun observeEntries(networkType: Node.NetworkType, keyPrefix: String): Flow<List<StorageEntryLocal>>
}