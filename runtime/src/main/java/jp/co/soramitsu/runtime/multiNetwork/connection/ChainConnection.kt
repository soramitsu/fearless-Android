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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

private const val NODE_SWITCHING_FREQUENCY = 3 // switch node every n attempt
private const val ATTEMPT_THRESHOLD = 1

class ChainConnection(
    val chain: Chain,
    val socketService: SocketService,
    externalRequirementFlow: Flow<ExternalRequirement>,
    initialNodes: List<Chain.Node>,
    private val onSelectedNodeChange: (newNodeUrl: String) -> Unit,
    private val isAutoBalanceEnabled: () -> Boolean
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    enum class ExternalRequirement {
        ALLOWED, STOPPED, FORBIDDEN
    }

    private var availableNodes: List<Chain.Node> = initialNodes

    val state = socketService.networkStateFlow()
        .stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = State.Disconnected)
        .onEach {
            when (it) {
                is State.Connecting -> {
                    println("!!! [${chain.name}] state is Connecting, attempt = ${it.attempt}, url= ${it.url}")
                }
                is State.WaitingForReconnect -> {
                    println("!!! [${chain.name}] state is WaitingForReconnect, attempt = ${it.attempt}, url= ${it.url}")
                }
                is State.Connected -> {
                    println("!! [${chain.name}] state is Connected, url= ${it.url}")
                }
                State.Disconnected -> {
                    println("!!! [${chain.name}] state is Disconnected")
                }
                is State.Paused -> {
                    println("!!! [${chain.name}] state is Paused, url= ${it.url}")
                }
            }
        }

    val isConnected = state.map {
        it is State.Connected
    }.distinctUntilChanged()

    val isConnecting = state.map {
        when (it) {
            is State.Connecting -> it.attempt > ATTEMPT_THRESHOLD
            is State.WaitingForReconnect -> it.attempt > ATTEMPT_THRESHOLD
            else -> false
        }
    }.distinctUntilChanged()
        .onEach {
            println("!!! [${chain.name}] isConnecting = $it")
        }


//    val chainsConnecting = state.map {
//        when (it) {
//            is State.Connecting -> it.attempt > ATTEMPT_THRESHOLD
//            is State.WaitingForReconnect -> it.attempt > ATTEMPT_THRESHOLD
//            else -> false
//        }
//    }

    init {
        require(availableNodes.isNotEmpty()) {
            "Cannot start connection with no available nodes"
        }

        state.onEach(::autoBalance)
            .launchIn(this)

        externalRequirementFlow.onEach {
            if (it == ExternalRequirement.ALLOWED) {
                runCatching {
                    socketService.resume()
                }
            } else {
                socketService.pause()
            }
        }.launchIn(this)

        val firstActiveNodeUrl = getFirstActiveNode()?.url ?: availableNodes.first().url.also(onSelectedNodeChange)


        socketService.start(firstActiveNodeUrl)

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
        if (!isAutoBalanceEnabled()) return
        if (currentState !is State.Connected) {
            println("!!! chain = ${chain.name}: autoBalance, currentState = $currentState")
        }
        if (currentState is State.Connecting) {
            println("!!! chain = ${chain.name}: autoBalance, url = '${currentState.url}', attempt = ${currentState.attempt}")
        }
        if (currentState is State.WaitingForReconnect) {
            println("!!! chain = ${chain.name}: autoBalance, url = '${currentState.url}', attempt = ${currentState.attempt}")
        }
        if (currentState is State.WaitingForReconnect && (currentState.attempt % NODE_SWITCHING_FREQUENCY) == 0
//            && currentState.attempt > 0
        ) {
            println("!!! chain = ${chain.name}: autoBalance, reached NODE_SWITCHING_FREQUENCY ($NODE_SWITCHING_FREQUENCY) with attempt = ${currentState.attempt}")
            val currentNodeIndex = availableNodes.indexOfFirst { it.isActive }
            // if current selected node is the last, start from first node
            val nextNodeIndex = (currentNodeIndex + 1).let { newIndex -> if (newIndex >= availableNodes.size) 0 else newIndex }
            val nextNode = availableNodes[nextNodeIndex]
            socketService.switchUrl(nextNode.url)
            onSelectedNodeChange(nextNode.url)
        }
    }
}
