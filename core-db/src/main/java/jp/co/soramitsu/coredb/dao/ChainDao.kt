package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChainDao {

    @Transaction
    open suspend fun update(
        removed: List<ChainLocal>,
        newOrUpdated: List<JoinedChainInfo>
    ) {
        // saving custom nodes before deleting
        val customNodes = getCustomNodes().filter {
            // excluding nodes for removed chains
            it.chainId !in removed.map { it.id }
        }

        deleteChains(removed)

        deleteChains(newOrUpdated.map(JoinedChainInfo::chain)) // delete all nodes and assets associated with changed chains

        insertChains(newOrUpdated.map(JoinedChainInfo::chain))
        insertChainNodes(newOrUpdated.flatMap(JoinedChainInfo::nodes))
        insertChainNodes(customNodes)
        insertChainAssets(newOrUpdated.flatMap(JoinedChainInfo::assets))
        insertChainExplorers(newOrUpdated.flatMap(JoinedChainInfo::explorers))
    }

    @Delete()
    protected abstract suspend fun deleteChains(chains: List<ChainLocal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertChains(chains: List<ChainLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChainNodes(nodes: List<ChainNodeLocal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertChainNode(nodes: ChainNodeLocal)


    @Query("SELECT * FROM chain_nodes WHERE chainId = :chainId")
    abstract fun nodesFlow(chainId: String): Flow<List<ChainNodeLocal>>

    @Transaction
    open suspend fun selectNode(chainId: String, nodeUrl: String) {
        clearNodeSelection(chainId)
        makeNodeSelected(chainId, nodeUrl)
    }

    @Query("UPDATE chain_nodes SET isActive = 0 WHERE chainId = :chainId AND isActive = 1")
    protected abstract suspend fun clearNodeSelection(chainId: String)

    @Query("SELECT * FROM chain_nodes WHERE isDefault = 0")
    protected abstract suspend fun getCustomNodes(): List<ChainNodeLocal>

    @Query("UPDATE chain_nodes SET isActive = 1 WHERE chainId = :chainId AND url = :nodeUrl")
    protected abstract suspend fun makeNodeSelected(chainId: String, nodeUrl: String)

    @Query("DELETE FROM chain_nodes WHERE chainId = :chainId AND url = :nodeUrl")
    abstract suspend fun deleteNode(chainId: String, nodeUrl: String)

    @Query("SELECT * FROM chain_nodes WHERE chainId = :chainId AND url = :nodeUrl")
    abstract suspend fun getNode(chainId: String, nodeUrl: String): ChainNodeLocal

    @Query("UPDATE chain_nodes SET name = :nodeName, url = :nodeUrl WHERE chainId = :chainId and url = :prevNodeUrl")
    abstract suspend fun updateNode(chainId: String, prevNodeUrl: String, nodeName: String, nodeUrl: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertChainAssets(assets: List<ChainAssetLocal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertChainExplorers(explorers: List<ChainExplorerLocal>)

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
