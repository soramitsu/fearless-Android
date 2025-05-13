package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountPositionUpdate
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.shared_utils.runtime.AccountId
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

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateMetaAccount(metaAccount: MetaAccountLocal)

    @Query("SELECT * FROM chain_accounts WHERE initialized = 0")
    fun observeNotInitializedChainAccounts(): Flow<List<ChainAccountLocal>>

    @Query("UPDATE chain_accounts SET initialized = 1 WHERE metaId = :metaId AND chainId = :chainId")
    suspend fun markChainAccountInitialized(metaId: Long, chainId: String) :Int

    @Query("SELECT * FROM meta_accounts")
    fun getMetaAccounts(): List<MetaAccountLocal>

    @Query("SELECT * FROM meta_accounts WHERE id = :metaId")
    suspend fun getMetaAccount(metaId: Long): MetaAccountLocal?

    @Query("SELECT * FROM meta_accounts")
    @Transaction
    fun getJoinedMetaAccountsInfo(): List<RelationJoinedMetaAccountInfo>

    @Query("SELECT * FROM meta_accounts")
    @Transaction
    fun observeJoinedMetaAccountsInfo(): Flow<List<RelationJoinedMetaAccountInfo>>

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

    @Query("SELECT * FROM meta_accounts WHERE isSelected = 1")
    @Transaction
    suspend fun selectedMetaAccountInfo(): RelationJoinedMetaAccountInfo

    @Query("SELECT * FROM meta_accounts WHERE isSelected = 1")
    fun selectedLocalMetaAccountFlow(): Flow<MetaAccountLocal?>

    @Query("SELECT * FROM meta_accounts WHERE isSelected = 1")
    suspend fun getSelectedLocalMetaAccount(): MetaAccountLocal

    @Query("SELECT * FROM meta_accounts WHERE id = :metaId")
    suspend fun getLocalMetaAccount(metaId: Long): MetaAccountLocal

    @Query("SELECT * FROM meta_accounts WHERE id = :metaId")
    fun observeLocalMetaAccount(metaId: Long): Flow<MetaAccountLocal?>

    @Query("SELECT EXISTS ($FIND_BY_ADDRESS_QUERY)")
    fun isMetaAccountExists(accountId: AccountId): Boolean

    @Query(FIND_BY_ADDRESS_QUERY)
    @Transaction
    fun getMetaAccountInfo(accountId: AccountId): RelationJoinedMetaAccountInfo?

    @Query("UPDATE meta_accounts SET name = :newName WHERE id = :metaId")
    suspend fun updateName(metaId: Long, newName: String)

    @Query("UPDATE meta_accounts SET googleBackupAddress = NULL WHERE id = :metaId")
    suspend fun clearGoogleBackupInfo(metaId: Long)

    @Query("UPDATE meta_accounts SET isBackedUp = :isBackedUp WHERE id = :metaId")
    suspend fun updateBackedUp(metaId: Long, isBackedUp: Int)

    suspend fun updateBackedUp(metaId: Long, isBackedUp: Boolean = true) {
        updateBackedUp(
            metaId = metaId,
            isBackedUp = if (isBackedUp) 1 else 0
        )
    }

    @Query("DELETE FROM meta_accounts WHERE id = :metaId")
    suspend fun delete(metaId: Long)

    @Query("DELETE FROM chain_accounts WHERE metaId = :metaId")
    suspend fun deleteChainAccounts(metaId: Long)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 from meta_accounts")
    suspend fun getNextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceFavoriteChain(favoriteChainLocal: FavoriteChainLocal)

    @Query("SELECT * FROM favorite_chains WHERE metaId = :metaId")
    fun observeFavoriteChains(metaId: Long): Flow<List<FavoriteChainLocal>>

    @Query("UPDATE meta_accounts SET initialized = 1 WHERE id in (:ids)")
    suspend fun markAccountsInitialized(ids: List<Long>) :Int

    @Query("SELECT * FROM meta_accounts WHERE initialized = 0")
    @Transaction
    fun observeNotInitializedMetaAccounts(): Flow<List<RelationJoinedMetaAccountInfo>>
}
