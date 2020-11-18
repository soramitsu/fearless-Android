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

@Dao
abstract class TransactionDao {

    @Query("SELECT * FROM transactions WHERE accountAddress = :accountAddress ORDER BY (case when status = :statusUp then 0 else 1 end), date DESC")
    abstract fun observeTransactions(
        accountAddress: String,
        statusUp: Status = Status.PENDING
    ): Observable<List<TransactionLocal>>

    @Query("SELECT * FROM transactions WHERE accountAddress = :accountAddress ORDER BY date DESC")
    abstract fun getTransactions(accountAddress: String): List<TransactionLocal>

    @Query("SELECT * FROM transactions WHERE hash = :hash")
    abstract fun getTransaction(hash: String): TransactionLocal?

    @Query(
        """
            SELECT DISTINCT recipientAddress FROM transactions WHERE (recipientAddress LIKE '%' || :query  || '%' AND recipientAddress != accountAddress) AND accountAddress = :accountAddress
            UNION
            SELECT DISTINCT senderAddress FROM transactions WHERE (senderAddress LIKE '%' || :query  || '%' AND senderAddress != accountAddress) AND accountAddress = :accountAddress
        """
    )
    abstract fun getContacts(query: String, accountAddress: String): Single<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transaction: TransactionLocal): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transaction: List<TransactionLocal>): Completable

    @Transaction
    open fun insertFromSubscan(accountAddress: String, transactions: List<TransactionLocal>) {
        clear(accountAddress, TransactionSource.SUBSCAN)

        if (transactions.isNotEmpty()) {
            val oldest = transactions.minBy(TransactionLocal::date)!!

            clearOld(accountAddress, oldest.date)
        }

        insertBlocking(transactions)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertBlocking(transactions: List<TransactionLocal>)

    @Query("DELETE FROM transactions WHERE accountAddress = :accountAddress AND source = :source")
    protected abstract fun clear(accountAddress: String, source: TransactionSource): Int

    @Query("DELETE FROM transactions WHERE date < :minDate AND accountAddress = :accountAddress")
    protected abstract fun clearOld(accountAddress: String, minDate: Long): Int
}