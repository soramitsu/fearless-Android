package jp.co.soramitsu.feature_account_impl.presentation.node.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.setValueIfNew
import jp.co.soramitsu.feature_account_api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.NodeDetailsRootViewModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import kotlinx.coroutines.launch

class NodeDetailsViewModel(
    private val nodesSettingsScenario: NodesSettingsScenario,
    private val router: AccountRouter,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val chainId: String,
    private val nodeUrl: String
) : NodeDetailsRootViewModel(resourceManager) {

    val nodeModelLiveData = liveData {
        emit(nodesSettingsScenario.getNode(NodeId(chainId to nodeUrl)))
    }

    val chainInfoLiveData = liveData {
        emit(nodesSettingsScenario.getChain(chainId))
    }

    val nameEditEnabled = nodeModelLiveData.map(::mapNodeNameEditState)
    val hostEditEnabled = nodeModelLiveData.map(::mapNodeHostEditState)

    private val _updateButtonEnabled = MutableLiveData<Boolean>()
    val updateButtonEnabled: LiveData<Boolean> = _updateButtonEnabled

    fun backClicked() {
        router.back()
    }

    fun nodeDetailsEdited() {
        _updateButtonEnabled.setValueIfNew(true)
    }

    fun copyNodeHostClicked() {
        nodeModelLiveData.value?.let {
            clipboardManager.addToClipboard(it.url)

            showMessage(resourceManager.getString(R.string.common_copied))
        }
    }

    fun updateClicked(name: String, hostUrl: String) {
        viewModelScope.launch {

            val result = nodesSettingsScenario.updateNode(NodeId(chainId to nodeUrl), name, hostUrl)

            if (result.isSuccess) {
                router.back()
            } else {
                handleNodeException(result.requireException())
            }
        }
    }

    private fun mapNodeNameEditState(node: Chain.Node): Boolean {
        return !node.isDefault
    }

    private fun mapNodeHostEditState(node: Chain.Node): Boolean {
        return !node.isDefault && !node.isActive
    }
}
