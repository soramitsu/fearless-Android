package jp.co.soramitsu.feature_account_impl.presentation.node.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_account_api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import kotlinx.coroutines.launch

class NodesViewModel(
    private val router: AccountRouter,
    private val nodeListingMixin: NodeListingMixin,
    private val resourceManager: ResourceManager,
    private val chainId: String,
    private val nodesSettingsScenario: NodesSettingsScenario,
) : BaseViewModel(), NodeListingMixin by nodeListingMixin {

    private val _editMode = MutableLiveData<Boolean>()
    val editMode: LiveData<Boolean> = _editMode

    private val _deleteNodeEvent = MutableLiveData<Event<NodeModel>>()
    val deleteNodeEvent: LiveData<Event<NodeModel>> = _deleteNodeEvent

    val chainInfo: LiveData<Pair<String, String>> = liveData {
        val chain = nodesSettingsScenario.getChain(chainId)
        emit(chain.name to chain.icon)
    }

    val toolbarAction = editMode.map {
        if (it) {
            resourceManager.getString(R.string.common_done)
        } else {
            resourceManager.getString(R.string.common_edit)
        }
    }

    fun editClicked() {
        val edit = editMode.value ?: false
        _editMode.value = !edit
    }

    fun backClicked() {
        router.back()
    }

    fun infoClicked(nodeModel: NodeModel) {
        router.openNodeDetails(chainId, nodeModel.link)
    }

    fun selectNodeClicked(nodeModel: NodeModel) {
        viewModelScope.launch {
            nodesSettingsScenario.selectNode(NodeId(chainId to nodeModel.link))
        }
    }

    fun addNodeClicked() {
        router.openAddNode(chainId)
    }

    fun deleteNodeClicked(nodeModel: NodeModel) {
        _deleteNodeEvent.value = Event(nodeModel)
    }

    fun confirmNodeDeletion(nodeModel: NodeModel) {
        viewModelScope.launch {
            nodesSettingsScenario.deleteNode(NodeId(chainId to nodeModel.link))
        }
    }
}
