package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import jp.co.soramitsu.core_db.model.chain.ChainAccountLocal
import jp.co.soramitsu.core_db.model.chain.EmbeddedJoinedMetaAccountInfo
import jp.co.soramitsu.core_db.model.chain.JoinedMetaAccountInfo
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal
import jp.co.soramitsu.core_db.model.chain.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.flow.Flow

private const val FIND_BY_ADDRESS_QUERY = """
        SELECT * FROM meta_accounts AS m INNER JOIN chain_accounts as c ON m.id = c.metaId
            WHERE m.ethereumAddress == :ethereumAddress OR m.substrateAccountId = :accountId OR c.accountId = :accountId
        """

@Dao
interface MetaAccountDao {

    @Insert
    suspend fun insertMetaAccount(metaAccount: MetaAccountLocal): Long

    @Insert
    suspend fun insertChainAccount(chainAccount: ChainAccountLocal)

    @Query("SELECT * FROM meta_accounts")
    fun getMetaAccounts(): List<MetaAccountLocal>

    @Query("SELECT * FROM meta_accounts")
    fun getJoinedMetaAccountsInfo(): List<JoinedMetaAccountInfo>

    @Query("SELECT * FROM meta_accounts WHERE id = :metaId")
    suspend fun getJoinedMetaAccountInfo(metaId: Long): RelationJoinedMetaAccountInfo

    @Query("SELECT * FROM meta_accounts WHERE isSelected = 1")
    fun selectedMetaAccountInfoFlow(): Flow<RelationJoinedMetaAccountInfo?>

    @Query(FIND_BY_ADDRESS_QUERY)
    fun getMetaAccountInfo(
        accountId: AccountId,
        ethereumAddress: String = accountId.toHexString()
    ): EmbeddedJoinedMetaAccountInfo?

    @Query("SELECT EXISTS ($FIND_BY_ADDRESS_QUERY)")
    fun isMetaAccountExists(
        accountId: AccountId,
        ethereumAddress: String = accountId.toHexString()
    ): Boolean
}
