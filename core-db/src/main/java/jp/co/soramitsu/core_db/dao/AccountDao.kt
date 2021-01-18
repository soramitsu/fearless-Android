package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.feature_account_api.domain.model.Node
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AccountDao {

    @Query("select * from users order by networkType, position")
    abstract fun accountsFlow(): Flow<List<AccountLocal>>

    @Query("select * from users order by networkType, position")
    abstract suspend fun getAccounts(): List<AccountLocal>

    @Query("select * from users where address = :address")
    abstract suspend fun getAccount(address: String): AccountLocal

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

    @Query("select * from users where networkType = :networkType")
    abstract suspend fun getAccountsByNetworkType(networkType: Int): List<AccountLocal>

    @Query("select address from users where (address LIKE '%' || :query  || '%') AND networkType = :networkType")
    abstract suspend fun getAddresses(query: String, networkType: Node.NetworkType): List<String>
}