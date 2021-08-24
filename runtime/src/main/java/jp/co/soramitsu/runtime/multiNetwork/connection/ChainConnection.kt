package jp.co.soramitsu.runtime.multiNetwork.connection

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.networkStateFlow
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class ChainConnection(
    val socketService: SocketService,
    initialNodes: List<Chain.Node>,
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private var availableNodes: List<Chain.Node> = initialNodes

    val state = socketService.networkStateFlow()
        .stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = State.Disconnected)

    init {
        require(availableNodes.isNotEmpty()) {
            "Cannot start connection with no available nodes"
        }

        state.onEach(::autoBalance)
            .launchIn(this)

        socketService.start(availableNodes.first().url)
    }

    fun considerUpdateNodes(nodes: List<Chain.Node>) {
        if (nodes != availableNodes) { //  List equals() first checks for referential equality, so there will be no O(n) check if the chain is the same
            availableNodes = nodes

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
        // TODO auto-balance
    }
}
