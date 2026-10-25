package jp.co.soramitsu.account.impl.presentation.node.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.runtime.multiNetwork.NodeMutation
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.account.impl.presentation.node.list.NodesFragment.Companion.CHAIN_ID_KEY
import jp.co.soramitsu.account.impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    private var mutation: NodeMutation? = null
    private var closed = false
    private var selectionJob: Job? = null
    val selectionPending = MutableLiveData(false)
    val supportsAutoSelection = MutableLiveData(true)

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
            .map { if (supportsAutoSelection.value == true) nodesSettingsStorage.setIsAutoSelectNodes(chainId, it) }
            .share()
    }

    init {
        viewModelScope.launch {
            try {
                if (nodesSettingsScenario.getChain(chainId).isUniversalWalletSolana() && !closed) {
                    // HTTP RPC selection is explicit; websocket node balancing
                    // preferences do not control this native transport.
                    supportsAutoSelection.value = false
                    autoSelectedNodeFlow.value = false
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (!closed) showError(resourceManager.getString(R.string.common_error_general_message))
            }
        }
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
        if (closed) return
        closed = true
        mutation?.cancel()
        selectionJob?.cancel()
        router.back()
    }

    fun infoClicked(nodeModel: NodeModel) {
        if (closed || selectionJob?.isActive == true) return
        router.openNodeDetails(NodeDetailsPayload(chainId, nodeModel.link))
    }

    fun selectNodeClicked(nodeModel: NodeModel) {
        if (closed || selectionJob?.isActive == true) return
        val admitted = try {
            nodesSettingsScenario.admitMutation(chainId)
        } catch (_: Exception) {
            showError(resourceManager.getString(R.string.common_error_general_message))
            return
        }
        mutation = admitted
        selectionPending.value = true
        selectionJob = viewModelScope.launch {
            try {
                nodesSettingsScenario.selectNode(admitted, NodeId(chainId to nodeModel.link))
                ensureActive()
                if (!closed && admitted.isLatest) {
                    closed = true
                    router.openMain()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (!closed && admitted.isLatest) showError(resourceManager.getString(R.string.common_error_general_message))
            } finally {
                admitted.cancel()
                if (!closed) selectionPending.value = false
            }
        }
    }

    fun addNodeClicked() {
        if (closed || selectionJob?.isActive == true) return
        router.openAddNode(chainId)
    }

    fun deleteNodeClicked(nodeModel: NodeModel) {
        _deleteNodeEvent.value = Event(nodeModel)
    }

    fun confirmNodeDeletion(nodeModel: NodeModel) {
        if (closed || selectionJob?.isActive == true) return
        val admitted = try {
            nodesSettingsScenario.admitMutation(chainId)
        } catch (_: Exception) {
            showError(resourceManager.getString(R.string.common_error_general_message))
            return
        }
        mutation = admitted
        selectionPending.value = true
        selectionJob = viewModelScope.launch {
            try {
                nodesSettingsScenario.deleteNode(admitted, NodeId(chainId to nodeModel.link))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (!closed && admitted.isLatest) showError(resourceManager.getString(R.string.common_error_general_message))
            } finally {
                admitted.cancel()
                if (!closed) selectionPending.value = false
            }
        }
    }

    override fun onCleared() {
        closed = true
        mutation?.cancel()
        super.onCleared()
    }
}
