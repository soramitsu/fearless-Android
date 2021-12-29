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
import java.util.Queue
import java.util.LinkedList

private const val NODE_SWITCHING_FREQUENCY = 5 //switch node every n attempt

class ChainConnection(
    val socketService: SocketService,
    externalRequirementFlow: Flow<ExternalRequirement>,
    initialNodes: List<Chain.Node>,
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    enum class ExternalRequirement {
        ALLOWED, STOPPED, FORBIDDEN
    }

    private var availableNodes: List<Chain.Node> = initialNodes
    private var nodesStack: Queue<Chain.Node> = LinkedList<Chain.Node>().apply { addAll(initialNodes) }

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

        socketService.start(nodesStack.getNextNode().url, remainPaused = true)
    }

    fun considerUpdateNodes(nodes: List<Chain.Node>) {
        if (nodes.isEmpty()) {
            return
        }
        if (nodes != availableNodes) { //  List equals() first checks for referential equality, so there will be no O(n) check if the chain is the same
            availableNodes = nodes
            nodesStack.clear()
            nodesStack.addAll(nodes)
            if (state.value !is State.Connected) { // do not trigger reconnects and re-balancing if connection is OK
                autoBalance(state.value)
            }
        }
    }

    fun finish() {
        cancel()

        socketService.stop()
    }

    private fun autoBalance(currentState: State) {
        if (currentState is State.WaitingForReconnect && (currentState.attempt % NODE_SWITCHING_FREQUENCY) == 0) {
            val nextNode = nodesStack.getNextNode()
            socketService.switchUrl(nextNode.url)
        }
    }

    private fun Queue<Chain.Node>.getNextNode(): Chain.Node {
        if (this.isEmpty()) {
            addAll(availableNodes)
        }
        return poll()!!
    }
}
