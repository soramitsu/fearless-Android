package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

private const val SELECT_QUERY = "SELECT * FROM account_staking_accesses WHERE accountId = :accountId AND chainId = :chainId"

@Dao
abstract class AccountStakingDao {

    @Query(SELECT_QUERY)
    abstract suspend fun get(chainId: String, accountId: ByteArray): AccountStakingLocal

    @Query(SELECT_QUERY)
    abstract fun observeInternal(chainId: String, accountId: ByteArray): Flow<AccountStakingLocal>

    fun observeDistinct(chainId: String, accountId: ByteArray) = observeInternal(chainId, accountId)
        .filterNotNull()
        .distinctUntilChanged()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(accountStaking: AccountStakingLocal)
}
