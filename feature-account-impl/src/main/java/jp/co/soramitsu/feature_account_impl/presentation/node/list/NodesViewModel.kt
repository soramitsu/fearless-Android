package jp.co.soramitsu.feature_account_impl.presentation.node.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_account_api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.feature_account_impl.presentation.node.list.NodesFragment.Companion.CHAIN_ID_KEY
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NodesViewModel @Inject constructor(
    private val router: AccountRouter,
    private val nodeListingMixin: NodeListingMixin,
    private val resourceManager: ResourceManager,
    private val nodesSettingsScenario: NodesSettingsScenario,
    private val nodesSettingsStorage: NodesSettingsStorage,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), NodeListingMixin by nodeListingMixin {

    private val chainId = savedStateHandle.get<ChainId>(CHAIN_ID_KEY)!!

    private val _editMode = MutableLiveData<Boolean>()
    val editMode: LiveData<Boolean> = _editMode

    private val _deleteNodeEvent = MutableLiveData<Event<NodeModel>>()
    val deleteNodeEvent: LiveData<Event<NodeModel>> = _deleteNodeEvent

    val hasCustomNodeModelsLiveData = groupedNodeModelsLiveData(chainId).map {
        it.any { (it as? NodeModel)?.isDefault == false }
    }

    val chainName: LiveData<String> = liveData {
        val chain = nodesSettingsScenario.getChain(chainId)
        emit(chain.name)
    }

    val autoSelectedNodeFlow = MutableStateFlow(nodesSettingsStorage.getIsAutoSelectNodes(chainId)).apply {
        this.drop(1)
            .map { nodesSettingsStorage.setIsAutoSelectNodes(chainId, it) }
            .share()
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
        router.openNodeDetails(NodeDetailsPayload(chainId, nodeModel.link))
    }

    fun selectNodeClicked(nodeModel: NodeModel) {
        viewModelScope.launch {
            nodesSettingsScenario.selectNode(NodeId(chainId to nodeModel.link))
            router.openMain()
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
