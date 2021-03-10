package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import kotlinx.coroutines.flow.Flow

private const val SELECT_QUERY = "SELECT * FROM account_staking_accesses WHERE address = :address"

@Dao
abstract class AccountStakingDao {

    @Query(SELECT_QUERY)
    abstract suspend fun get(address: String) : AccountStakingLocal

    @Query(SELECT_QUERY)
    abstract fun observe(address: String) : Flow<AccountStakingLocal>

    @Insert
    abstract suspend fun insert(accountStaking: AccountStakingLocal)
}