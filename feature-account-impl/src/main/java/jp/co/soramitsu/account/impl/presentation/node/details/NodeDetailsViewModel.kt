package jp.co.soramitsu.account.impl.presentation.node.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.runtime.multiNetwork.NodeMutation
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.domain.NativeNodeConnectionCheck
import jp.co.soramitsu.account.impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.node.NodeDetailsRootViewModel
import jp.co.soramitsu.account.impl.presentation.node.add.AddNodeViewModel
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.data.network.solana.SolanaRpcRoutes
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.setValueIfNew
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@HiltViewModel
class NodeDetailsViewModel @Inject constructor(
    private val nodesSettingsScenario: NodesSettingsScenario,
    private val router: AccountRouter,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val savedStateHandle: SavedStateHandle,
    private val nativeNodeConnectionCheck: NativeNodeConnectionCheck
) : NodeDetailsRootViewModel(resourceManager) {

    private var operation: Job? = null
    private var mutation: NodeMutation? = null
    private var closed = false
    private val payload = savedStateHandle.get<NodeDetailsPayload>(NodeDetailsFragment.PAYLOAD_KEY)!!

    val nodeModelLiveData = liveData {
        emit(nodesSettingsScenario.getNode(NodeId(payload.chainId to payload.nodeUrl)))
    }

    val chainInfoLiveData = liveData {
        emit(nodesSettingsScenario.getChain(payload.chainId))
    }

    val nameEditEnabled = nodeModelLiveData.map(::mapNodeNameEditState)
    val hostEditEnabled = nodeModelLiveData.map(::mapNodeHostEditState)

    private val _updateButtonEnabled = MutableLiveData<Boolean>()
    val updateButtonEnabled: LiveData<Boolean> = _updateButtonEnabled

    fun backClicked() {
        if (closed) return
        closed = true
        mutation?.cancel()
        operation?.cancel()
        router.back()
    }

    fun nodeDetailsEdited() {
        if (!closed && operation?.isActive != true) _updateButtonEnabled.setValueIfNew(true)
    }

    fun copyNodeHostClicked() {
        nodeModelLiveData.value?.let {
            clipboardManager.addToClipboard(it.url)

            showMessage(resourceManager.getString(R.string.common_copied))
        }
    }

    fun updateClicked(name: String, hostUrl: String) {
        if (closed || operation?.isActive == true) return
        val admitted = try {
            nodesSettingsScenario.admitMutation(payload.chainId)
        } catch (_: Exception) {
            showError(resourceManager.getString(R.string.common_error_general_message))
            return
        }
        mutation = admitted
        _updateButtonEnabled.value = false
        operation = viewModelScope.launch {
            try {
                require(name.isNotBlank())
                val id = NodeId(payload.chainId to payload.nodeUrl)
                val node = nodesSettingsScenario.getNode(id)
                require(!node.isDefault)
                val chain = nodesSettingsScenario.getChain(payload.chainId)
                val checkedHost = if (chain.isUniversalWalletSolana()) checkedNativeHost(hostUrl, node.url, requireNotNull(chain.universalWalletSolanaIndexerNetwork()).genesisHash) else hostUrl
                ensureActive()
                // The endpoint may have become active while its probe was held.
                val latest = nodesSettingsScenario.getNode(id)
                require(!latest.isDefault && (!latest.isActive || checkedHost == latest.url))
                nodesSettingsScenario.updateNode(admitted, id, name, checkedHost).getOrThrow()
                ensureActive()
                if (!closed && admitted.isLatest) {
                    closed = true
                    router.back()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!closed && admitted.isLatest) {
                    if (error is NodeAlreadyExistsException || error is FearlessException) handleNodeException(error)
                    else showError(resourceManager.getString(R.string.common_error_general_message))
                }
            } finally {
                admitted.cancel()
                if (!closed) _updateButtonEnabled.value = true
            }
        }
    }

    private suspend fun checkedNativeHost(host: String, original: String, expectedGenesisHash: String): String {
        return try {
            val normalized = SolanaRpcRoutes.normalizeRpcUrl(host)
            if (normalized != original) {
                withTimeout(AddNodeViewModel.NODE_CHECK_TIMEOUT_MILLIS) { nativeNodeConnectionCheck.checkSolana(normalized, expectedGenesisHash) }
            }
            normalized
        } catch (_: TimeoutCancellationException) {
            throw FearlessException(FearlessException.Kind.NETWORK, null)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw FearlessException(FearlessException.Kind.NETWORK, null)
        }
    }

    private fun mapNodeNameEditState(node: ChainNode): Boolean {
        return !node.isDefault
    }

    private fun mapNodeHostEditState(node: ChainNode): Boolean {
        return !node.isDefault && !node.isActive
    }

    override fun onCleared() {
        closed = true
        mutation?.cancel()
        super.onCleared()
    }
}
