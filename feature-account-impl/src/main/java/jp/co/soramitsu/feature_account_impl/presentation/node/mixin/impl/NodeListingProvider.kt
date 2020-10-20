package jp.co.soramitsu.feature_account_impl.presentation.node.mixin.impl

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.asMutableLiveData
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

class NodeListingProvider(
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager
) : NodeListingMixin {

    override val nodeListingDisposable: CompositeDisposable = CompositeDisposable()

    override val groupedNodeModelsLiveData = getGroupedNodes()
        .asLiveData(nodeListingDisposable)

    override val selectedNodeLiveData = getSelectedNodeModel()
        .asMutableLiveData(nodeListingDisposable)

    private fun getSelectedNodeModel() = accountInteractor.observeSelectedNode()
        .subscribeOn(Schedulers.computation())
        .map(::transformNode)
        .observeOn(AndroidSchedulers.mainThread())

    private fun getGroupedNodes() = accountInteractor.observeNodes()
        .subscribeOn(Schedulers.computation())
        .map(::transformToModels)
        .observeOn(AndroidSchedulers.mainThread())

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