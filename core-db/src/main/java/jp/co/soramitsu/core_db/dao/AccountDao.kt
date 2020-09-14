package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.AccountWithNode

@Dao
abstract class AccountDao {

    @Query("select * from users, nodes where link = nodeLink")
    abstract fun getAccountsWithNodes(): Single<List<AccountWithNode>>

    @Query("select * from users, nodes where address = :address")
    abstract fun getAccount(address: String): Single<AccountWithNode>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insert(account: AccountLocal): Long

    @Query("DELETE FROM users where address = :address")
    abstract fun remove(address: String)
}