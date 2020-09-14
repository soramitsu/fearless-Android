package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.AccountLocal

@Dao
abstract class AccountDao {

    @Query("select * from users")
    abstract fun getAccounts(): Single<List<AccountLocal>>

    @Query("select * from users where address = :address")
    abstract fun getAccounts(address: String): Single<AccountLocal>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insert(account: AccountLocal): Long

    @Query("DELETE FROM users where address = :address")
    abstract fun remove(address: String)
}