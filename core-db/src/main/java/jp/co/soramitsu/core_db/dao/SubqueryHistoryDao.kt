package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.SubqueryHistoryModel
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SubqueryHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(transaction: SubqueryHistoryModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(transactions: List<SubqueryHistoryModel>)

    @Query("SELECT * FROM subqueryentity WHERE address = :accountAddress ORDER BY (case when status = :statusUp then 0 else 1 end), time DESC")
    abstract fun observe(accountAddress: String,
                         statusUp: SubqueryHistoryModel.Status = SubqueryHistoryModel.Status.PENDING
    ): Flow<List<SubqueryHistoryModel>>

    @Query("SELECT * FROM subqueryentity WHERE hash = :hash")
    abstract suspend fun getTransaction(hash: String): SubqueryHistoryModel?

    @Query(
        """
        SELECT DISTINCT receiver FROM subqueryentity WHERE (receiver LIKE '%' || :query  || '%' AND receiver != address) AND address = :accountAddress
        UNION
        SELECT DISTINCT sender FROM subqueryentity WHERE (sender LIKE '%' || :query  || '%' AND SENDER != address) AND address = :accountAddress
    """
    )
    abstract suspend fun getContacts(query: String, accountAddress: String): List<String>

    suspend fun insertFromSubquery(accountAddress: String, transactions: List<SubqueryHistoryModel>) {
        clear(accountAddress)

        val oldest = transactions.minByOrNull(SubqueryHistoryModel::time)
        oldest?.let {
            clearOld(accountAddress, oldest.time)
        }

        insertAll(transactions)
    }

    @Query("DELETE FROM subqueryentity WHERE address = :accountAddress")
    protected abstract suspend fun clear(accountAddress: String): Int

    @Query("DELETE FROM subqueryentity WHERE time < :minTime AND address = :accountAddress")
    protected abstract suspend fun clearOld(accountAddress: String, minTime: Long): Int
}
