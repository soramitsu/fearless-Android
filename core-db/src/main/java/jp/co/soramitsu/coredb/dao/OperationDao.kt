package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import jp.co.soramitsu.coredb.model.OperationLocal
import kotlinx.coroutines.flow.Flow

private const val ID_FILTER = "address = :address AND chainId = :chainId AND chainAssetId = :chainAssetId"

@Dao
abstract class OperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(operation: OperationLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(operations: List<OperationLocal>)

    @Query(
        """
        SELECT * FROM operations WHERE $ID_FILTER
        ORDER BY (case when status = :statusUp then 0 else 1 end), time DESC
        """
    )
    abstract fun observe(
        address: String,
        chainId: String,
        chainAssetId: String,
        statusUp: OperationLocal.Status = OperationLocal.Status.PENDING
    ): Flow<List<OperationLocal>>

    @Query("SELECT * FROM operations WHERE hash = :hash")
    abstract suspend fun getOperation(hash: String): OperationLocal?

    @Query("SELECT * FROM operations")
    abstract suspend fun getOperations(): List<OperationLocal>

    @Query("SELECT * FROM operations ORDER BY time DESC")
    abstract fun observeOperations(): Flow<List<OperationLocal>>

    @Transaction
    open suspend fun insertFromSubquery(
        accountAddress: String,
        chainId: String,
        chainAssetId: String,
        operations: List<OperationLocal>
    ) {
        clearBySource(accountAddress, chainId, chainAssetId, OperationLocal.Source.SUBQUERY)

        val operationsWithHashes = operations.mapNotNullTo(mutableSetOf(), OperationLocal::hash)

        if (operationsWithHashes.isNotEmpty()) {
            clearByHashes(accountAddress, chainId, chainAssetId, operationsWithHashes)
        }

        val oldest = operations.minByOrNull(OperationLocal::time)
        oldest?.let {
            clearOld(accountAddress, chainId, chainAssetId, oldest.time)
        }

        insertAll(operations)
    }

    @Query("DELETE FROM operations WHERE $ID_FILTER AND source = :source")
    protected abstract suspend fun clearBySource(
        address: String,
        chainId: String,
        chainAssetId: String,
        source: OperationLocal.Source
    ): Int

    @Query("DELETE FROM operations WHERE time < :minTime AND $ID_FILTER")
    protected abstract suspend fun clearOld(
        address: String,
        chainId: String,
        chainAssetId: String,
        minTime: Long
    ): Int

    @Query("DELETE FROM operations WHERE $ID_FILTER AND hash in (:hashes)")
    protected abstract suspend fun clearByHashes(
        address: String,
        chainId: String,
        chainAssetId: String,
        hashes: Set<String>
    ): Int
}
