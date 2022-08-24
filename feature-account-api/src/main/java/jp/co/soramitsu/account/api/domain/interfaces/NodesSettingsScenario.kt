package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import kotlinx.coroutines.flow.Flow

interface NodesSettingsScenario {
    suspend fun getChain(chainId: ChainId): Chain

    fun nodesFlow(chainId: ChainId): Flow<List<Chain.Node>>

    suspend fun selectNode(id: NodeId)
    suspend fun getNode(id: NodeId): Chain.Node
    suspend fun deleteNode(id: NodeId)
    suspend fun addNode(chainId: ChainId, nodeName: String, nodeHost: String): Result<Unit>
    suspend fun updateNode(id: NodeId, name: String, url: String): Result<Unit>
}
