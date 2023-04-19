package jp.co.soramitsu.account.impl.presentation.node.add

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.account.impl.domain.NodeHostValidator
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.node.NodeDetailsRootViewModel
import jp.co.soramitsu.account.impl.presentation.node.add.AddNodeFragment.Companion.CHAIN_ID_KEY
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Provider

@HiltViewModel
class AddNodeViewModel @Inject constructor(
    private val nodesSettingsScenario: NodesSettingsScenario,
    private val router: AccountRouter,
    private val nodeHostValidator: NodeHostValidator,
    resourceManager: ResourceManager,
    private val savedStateHandle: SavedStateHandle,
    private val socketServiceProvider: Provider<SocketService>
) : NodeDetailsRootViewModel(resourceManager) {

    companion object {
        const val NODE_CHECK_TIMEOUT_MILLIS = 2000L
    }

    private val chainId = savedStateHandle.get<String>(CHAIN_ID_KEY)!!

    val nodeNameInputLiveData = MutableLiveData("")
    val nodeHostInputLiveData = MutableLiveData("")

    private val addingInProgressLiveData = MutableLiveData(false)

    val addButtonState = combine(
        nodeNameInputLiveData,
        nodeHostInputLiveData,
        addingInProgressLiveData
    ) { (name: String, host: String, addingInProgress: Boolean) ->
        when {
            addingInProgress -> LabeledButtonState(ButtonState.PROGRESS)
            name.isEmpty() -> LabeledButtonState(ButtonState.DISABLED, resourceManager.getString(R.string.error_message_enter_the_name))
            !nodeHostValidator.hostIsValid(host) -> LabeledButtonState(
                ButtonState.DISABLED,
                resourceManager.getString(R.string.error_message_enter_the_url_address)
            )
            else -> LabeledButtonState(ButtonState.NORMAL, resourceManager.getString(R.string.add_node_button_title))
        }
    }

    fun backClicked() {
        router.back()
    }

    fun addNodeClicked() {
        val nodeName = nodeNameInputLiveData.value ?: return
        val nodeHost = nodeHostInputLiveData.value ?: return

        addingInProgressLiveData.value = true

        viewModelScope.launch {
            val checkResult = checkNodeConnection(nodeHost)
            if (checkResult.isSuccess.not()) {
                handleNodeException(checkResult.requireException())
                addingInProgressLiveData.postValue(false)
                return@launch
            }

            val result = nodesSettingsScenario.addNode(chainId, nodeName, nodeHost)

            if (result.isSuccess) {
                router.back()
            } else {
                handleNodeException(result.requireException())
                addingInProgressLiveData.postValue(false)
            }
        }
    }

    private suspend fun checkNodeConnection(nodeHost: String): Result<Any?> {
        val socketService = socketServiceProvider.get()

        return try {
            socketService.start(nodeHost)

            val result = withTimeout(NODE_CHECK_TIMEOUT_MILLIS) {
                socketService.executeAsync(RuntimeVersionRequest())
            }

            Result.success(result)
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(FearlessException(FearlessException.Kind.NETWORK, null))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching {
                socketService.stop()
            }
        }
    }
}
