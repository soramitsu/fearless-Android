package jp.co.soramitsu.feature_account_impl.presentation.node.mixin.impl

import androidx.lifecycle.asLiveData
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNodeToNodeModel
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class NodeListingProvider(
    private val nodesSettingsScenario: NodesSettingsScenario,
    private val resourceManager: ResourceManager,
    private val chainId: ChainId
) : NodeListingMixin {

    override val groupedNodeModelsLiveData = getGroupedNodes()
        .asLiveData()

    private fun getGroupedNodes() = nodesSettingsScenario.nodesFlow(chainId)
        .map(::transformToModels)
        .flowOn(Dispatchers.Default)

    private fun transformToModels(list: List<Chain.Node>): List<Any> {
        val defaultHeader = NodeHeaderModel(resourceManager.getString(R.string.connection_management_default_title))
        val customHeader = NodeHeaderModel(resourceManager.getString(R.string.connection_management_custom_title))

        val defaultNodes = list.filter(Chain.Node::isDefault)
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
