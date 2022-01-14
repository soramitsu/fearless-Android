package jp.co.soramitsu.runtime.multiNetwork.connection

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.networkStateFlow
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

private const val NODE_SWITCHING_FREQUENCY = 5 // switch node every n attempt

class ChainConnection(
    val socketService: SocketService,
    externalRequirementFlow: Flow<ExternalRequirement>,
    initialNodes: List<Chain.Node>,
    private val onSelectedNodeChange: (newNodeUrl: String) -> Unit
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    enum class ExternalRequirement {
        ALLOWED, STOPPED, FORBIDDEN
    }

    private var availableNodes: List<Chain.Node> = initialNodes

    val state = socketService.networkStateFlow()
        .stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = State.Disconnected)

    init {
        require(availableNodes.isNotEmpty()) {
            "Cannot start connection with no available nodes"
        }

        state.onEach(::autoBalance)
            .launchIn(this)

        externalRequirementFlow.onEach {
            if (it == ExternalRequirement.ALLOWED) {
                socketService.resume()
            } else {
                socketService.pause()
            }
        }.launchIn(this)

        val firstActiveNodeUrl = getFirstActiveNode()?.url ?: availableNodes.first().url.also(onSelectedNodeChange)

        socketService.start(firstActiveNodeUrl, remainPaused = true)
    }

    private fun getFirstActiveNode() = availableNodes.firstOrNull { it.isActive }

    fun considerUpdateNodes(nodes: List<Chain.Node>) {
        if (nodes.isEmpty()) {
            return
        }
        if (nodes != availableNodes) { //  List equals() first checks for referential equality, so there will be no O(n) check if the chain is the same
            val lastActiveNode = getFirstActiveNode()
            availableNodes = nodes
            val newActiveNode = getFirstActiveNode()

            if (newActiveNode != null && lastActiveNode != newActiveNode) {
                socketService.switchUrl(newActiveNode.url)
            }
        }
    }

    fun finish() {
        cancel()

        socketService.stop()
    }

    private fun autoBalance(currentState: State) {
        if (currentState is State.WaitingForReconnect && (currentState.attempt % NODE_SWITCHING_FREQUENCY) == 0) {
            val currentNodeIndex = availableNodes.indexOfFirst { it.isActive }
            //if current selected node is the last, start from first node
            val nextNodeIndex = (currentNodeIndex + 1).let { newIndex -> if (newIndex >= availableNodes.size) 0 else newIndex }
            val nextNode = availableNodes[nextNodeIndex]

            socketService.switchUrl(nextNode.url)
            onSelectedNodeChange(nextNode.url)
        }
    }
}
