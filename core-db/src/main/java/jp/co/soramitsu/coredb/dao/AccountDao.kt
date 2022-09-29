package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import jp.co.soramitsu.coredb.model.AccountLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AccountDao {

    @Query("select * from users order by position")
    abstract fun accountsFlow(): Flow<List<AccountLocal>>

    @Query("select * from users order by position")
    abstract suspend fun getAccounts(): List<AccountLocal>

    @Query("select * from users where address = :address")
    abstract suspend fun getAccount(address: String): AccountLocal?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(account: AccountLocal): Long

    @Query("DELETE FROM users where address = :address")
    abstract suspend fun remove(address: String)

    @Update
    abstract suspend fun updateAccount(account: AccountLocal)

    @Update
    abstract suspend fun updateAccounts(accounts: List<AccountLocal>)

    @Query("SELECT COALESCE(MAX(position), 0)  + 1 from users")
    abstract suspend fun getNextPosition(): Int

    @Query("SELECT EXISTS(SELECT * FROM users WHERE address = :accountAddress)")
    abstract suspend fun accountExists(accountAddress: String): Boolean
}
