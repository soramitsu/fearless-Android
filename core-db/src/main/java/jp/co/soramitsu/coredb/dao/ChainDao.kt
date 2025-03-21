package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.coredb.dao.AssetDao.Companion.xcPrefix
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.coredb.model.chain.ChainTypesLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

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

        insertChains(newOrUpdated.map(JoinedChainInfo::chain))
        insertChainNodes(newOrUpdated.flatMap(JoinedChainInfo::nodes))
        insertChainNodes(customNodes)
        insertChainAssets(newOrUpdated.flatMap(JoinedChainInfo::assets))
        insertChainExplorers(newOrUpdated.flatMap(JoinedChainInfo::explorers))
    }

    @Transaction
    open suspend fun updateChains(chainsToAdd: List<ChainLocal>, chainsToUpdate: List<ChainLocal>) {
        insertChains(chainsToAdd)
        updateChains(chainsToUpdate)
    }

    @Transaction
    open suspend fun updateAssets(assetsToAdd: List<ChainAssetLocal>, assetsToUpdate: List<ChainAssetLocal>, assetsToRemove: List<ChainAssetLocal>) {
        insertChainAssets(assetsToAdd)
        updateChainAssets(assetsToUpdate)
        deleteChainAssets(assetsToRemove)
    }

    @Transaction
    open suspend fun updateNodes(nodesToAdd: List<ChainNodeLocal>, nodesToUpdate: List<ChainNodeLocal>, nodesToRemove: List<ChainNodeLocal>) {
        insertChainNodes(nodesToAdd)
        updateChainNodes(nodesToUpdate)
        deleteChainNodes(nodesToRemove)
    }

    @Transaction
    open suspend fun updateExplorers(
        explorersToAdd: MutableList<ChainExplorerLocal>,
        explorersToUpdate: MutableList<ChainExplorerLocal>,
        explorersToRemove: List<ChainExplorerLocal>
    ) {
        insertChainExplorers(explorersToAdd)
        updateChainExplorers(explorersToUpdate)
        deleteChainExplorers(explorersToRemove)
    }

    @Delete
    abstract suspend fun deleteChains(chains: List<ChainLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChains(chains: List<ChainLocal>)

    @Update
    abstract suspend fun updateChains(chains: List<ChainLocal>)

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
    abstract suspend fun updateNode(
        chainId: String,
        prevNodeUrl: String,
        nodeName: String,
        nodeUrl: String
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChainAssets(assets: List<ChainAssetLocal>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertChainAssetsIgnoringConflicts(assets: List<ChainAssetLocal>)

    @Update
    protected abstract suspend fun updateChainAssets(assets: List<ChainAssetLocal>)
    @Delete
    protected abstract suspend fun deleteChainAssets(assets: List<ChainAssetLocal>)

    @Update
    protected abstract suspend fun updateChainNodes(nodes: List<ChainNodeLocal>)
    @Delete
    protected abstract suspend fun deleteChainNodes(nodes: List<ChainNodeLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChainExplorers(explorers: List<ChainExplorerLocal>)

    @Update
    protected abstract suspend fun updateChainExplorers(explorers: List<ChainExplorerLocal>)

    @Delete
    protected abstract suspend fun deleteChainExplorers(explorers: List<ChainExplorerLocal>)

    @Query("SELECT * FROM chains")
    @Transaction
    abstract suspend fun getJoinChainInfo(): List<JoinedChainInfo>

    @Query("SELECT * FROM chains WHERE id = :chainId")
    @Transaction
    abstract suspend fun getJoinChainInfo(chainId: String): JoinedChainInfo

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
            insertRuntimeInfo(
                ChainRuntimeInfoLocal(
                    chainId,
                    syncedVersion = 0,
                    remoteVersion = remoteVersion
                )
            )
        }
    }

    @Query("UPDATE chain_runtimes SET remoteVersion = :remoteVersion WHERE chainId = :chainId")
    protected abstract suspend fun updateRemoteRuntimeVersionUnsafe(
        chainId: String,
        remoteVersion: Int
    )

    @Query("SELECT EXISTS (SELECT * FROM chain_runtimes WHERE chainId = :chainId)")
    protected abstract suspend fun isRuntimeInfoExists(chainId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRuntimeInfo(runtimeInfoLocal: ChainRuntimeInfoLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTypes(types: List<ChainTypesLocal>)

    @Query("SELECT typesConfig FROM chain_types WHERE chainId = :chainId")
    abstract suspend fun getTypes(chainId: String): String?

    @Query("SELECT * FROM chain_assets")
    abstract suspend fun getAssetsConfigs(): List<ChainAssetLocal>

    open fun observeChainsWithBalance(
        accountMetaId: Long,
        assetId: String
    ): Flow<Map<JoinedChainInfo, AssetWithToken>> {
        return observeAssetSymbolById(assetId).flatMapLatest { symbol ->
            observeChainsWithBalanceByName(
                accountMetaId = accountMetaId,
                assetSymbol = symbol.removedXcPrefix()
            )
        }
    }

    @Query(
        """
            SELECT symbol FROM chain_assets WHERE chain_assets.id = :assetId
        """
    )
    protected abstract fun observeAssetSymbolById(assetId: String): Flow<String>

    @Transaction
    @Query(
        """
            SELECT c.*, a.*, tp.* FROM chains c
            JOIN chain_assets ca ON ca.chainId = c.id AND ca.symbol in (:assetSymbol, '$xcPrefix'||:assetSymbol)
            LEFT JOIN assets a ON a.chainId = c.id AND a.id = ca.id AND a.metaId = :accountMetaId AND a.enabled = 1
            LEFT JOIN token_price tp ON tp.priceId = a.tokenPriceId
        """
    )
    protected abstract fun observeChainsWithBalanceByName(
        accountMetaId: Long,
        assetSymbol: String
    ): Flow<Map<JoinedChainInfo, AssetWithToken>>

}
