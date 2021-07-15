package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.SubqueryHistoryModel
import jp.co.soramitsu.core_db.model.TransactionLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SubqueryHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(transaction: SubqueryHistoryModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(transactions: List<SubqueryHistoryModel>)

    @Query("SELECT * FROM subqueryentity WHERE address = :accountAddress")
    abstract fun observe(accountAddress: String): Flow<List<SubqueryHistoryModel>>

    suspend fun insertFromSubquery(accountAddress: String, transactions: List<SubqueryHistoryModel>){
        clear(accountAddress)

        val oldest = transactions.minByOrNull(SubqueryHistoryModel::time)
        oldest?.let{
            clearOld(accountAddress, oldest.time)
        }

        insertAll(transactions)
    }

    @Query("DELETE FROM subqueryentity WHERE address = :accountAddress")
    protected abstract suspend fun clear(accountAddress: String): Int

    @Query("DELETE FROM subqueryentity WHERE time < :minTime AND address = :accountAddress")
    protected abstract suspend fun clearOld(accountAddress: String, minTime: Long): Int
}
