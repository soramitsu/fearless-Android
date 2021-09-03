package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import jp.co.soramitsu.core_db.model.chain.ChainAccountLocal
import jp.co.soramitsu.core_db.model.chain.JoinedMetaAccountInfo
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal

@Dao
interface MetaAccountDao {

    @Insert
    suspend fun insertMetaAccount(metaAccount: MetaAccountLocal): Long

    @Insert
    suspend fun insertChainAccount(chainAccount: ChainAccountLocal)

    @Query("SELECT * FROM meta_accounts")
    fun getMetaAccounts(): List<MetaAccountLocal>

    @Query("SELECT * FROM meta_accounts WHERE id = :metaId")
    suspend fun getJoinedMetaAccountInfo(metaId: Long): JoinedMetaAccountInfo
}
