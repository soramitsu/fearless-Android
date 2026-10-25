package jp.co.soramitsu.account.impl.presentation.node.add

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import jp.co.soramitsu.runtime.multiNetwork.NodeMutation
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.domain.NativeNodeConnectionCheck
import jp.co.soramitsu.account.impl.domain.NodeHostValidator
import jp.co.soramitsu.account.impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.node.NodeDetailsRootViewModel
import jp.co.soramitsu.account.impl.presentation.node.add.AddNodeFragment.Companion.CHAIN_ID_KEY
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.data.network.solana.SolanaRpcRoutes
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@HiltViewModel
class AddNodeViewModel @Inject constructor(
    private val nodesSettingsScenario: NodesSettingsScenario,
    private val router: AccountRouter,
    private val nodeHostValidator: NodeHostValidator,
    private val resourceManager: ResourceManager,
    private val savedStateHandle: SavedStateHandle,
    private val socketServiceProvider: Provider<SocketService>,
    private val nativeNodeConnectionCheck: NativeNodeConnectionCheck
) : NodeDetailsRootViewModel(resourceManager) {

    companion object {
        const val NODE_CHECK_TIMEOUT_MILLIS = 2000L
    }

    private val chainId = savedStateHandle.get<String>(CHAIN_ID_KEY)!!

    val nodeNameInputLiveData = MutableLiveData("")
    val nodeHostInputLiveData = MutableLiveData("")

    private val addingInProgressLiveData = MutableLiveData(false)
    private val chainReady = MutableLiveData(false)
    private var isSolana = false
    private var solanaGenesisHash: String? = null
    private var mutation: NodeMutation? = null
    private var closed = false
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            try {
                val chain = nodesSettingsScenario.getChain(chainId)
                if (!closed) {
                    isSolana = chain.isUniversalWalletSolana()
                    solanaGenesisHash = chain.universalWalletSolanaIndexerNetwork()?.genesisHash
                    chainReady.value = true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (!closed) showError(resourceManager.getString(R.string.common_error_general_message))
            }
        }
    }

    val addButtonState = combine(
        nodeNameInputLiveData,
        nodeHostInputLiveData,
        addingInProgressLiveData,
        chainReady
    ) { (name: String, host: String, addingInProgress: Boolean, ready: Boolean) ->
        when {
            addingInProgress -> LabeledButtonState(ButtonState.PROGRESS)
            !ready -> LabeledButtonState(ButtonState.DISABLED)
            name.isEmpty() -> LabeledButtonState(ButtonState.DISABLED, resourceManager.getString(R.string.error_message_enter_the_name))
            !hostIsValid(host) -> LabeledButtonState(
                ButtonState.DISABLED,
                resourceManager.getString(R.string.error_message_enter_the_url_address)
            )
            else -> LabeledButtonState(ButtonState.NORMAL, resourceManager.getString(R.string.add_node_button_title))
        }
    }

    private fun hostIsValid(host: String): Boolean = if (isSolana) {
        runCatching { SolanaRpcRoutes.normalizeRpcUrl(host) }.isSuccess
    } else {
        nodeHostValidator.hostIsValid(host)
    }

    fun backClicked() {
        if (closed) return
        closed = true
        mutation?.cancel()
        operation?.cancel()
        router.back()
    }

    fun addNodeClicked() {
        if (closed || operation?.isActive == true || chainReady.value != true) return
        val nodeName = nodeNameInputLiveData.value?.takeIf { it.isNotBlank() } ?: return
        val input = nodeHostInputLiveData.value?.takeIf(::hostIsValid) ?: return
        val nodeHost = if (isSolana) SolanaRpcRoutes.normalizeRpcUrl(input) else input

        val admitted = try {
            nodesSettingsScenario.admitMutation(chainId)
        } catch (_: Exception) {
            showError(resourceManager.getString(R.string.common_error_general_message))
            return
        }
        mutation = admitted
        addingInProgressLiveData.value = true
        operation = viewModelScope.launch {
            try {
                checkNodeConnection(nodeHost).getOrThrow()
                ensureActive()
                nodesSettingsScenario.addNode(admitted, chainId, nodeName, nodeHost).getOrThrow()
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
                if (!closed) addingInProgressLiveData.value = false
            }
        }
    }

    private suspend fun checkNodeConnection(nodeHost: String): Result<Any?> {
        if (isSolana) {
            return try {
                withTimeout(NODE_CHECK_TIMEOUT_MILLIS) { nativeNodeConnectionCheck.checkSolana(nodeHost, requireNotNull(solanaGenesisHash)) }
                Result.success(Unit)
            } catch (_: TimeoutCancellationException) {
                Result.failure(FearlessException(FearlessException.Kind.NETWORK, null))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Result.failure(FearlessException(FearlessException.Kind.NETWORK, null))
            }
        }
        val socketService = socketServiceProvider.get()

        return try {
            socketService.start(nodeHost)

            val result = withTimeout(NODE_CHECK_TIMEOUT_MILLIS) {
                socketService.executeAsync(RuntimeVersionRequest())
            }

            Result.success(result)
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(FearlessException(FearlessException.Kind.NETWORK, null))
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching {
                socketService.stop()
            }
        }
    }

    override fun onCleared() {
        closed = true
        mutation?.cancel()
        super.onCleared()
    }
}
