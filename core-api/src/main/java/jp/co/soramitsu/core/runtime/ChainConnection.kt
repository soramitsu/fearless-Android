package jp.co.soramitsu.core.runtime

import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

class ChainConnection(
    val chain: IChain,
    val socketService: SocketService,
    initialNodes: List<ChainNode>,
    private val externalRequirementFlow: MutableStateFlow<ExternalRequirement>,
    private val onSelectedNodeChange: (String) -> Unit,
    private val isAutoBalanceEnabled: () -> Boolean
) {
    enum class ExternalRequirement {
        ALLOWED,
        STOPPED,
        FORBIDDEN
    }

    private val nodesFlow = MutableStateFlow(initialNodes)
    private val selectedNodeFlow = MutableStateFlow(initialNodes.firstOrNull())
    private val stateFlow = MutableStateFlow<SocketStateMachine.State>(SocketStateMachine.initialState())
    val state: StateFlow<SocketStateMachine.State> = stateFlow
    val isConnected: Flow<Unit> = stateFlow
        .filter { it is SocketStateMachine.State.Connected }
        .map { Unit }

    init {
        socketService.addStateObserver { stateFlow.value = it }
    }

    val nodes: List<ChainNode>
        get() = nodesFlow.value

    val selectedNode: ChainNode?
        get() = selectedNodeFlow.value

    fun considerUpdateNodes(nodes: List<ChainNode>) {
        nodesFlow.value = nodes
        if (selectedNodeFlow.value == null || selectedNodeFlow.value !in nodes) {
            selectedNodeFlow.value = nodes.firstOrNull()
            selectedNodeFlow.value?.url?.let(onSelectedNodeChange)
        }

        if (state.value is SocketStateMachine.State.Connected && isAutoBalanceEnabled()) {
            selectedNodeFlow.value?.url?.let(socketService::switchUrl)
        }
    }

    fun finish() {
        socketService.stop()
    }

    @Suppress("unused")
    fun applyExternalRequirement(requirement: ExternalRequirement) {
        externalRequirementFlow.value = requirement
        when (requirement) {
            ExternalRequirement.ALLOWED -> socketService.resume()
            ExternalRequirement.STOPPED,
            ExternalRequirement.FORBIDDEN -> socketService.pause()
        }
    }
}
