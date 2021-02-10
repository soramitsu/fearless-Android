package jp.co.soramitsu.feature_account_impl.presentation.node.mixin.impl

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.mediatorLiveData
import jp.co.soramitsu.common.utils.setFrom
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class NodeListingProvider(
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager
) : NodeListingMixin {

    override val groupedNodeModelsLiveData = getGroupedNodes()
        .asLiveData()

    override val selectedNodeLiveData: MutableLiveData<NodeModel> = mediatorLiveData {
        setFrom(getSelectedNodeModel().asLiveData())
    }

    private fun getSelectedNodeModel() = accountInteractor.selectedNodeFlow()
        .map(::transformNode)

    private fun getGroupedNodes() = accountInteractor.nodesFlow()
        .map(::transformToModels)
        .flowOn(Dispatchers.Default)

    private fun transformToModels(list: List<Node>): List<Any> {
        val defaultHeader = NodeHeaderModel(resourceManager.getString(R.string.connection_management_default_title))
        val customHeader = NodeHeaderModel(resourceManager.getString(R.string.connection_management_custom_title))

        val defaultNodes = list.filter(Node::isDefault)
        val customNodes = list.filter { !it.isDefault }

        return mutableListOf<Any>().apply {
            add(defaultHeader)
            addAll(defaultNodes.map(::transformNode))

            if (customNodes.isNotEmpty()) {
                add(customHeader)
                addAll(customNodes.map(::transformNode))
            }
        }
    }

    private fun transformNode(node: Node): NodeModel {
        val networkModelType = when (node.networkType) {
            Node.NetworkType.KUSAMA -> NetworkModel.NetworkTypeUI.Kusama
            Node.NetworkType.POLKADOT -> NetworkModel.NetworkTypeUI.Polkadot
            Node.NetworkType.WESTEND -> NetworkModel.NetworkTypeUI.Westend
        }

        return NodeModel(node.id, node.name, node.link, networkModelType, node.isDefault)
    }
}