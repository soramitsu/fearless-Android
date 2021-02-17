package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.StorageEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Dao
abstract class StorageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entry: StorageEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entries: List<StorageEntry>)

    @Query("SELECT * from storage WHERE storageKey = :key")
    abstract fun observeEntry(key: String): Flow<StorageEntry?>

    @Query("SELECT * from storage WHERE storageKey LIKE :keyPrefix || '%'")
    abstract fun observeEntries(keyPrefix: String): Flow<List<StorageEntry>>

    suspend fun waitForEntry(key: String) = observeEntry(key)
        .filterNotNull()
        .first()

    suspend fun waitForEntries(keyPrefix: String) = observeEntries(keyPrefix)
        .filter { it.isNotEmpty() }
        .first()
}