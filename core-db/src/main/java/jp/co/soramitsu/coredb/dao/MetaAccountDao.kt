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

    /** Returns whether a different meta account owns any supplied non-null identity. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM meta_accounts
            WHERE id != :metaId
              AND (
                  (:substrateAccountId IS NOT NULL AND substrateAccountId = :substrateAccountId)
                  OR (:ethereumAddress IS NOT NULL AND ethereumAddress = :ethereumAddress)
                  OR (:tonPublicKey IS NOT NULL AND tonPublicKey = :tonPublicKey)
              )
        )
        """
    )
    suspend fun hasIdentityConflict(
        metaId: Long,
        substrateAccountId: ByteArray?,
        ethereumAddress: ByteArray?,
        tonPublicKey: ByteArray?
    ): Boolean

    /** Returns whether the exact primary key is currently present. */
    @Query("SELECT EXISTS(SELECT 1 FROM meta_accounts WHERE id = :metaId)")
    suspend fun metaAccountExists(metaId: Long): Boolean

    /** Finds the first remaining account ordered by position, then primary key. */
    @Query(
        """
        SELECT id
        FROM meta_accounts
        WHERE id != :excludedMetaId
        ORDER BY position ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun getDeterministicSuccessorId(excludedMetaId: Long): Long?

    /** Lists a meta account's chain-specific account ids in stable chain order. */
    @Query(
        """
        SELECT accountId
        FROM chain_accounts
        WHERE metaId = :metaId
        ORDER BY chainId ASC
        """
    )
    suspend fun getChainAccountIds(metaId: Long): List<ByteArray>

    @Query("SELECT * FROM meta_accounts")
    @Transaction
    fun getJoinedMetaAccountsInfo(): List<RelationJoinedMetaAccountInfo>

    @Query("SELECT * FROM meta_accounts")
    @Transaction
    fun observeJoinedMetaAccountsInfo(): Flow<List<RelationJoinedMetaAccountInfo>>

    @Query("SELECT * FROM meta_accounts ORDER BY position")
    @Transaction
    fun observeOrderedJoinedMetaAccountsInfo(): Flow<List<RelationJoinedMetaAccountInfo>>

    @Query("SELECT * FROM meta_accounts WHERE id = :metaId")
    @Transaction
    fun observeJoinedMetaAccountInfo(metaId: Long): Flow<RelationJoinedMetaAccountInfo?>

    @Query("SELECT * FROM meta_accounts ORDER BY position")
    fun metaAccountsFlow(): Flow<List<MetaAccountLocal>>

    @Query(
        """
        UPDATE meta_accounts
        SET isSelected = (id = :metaId)
        WHERE EXISTS(
            SELECT 1
            FROM meta_accounts
            WHERE id = :metaId
        )
        """
    )
    suspend fun selectExistingMetaAccount(metaId: Long): Int

    /**
     * Selects an existing wallet without ever clearing the current selection
     * when a stale or attacker-controlled id is supplied.
     */
    @Transaction
    suspend fun selectMetaAccount(metaId: Long) {
        check(selectExistingMetaAccount(metaId) > 0) {
            "Cannot select a missing meta account"
        }
    }

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

    /** Deletes balance rows that are not linked to meta accounts by a foreign key. */
    @Query("DELETE FROM assets WHERE metaId = :metaId")
    suspend fun deleteMetaAccountAssets(metaId: Long)

    /** Returns whether any non-foreign-keyed balance row still belongs to this wallet. */
    @Query("SELECT EXISTS(SELECT 1 FROM assets WHERE metaId = :metaId)")
    suspend fun hasMetaAccountAssets(metaId: Long): Boolean

    @Query("DELETE FROM chain_accounts WHERE metaId = :metaId")
    suspend fun deleteChainAccounts(metaId: Long)

    /**
     * Deletes a meta account and its non-foreign-keyed assets as one Room transaction.
     *
     * If the target is selected, the remaining account with the lowest position
     * (then lowest id) becomes selected. Replaying an already-completed deletion is
     * a no-op and never changes the current selection.
     *
     * @return `true` only when an existing meta account was deleted.
     */
    @Transaction
    suspend fun deleteMetaAccountAndSelectSuccessor(metaId: Long): Boolean {
        val account = getMetaAccount(metaId) ?: return false
        val successorId = if (account.isSelected) {
            getDeterministicSuccessorId(excludedMetaId = metaId)
        } else {
            null
        }

        deleteMetaAccountAssets(metaId)
        delete(metaId)

        if (successorId != null) {
            selectMetaAccount(successorId)
        }

        return true
    }

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
