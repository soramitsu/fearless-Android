package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import jp.co.soramitsu.coredb.model.chain.ChainAccountLocal
import jp.co.soramitsu.coredb.model.chain.MetaAccountLocal
import jp.co.soramitsu.coredb.model.chain.MetaAccountPositionUpdate
import jp.co.soramitsu.coredb.model.chain.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.flow.Flow

/**
 * Fetch meta account where
 * accountId = meta.substrateAccountId
 * or hex(accountId) = meta.ethereumAddress
 * or there is a child chain account which have child.accountId = accountId
 */
private const val FIND_BY_ADDRESS_QUERY = """
        SELECT * FROM meta_accounts 
        WHERE substrateAccountId = :accountId
        OR ethereumAddress = :accountId
        OR  id = (
            SELECT id FROM meta_accounts AS m
                INNER JOIN chain_accounts as c ON m.id = c.metaId
                WHERE  c.accountId = :accountId
            )
    """

@Dao
interface MetaAccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMetaAccount(metaAccount: MetaAccountLocal): Long

    @Insert
    suspend fun insertChainAccount(chainAccount: ChainAccountLocal)

    @Query("SELECT * FROM meta_accounts")
    fun getMetaAccounts(): List<MetaAccountLocal>

    @Query("SELECT * FROM meta_accounts")
    @Transaction
    fun getJoinedMetaAccountsInfo(): List<RelationJoinedMetaAccountInfo>

    @Query("SELECT * FROM meta_accounts ORDER BY position")
    fun metaAccountsFlow(): Flow<List<MetaAccountLocal>>

    @Query("UPDATE meta_accounts SET isSelected = (id = :metaId)")
    suspend fun selectMetaAccount(metaId: Long)

    @Update(entity = MetaAccountLocal::class)
    suspend fun updatePositions(updates: List<MetaAccountPositionUpdate>)

    @Query("SELECT * FROM meta_accounts WHERE id = :metaId")
    @Transaction
    suspend fun getJoinedMetaAccountInfo(metaId: Long): RelationJoinedMetaAccountInfo

    @Query("SELECT * FROM meta_accounts WHERE isSelected = 1")
    @Transaction
    fun selectedMetaAccountInfoFlow(): Flow<RelationJoinedMetaAccountInfo?>

    @Query("SELECT EXISTS ($FIND_BY_ADDRESS_QUERY)")
    fun isMetaAccountExists(accountId: AccountId): Boolean

    @Query(FIND_BY_ADDRESS_QUERY)
    @Transaction
    fun getMetaAccountInfo(accountId: AccountId): RelationJoinedMetaAccountInfo?

    @Query("UPDATE meta_accounts SET name = :newName WHERE id = :metaId")
    suspend fun updateName(metaId: Long, newName: String)

    @Query("DELETE FROM meta_accounts WHERE id = :metaId")
    suspend fun delete(metaId: Long)

    @Query("DELETE FROM chain_accounts WHERE metaId = :metaId")
    suspend fun deleteChainAccounts(metaId: Long)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 from meta_accounts")
    suspend fun getNextPosition(): Int
}
