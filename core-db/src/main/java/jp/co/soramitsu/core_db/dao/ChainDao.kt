package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import jp.co.soramitsu.core_db.model.chain.ChainAssetLocal
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.ChainNodeLocal
import jp.co.soramitsu.core_db.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChainDao {

    @Transaction
    open suspend fun update(
        removed: List<ChainLocal>,
        newOrUpdated: List<JoinedChainInfo>,
    ) {
        deleteChains(removed)

        deleteChains(newOrUpdated.map(JoinedChainInfo::chain)) // delete all nodes and assets associated with changed chains

        insertChains(newOrUpdated.map(JoinedChainInfo::chain))
        insertChainNodes(newOrUpdated.flatMap(JoinedChainInfo::nodes))
        insertChainAssets(newOrUpdated.flatMap(JoinedChainInfo::assets))
    }

    @Delete
    protected abstract suspend fun deleteChains(chains: List<ChainLocal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertChains(chains: List<ChainLocal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertChainNodes(nodes: List<ChainNodeLocal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertChainAssets(assets: List<ChainAssetLocal>)

    @Query("SELECT * FROM chains")
    @Transaction
    abstract suspend fun getJoinChainInfo(): List<JoinedChainInfo>

    @Query("SELECT * FROM chains")
    @Transaction
    abstract fun joinChainInfoFlow(): Flow<List<JoinedChainInfo>>

    @Query("SELECT * FROM chain_runtimes WHERE chainId = :chainId")
    abstract suspend fun runtimeInfo(chainId: String): ChainRuntimeInfoLocal?

    @Query("UPDATE chain_runtimes SET syncedVersion = :syncedVersion WHERE chainId = :chainId")
    abstract suspend fun updateSyncedRuntimeVersion(chainId: String, syncedVersion: Int)

    @Transaction
    open suspend fun updateRemoteRuntimeVersion(chainId: String, remoteVersion: Int) {
        if (isRuntimeInfoExists(chainId)) {
            updateRemoteRuntimeVersionUnsafe(chainId, remoteVersion)
        } else {
            insertRuntimeInfo(ChainRuntimeInfoLocal(chainId, syncedVersion = 0, remoteVersion = remoteVersion))
        }
    }

    @Query("UPDATE chain_runtimes SET remoteVersion = :remoteVersion WHERE chainId = :chainId")
    protected abstract suspend fun updateRemoteRuntimeVersionUnsafe(chainId: String, remoteVersion: Int)

    @Query("SELECT EXISTS (SELECT * FROM chain_runtimes WHERE chainId = :chainId)")
    protected abstract suspend fun isRuntimeInfoExists(chainId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRuntimeInfo(runtimeInfoLocal: ChainRuntimeInfoLocal)
}
