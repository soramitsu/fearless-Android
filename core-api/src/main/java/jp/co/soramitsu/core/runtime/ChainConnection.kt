package jp.co.soramitsu.core.runtime

import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.socket.StateObserver
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * A pool-owned connection. The pool serializes configure/requirement/finish calls; its socket
 * observer only publishes immutable observations and never takes a pool or database writer lock.
 */
class ChainConnection(
    chain: IChain,
    val socketService: SocketService,
    private val transportUrl: (ChainNode) -> String,
    private val currentRequirement: () -> ExternalRequirement
) {
    enum class ExternalRequirement { ALLOWED, STOPPED, FORBIDDEN }

    @Volatile
    var chain: IChain = chain
        private set

    private class Binding(
        val node: ChainNode,
        val mappedUrl: String,
        val configuration: SocketService.Configuration,
        val current: () -> Boolean,
        val permission: ExternalRequirement
    )

    @Volatile private var binding: Binding? = null
    @Volatile private var retired = false
    @Volatile private var requirement = ExternalRequirement.FORBIDDEN
    private val nodesFlow = MutableStateFlow(chain.nodes.toList())
    private val stateFlow = MutableStateFlow<SocketStateMachine.State>(SocketStateMachine.initialState())
    val state: StateFlow<SocketStateMachine.State> = stateFlow
    private val runtimeFlow = MutableStateFlow(status(EndpointRuntimeStatus.Phase.NOT_IN_USE))
    val runtime: StateFlow<EndpointRuntimeStatus> = runtimeFlow
    val isConnected: Flow<Unit> = runtimeFlow
        .filter { it.phase == EndpointRuntimeStatus.Phase.CONNECTED && selectedNode?.let(it::isCurrentFor) == true }
        .map { Unit }

    private val observer: StateObserver = {
        publish(socketService.configurationSnapshot())
    }

    init {
        socketService.addStateObserver(observer)
    }

    val nodes: List<ChainNode> get() = nodesFlow.value
    val selectedNode: ChainNode? get() = nodesFlow.value.filter { it.isActive }.singleOrNull()

    /** Only a fresh lane-owned database snapshot may supply [isCurrent]. */
    fun reconcile(snapshot: IChain, externalRequirement: ExternalRequirement, isCurrent: () -> Boolean) {
        check(!retired) { "Chain connection was retired" }
        require(snapshot.id == chain.id) { "Chain connection target differs" }
        if (!isCurrent()) return
        chain = snapshot
        nodesFlow.value = snapshot.nodes.toList()
        requirement = externalRequirement
        val node = selectedNode
        if (node == null) {
            binding = null
            socketService.stop()
            stateFlow.value = SocketStateMachine.State.Disconnected
            runtimeFlow.value = status(EndpointRuntimeStatus.Phase.UNAVAILABLE)
            return
        }
        val mapped = transportUrl(node)
        val configuration = socketService.admitConfiguration {
            !retired && isCurrent() && currentRequirement() == externalRequirement
        }
        val next = Binding(node, mapped, configuration, isCurrent, externalRequirement)
        binding = next
        runtimeFlow.value = status(EndpointRuntimeStatus.Phase.PENDING, next)
        val applied = socketService.configure(configuration, mapped, externalRequirement == ExternalRequirement.ALLOWED)
        if (applied != null) publish(applied)
    }

    /** Consumes the global requirement without recursively writing to that flow. */
    fun applyExternalRequirement(externalRequirement: ExternalRequirement) {
        if (retired) return
        requirement = externalRequirement
        val current = binding ?: return
        // A replayed permission never becomes a stop command for a newly retired logical binding.
        // Reconcile performs its own currency check, including if an intent changes between these reads.
        if (current.permission == externalRequirement) {
            val observed = runtimeFlow.value
            if (!observed.applied || !observed.isCurrentFor(current.node)) {
                reconcile(chain, externalRequirement, current.current)
            }
            return
        }
        if (!current.current()) {
            socketService.pause()
            runtimeFlow.value = status(EndpointRuntimeStatus.Phase.PENDING, current)
            return
        }
        // A fresh configuration identity prevents a late prior-permission publication.
        reconcile(chain, externalRequirement, current.current)
    }

    /** Pure check for a pool wake-up; it is neither a transport acknowledgment nor a write grant. */
    fun hasCurrentRuntimeBinding(): Boolean = binding?.let { !retired && it.current() } == true

    /** Fresh storage could not supply a runtime target. Keep demand, but stop stale transport. */
    fun markRuntimeUnavailable() {
        if (retired) return
        binding = null
        nodesFlow.value = emptyList()
        socketService.stop()
        stateFlow.value = SocketStateMachine.State.Disconnected
        runtimeFlow.value = status(EndpointRuntimeStatus.Phase.UNAVAILABLE)
    }

    fun finish() {
        retired = true
        binding = null
        socketService.removeStateObserver(observer)
        socketService.stop()
        stateFlow.value = SocketStateMachine.State.Disconnected
        runtimeFlow.value = status(EndpointRuntimeStatus.Phase.NOT_IN_USE)
    }

    private fun publish(snapshot: SocketService.ConfigurationSnapshot) {
        val current = binding ?: return
        if (retired || snapshot.configuration !== current.configuration || !current.current() ||
            currentRequirement() != current.permission) return
        val state = snapshot.state
        val actual = when (state) {
            is SocketStateMachine.State.Connected -> state.url
            is SocketStateMachine.State.Connecting -> state.url
            is SocketStateMachine.State.WaitingForReconnect -> state.url
            is SocketStateMachine.State.Paused -> state.url
            SocketStateMachine.State.Disconnected -> null
        }
        val applied = snapshot.applied && actual == current.mappedUrl
        val phase = when {
            !applied -> EndpointRuntimeStatus.Phase.PENDING
            requirement != ExternalRequirement.ALLOWED -> EndpointRuntimeStatus.Phase.PAUSED
            state is SocketStateMachine.State.Connected -> EndpointRuntimeStatus.Phase.CONNECTED
            state is SocketStateMachine.State.Paused -> EndpointRuntimeStatus.Phase.PAUSED
            else -> EndpointRuntimeStatus.Phase.CONNECTING
        }
        if (applied) stateFlow.value = state
        runtimeFlow.value = status(phase, current, applied, snapshot)
    }

    private fun status(phase: EndpointRuntimeStatus.Phase, current: Binding? = null, applied: Boolean = false,
        observed: SocketService.ConfigurationSnapshot? = null) =
        EndpointRuntimeStatus(phase, applied, current?.node, current?.let { binding ->
            {
                !retired && this.binding === binding && binding.current() && currentRequirement() == binding.permission &&
                    socketService.configurationSnapshot().configuration === binding.configuration &&
                    (observed == null || observed.isCurrent())
            }
        })
}
