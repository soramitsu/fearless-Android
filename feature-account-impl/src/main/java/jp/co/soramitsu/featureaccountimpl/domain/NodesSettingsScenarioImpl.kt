package jp.co.soramitsu.featureaccountimpl.domain

import android.database.sqlite.SQLiteConstraintException
import jp.co.soramitsu.featureaccountapi.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.featureaccountimpl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import kotlinx.coroutines.flow.Flow

class NodesSettingsScenarioImpl(private val chainRegistry: ChainRegistry) : NodesSettingsScenario {

    override fun nodesFlow(chainId: ChainId): Flow<List<Chain.Node>> = chainRegistry.nodesFlow(chainId)

    override suspend fun getNode(id: NodeId): Chain.Node = chainRegistry.getNode(id)

    override suspend fun selectNode(id: NodeId) = chainRegistry.selectNode(id)

    override suspend fun addNode(chainId: ChainId, nodeName: String, nodeHost: String) =
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

    override suspend fun getChain(chainId: ChainId) = chainRegistry.getChain(chainId)
}
