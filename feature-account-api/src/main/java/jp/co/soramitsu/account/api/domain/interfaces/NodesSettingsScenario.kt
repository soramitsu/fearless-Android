package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.runtime.multiNetwork.NodeMutation
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import kotlinx.coroutines.flow.Flow

interface NodesSettingsScenario {
    suspend fun getChain(chainId: ChainId): Chain

    fun nodesFlow(chainId: ChainId): Flow<List<ChainNode>>

    fun admitMutation(chainId: ChainId): NodeMutation

    suspend fun selectNode(mutation: NodeMutation, id: NodeId)
    suspend fun getNode(id: NodeId): ChainNode
    suspend fun deleteNode(mutation: NodeMutation, id: NodeId)
    suspend fun addNode(mutation: NodeMutation, chainId: ChainId, nodeName: String, nodeHost: String): Result<Unit>
    suspend fun updateNode(mutation: NodeMutation, id: NodeId, name: String, url: String): Result<Unit>
}
