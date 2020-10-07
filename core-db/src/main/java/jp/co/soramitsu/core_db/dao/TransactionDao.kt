package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.feature_account_api.domain.model.Node

@Dao
abstract class TransactionDao {

    @Query("SELECT * FROM transactions WHERE accountAddress = :accountAddress")
    abstract fun observeTransactions(accountAddress: String): Observable<List<TransactionLocal>>

    @Query(
        """
            SELECT DISTINCT recipientAddress FROM transactions WHERE (recipientAddress LIKE '%' || :query  || '%' AND recipientAddress != accountAddress) AND networkType = :networkType
            UNION
            SELECT DISTINCT senderAddress FROM transactions WHERE (senderAddress LIKE '%' || :query  || '%' AND senderAddress != accountAddress) AND networkType = :networkType
        """
    )
    abstract fun getContacts(query: String, networkType: Node.NetworkType): Single<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insert(transactions: List<TransactionLocal>)

    @Query("DELETE FROM transactions WHERE accountAddress = :accountAddress")
    protected abstract fun clear(accountAddress: String)

    @Transaction
    open fun clearAndInsert(accountAddress: String, transactions: List<TransactionLocal>) {
        clear(accountAddress)
        insert(transactions)
    }
}