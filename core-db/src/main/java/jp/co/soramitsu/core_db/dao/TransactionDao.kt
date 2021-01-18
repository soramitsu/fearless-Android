package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction.Status
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionDao {

    @Query("SELECT * FROM transactions WHERE accountAddress = :accountAddress ORDER BY (case when status = :statusUp then 0 else 1 end), date DESC")
    abstract fun observeTransactions(
        accountAddress: String,
        statusUp: Status = Status.PENDING
    ): Flow<List<TransactionLocal>>

    @Query("SELECT * FROM transactions WHERE accountAddress = :accountAddress ORDER BY date DESC")
    abstract suspend fun getTransactions(accountAddress: String): List<TransactionLocal>

    @Query("SELECT * FROM transactions WHERE hash = :hash")
    abstract suspend fun getTransaction(hash: String): TransactionLocal?

    @Query(
        """
            SELECT DISTINCT recipientAddress FROM transactions WHERE (recipientAddress LIKE '%' || :query  || '%' AND recipientAddress != accountAddress) AND accountAddress = :accountAddress
            UNION
            SELECT DISTINCT senderAddress FROM transactions WHERE (senderAddress LIKE '%' || :query  || '%' AND senderAddress != accountAddress) AND accountAddress = :accountAddress
        """
    )
    abstract suspend fun getContacts(query: String, accountAddress: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(transaction: TransactionLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(transaction: List<TransactionLocal>)

    @Transaction
    open suspend fun insertFromSubScan(accountAddress: String, transactions: List<TransactionLocal>) {
        clear(accountAddress, TransactionSource.SUBSCAN)

        val oldest = transactions.minByOrNull(TransactionLocal::date)

        oldest?.let {
            clearOld(accountAddress, oldest.date)
        }

        insert(transactions)
    }

    @Query("DELETE FROM transactions WHERE accountAddress = :accountAddress AND source = :source")
    protected abstract suspend fun clear(accountAddress: String, source: TransactionSource): Int

    @Query("DELETE FROM transactions WHERE date < :minDate AND accountAddress = :accountAddress")
    protected abstract suspend fun clearOld(accountAddress: String, minDate: Long): Int
}