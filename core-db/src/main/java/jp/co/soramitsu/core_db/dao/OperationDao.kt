package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import jp.co.soramitsu.core_db.model.OperationLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(operation: OperationLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(operations: List<OperationLocal>)

    @Query("SELECT * FROM operations WHERE address = :accountAddress ORDER BY (case when status = :statusUp then 0 else 1 end), time DESC")
    abstract fun observe(
        accountAddress: String,
        statusUp: OperationLocal.Status = OperationLocal.Status.PENDING
    ): Flow<List<OperationLocal>>

    @Query("SELECT * FROM operations WHERE hash = :hash")
    abstract suspend fun getOperation(hash: String): OperationLocal?

    @Query("SELECT * FROM operations WHERE address = :accountAddress ORDER BY time DESC")
    abstract suspend fun getOperations(accountAddress: String): List<OperationLocal>

    @Query(
        """
        SELECT DISTINCT receiver FROM operations WHERE (receiver LIKE '%' || :query  || '%' AND receiver != address) AND address = :accountAddress
        UNION
        SELECT DISTINCT sender FROM operations WHERE (sender LIKE '%' || :query  || '%' AND SENDER != address) AND address = :accountAddress
    """
    )
    abstract suspend fun getContacts(query: String, accountAddress: String): List<String>

    @Transaction
    open suspend fun insertFromSubquery(accountAddress: String, operations: List<OperationLocal>) {
        clearBySource(accountAddress, OperationLocal.Source.SUBQUERY)

        val operationsWithHashes = operations.mapNotNullTo(mutableSetOf(), OperationLocal::hash)

        if (operationsWithHashes.isNotEmpty()) {
            val cleared = clearByHashes(accountAddress, operationsWithHashes)
        }

        val oldest = operations.minByOrNull(OperationLocal::time)
        oldest?.let {
            clearOld(accountAddress, oldest.time)
        }

        insertAll(operations)
    }

    @Query("DELETE FROM operations WHERE address = :accountAddress AND source = :source")
    protected abstract suspend fun clearBySource(accountAddress: String, source: OperationLocal.Source): Int

    @Query("DELETE FROM operations WHERE time < :minTime AND address = :accountAddress")
    protected abstract suspend fun clearOld(accountAddress: String, minTime: Long): Int

    @Query("DELETE FROM operations WHERE address = :accountAddress AND hash in (:hashes)")
    protected abstract suspend fun clearByHashes(accountAddress: String, hashes: Set<String>): Int
}
