package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.coredb.model.OkxChainLocal
import jp.co.soramitsu.coredb.model.OkxTokenLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface OkxDao {

    @Query("SELECT * FROM okx_chains")
    suspend fun getSupportedChains(): List<OkxChainLocal>

    @Query("SELECT * FROM okx_chains")
    fun observeSupportedChains(): Flow<List<OkxChainLocal>>

    @Delete
    suspend fun deleteOkxChains(list: List<OkxChainLocal>)

    @Upsert
    suspend fun insertOkxChains(list: List<OkxChainLocal>)

    @Query("SELECT * FROM okx_tokens WHERE chainId = :chainId OR :chainId IS NULL")
    suspend fun getSupportedTokens(chainId: ChainId? = null): List<OkxTokenLocal>

    @Upsert
    suspend fun insertOkxTokens(list: List<OkxTokenLocal>)

    @Delete
    suspend fun deleteOkxTokens(list: List<OkxTokenLocal>)
}
