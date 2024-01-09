package jp.co.soramitsu.account.impl.presentation.node.mixin.impl

import androidx.lifecycle.asLiveData
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.data.mappers.mapNodeToNodeModel
import jp.co.soramitsu.account.impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.account.impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NodeListingProvider @Inject constructor(
    private val nodesSettingsScenario: NodesSettingsScenario,
    private val resourceManager: ResourceManager
) : NodeListingMixin {

    override fun groupedNodeModelsLiveData(chainId: ChainId) = getGroupedNodes(chainId)
        .asLiveData()

    private fun getGroupedNodes(chainId: ChainId) = nodesSettingsScenario.nodesFlow(chainId)
        .map(::transformToModels)
        .flowOn(Dispatchers.Default)

    private fun transformToModels(list: List<ChainNode>): List<Any> {
        val defaultHeader = NodeHeaderModel(resourceManager.getString(R.string.connection_management_default_title))
        val customHeader = NodeHeaderModel(resourceManager.getString(R.string.connection_management_custom_title))

        val defaultNodes = list.filter(ChainNode::isDefault)
        val customNodes = list.filter { !it.isDefault }

        return mutableListOf<Any>().apply {
            if (customNodes.isNotEmpty()) {
                add(customHeader)
                addAll(customNodes.map(::mapNodeToNodeModel))
            }
            add(defaultHeader)
            addAll(defaultNodes.map(::mapNodeToNodeModel))
        }
    }
}
