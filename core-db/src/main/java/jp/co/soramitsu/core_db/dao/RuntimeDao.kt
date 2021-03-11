package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.RuntimeCacheEntry

@Dao
abstract class RuntimeDao {

    @Query("SELECT * FROM runtimeCache WHERE networkName = :networkName")
    abstract suspend fun getCacheEntry(networkName: String): RuntimeCacheEntry

    @Query("UPDATE runtimeCache SET latestKnownVersion = :latestKnownVersion WHERE networkName = :networkName")
    abstract suspend fun updateLatestKnownVersion(networkName: String, latestKnownVersion: Int)

    @Query("UPDATE runtimeCache SET latestAppliedVersion = :latestAppliedVersion WHERE networkName = :networkName")
    abstract suspend fun updateLatestAppliedVersion(networkName: String, latestAppliedVersion: Int)

    @Query("UPDATE runtimeCache SET typesVersion = :typesVersion WHERE networkName = :networkName")
    abstract suspend fun updateTypesVersion(networkName: String, typesVersion: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertOrSkipCacheEntry(cacheEntry: RuntimeCacheEntry)
}
