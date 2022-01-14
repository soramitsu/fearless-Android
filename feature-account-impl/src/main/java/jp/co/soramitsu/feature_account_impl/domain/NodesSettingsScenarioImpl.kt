package jp.co.soramitsu.feature_account_impl.domain

import android.database.sqlite.SQLiteConstraintException
import jp.co.soramitsu.feature_account_api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.feature_account_impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import kotlinx.coroutines.flow.Flow

class NodesSettingsScenarioImpl(private val chainRegistry: ChainRegistry) : NodesSettingsScenario {

    override fun nodesFlow(chainId: String): Flow<List<Chain.Node>> = chainRegistry.nodesFlow(chainId)

    override suspend fun getNode(id: NodeId): Chain.Node = chainRegistry.getNode(id)

    override suspend fun selectNode(id: NodeId) = chainRegistry.selectNode(id)

    override suspend fun addNode(chainId: String, nodeName: String, nodeHost: String) =
        try {
            chainRegistry.addNode(chainId, nodeName, nodeHost)
            Result.success(Unit)
        } catch (e: SQLiteConstraintException) {
            Result.failure(NodeAlreadyExistsException())
        }

    override suspend fun deleteNode(id: NodeId) = chainRegistry.deleteNode(id)

    override suspend fun updateNode(id: NodeId, name: String, url: String) =
        try {
            chainRegistry.updateNode(id, name, url)
            Result.success(Unit)
        } catch (e: SQLiteConstraintException) {
            Result.failure(NodeAlreadyExistsException())
        }

    override suspend fun getChain(chainId: String) = chainRegistry.getChain(chainId)
}

