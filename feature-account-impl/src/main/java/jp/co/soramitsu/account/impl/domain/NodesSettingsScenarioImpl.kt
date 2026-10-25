package jp.co.soramitsu.account.impl.domain

import android.database.sqlite.SQLiteConstraintException
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.multiNetwork.NodeMutation
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NodesSettingsScenarioImpl(private val chainRegistry: ChainRegistry) : NodesSettingsScenario {

    override fun nodesFlow(chainId: ChainId): Flow<List<ChainNode>> = chainRegistry.nodesFlow(chainId)

    override suspend fun getNode(id: NodeId): ChainNode = chainRegistry.getNode(id)

    override fun admitMutation(chainId: ChainId): NodeMutation = chainRegistry.admitNodeMutation(chainId)

    override suspend fun selectNode(mutation: NodeMutation, id: NodeId) = chainRegistry.switchNode(mutation, id)

    override suspend fun addNode(mutation: NodeMutation, chainId: ChainId, nodeName: String, nodeHost: String) =
        try {
            chainRegistry.addNode(mutation, chainId, nodeName, nodeHost)
            Result.success(Unit)
        } catch (e: SQLiteConstraintException) {
            Result.failure(NodeAlreadyExistsException())
        }

    override suspend fun deleteNode(mutation: NodeMutation, id: NodeId) = chainRegistry.deleteNode(mutation, id)

    override suspend fun updateNode(mutation: NodeMutation, id: NodeId, name: String, url: String) =
        try {
            chainRegistry.updateNode(mutation, id, name, url)
            Result.success(Unit)
        } catch (e: SQLiteConstraintException) {
            Result.failure(NodeAlreadyExistsException())
        }

    override suspend fun getChain(chainId: ChainId) = chainRegistry.getChain(chainId)
}
