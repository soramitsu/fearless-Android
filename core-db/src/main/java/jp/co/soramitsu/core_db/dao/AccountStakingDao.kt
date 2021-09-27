package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

private const val SELECT_QUERY = """
    SELECT * FROM account_staking_accesses
    WHERE accountId = :accountId AND chainId = :chainId AND chainAssetId = :chainAssetId
    """

@Dao
abstract class AccountStakingDao {

    @Query(SELECT_QUERY)
    abstract suspend fun get(chainId: String, chainAssetId: Int, accountId: ByteArray): AccountStakingLocal

    @Query(SELECT_QUERY)
    protected abstract fun observeInternal(chainId: String, chainAssetId: Int, accountId: ByteArray): Flow<AccountStakingLocal>

    fun observeDistinct(chainId: String, chainAssetId: Int, accountId: ByteArray): Flow<AccountStakingLocal> {
        return observeInternal(chainId, chainAssetId, accountId)
            .filterNotNull()
            .distinctUntilChanged()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(accountStaking: AccountStakingLocal)
}
