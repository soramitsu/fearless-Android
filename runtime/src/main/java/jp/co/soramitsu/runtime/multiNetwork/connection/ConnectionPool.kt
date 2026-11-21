package jp.co.soramitsu.runtime.multiNetwork.connection

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.runtime.multiNetwork.ChainState
import jp.co.soramitsu.runtime.multiNetwork.ChainsStateTracker
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChainOrNull
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A catalog node that fails this many connection attempts in a row is replaced while Auto select is on. */
internal const val NODE_SWITCH_FAILED_ATTEMPTS = 3

/** The pool owns one serial configuration path; state observers never acquire that lane. */
class ConnectionPool @Inject constructor(
    private val socketServiceProvider: Provider<SocketService>,
    private val externalRequirementFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
    private val chainDao: ChainDao,
    private val networkStateService: NetworkStateService,
    private val nodesSettingsStorage: NodesSettingsStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val serial = Mutex()
    private val desired = ConcurrentHashMap<String, Any>()
    private val configured = mutableMapOf<String, Any>()
    // The fresh read behind each chain's latest binding; automatic switches are admitted only against it.
    private val configuredReads = ConcurrentHashMap<String, EndpointMutationLane.RuntimeVersion>()
    private val poolFlow = MutableStateFlow<Map<String, ChainConnection>>(emptyMap())
    private val observers = mutableMapOf<String, Job>()

    init {
        externalRequirementFlow.onEach { requirement ->
            serial.withLock {
                // Read the current value, since a queued collector value may already be obsolete.
                val current = externalRequirementFlow.value
                if (current == requirement) poolFlow.value.values.forEach { it.applyExternalRequirement(current) }
            }
        }.launchIn(scope)
        chainDao.endpointMutationLane.runtimeChanges.onEach {
            serial.withLock {
                // Only existing demanded connections are refreshed. A failed Select grants no demand.
                for ((id, connection) in poolFlow.value) {
                    val command = configured[id] ?: continue
                    if (!connection.hasCurrentRuntimeBinding()) refreshRuntime(id, command, connection)
                }
            }
        }.launchIn(scope)
    }

    suspend fun awaitConnection(chainId: ChainId): ChainConnection =
        poolFlow.map { it[chainId] }.filterNotNull().first()

    /** Observation never creates connection demand or starts transport. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun runtimeStatus(chainId: ChainId): Flow<EndpointRuntimeStatus?> = poolFlow.flatMapLatest { entries ->
        entries[chainId]?.runtime ?: flowOf(null)
    }

    fun getConnectionOrNull(chainId: ChainId): ChainConnection? = poolFlow.value[chainId]
    fun getConnectionOrThrow(chainId: ChainId): ChainConnection = poolFlow.value.getValue(chainId)

    /** The caller requests a chain ID. Captured caller nodes never become transport authority. */
    suspend fun setupConnection(chain: Chain): ChainConnection {
        val command = Any()
        desired[chain.id] = command
        try {
            return serial.withLock {
                check(desired[chain.id] === command) { "Runtime configuration was superseded" }
                val read = chainDao.readEndpointRuntime(chain.id)
                currentCoroutineContext().ensureActive()
                val fresh = read.value?.let(::mapChainLocalToChainOrNull)
                    ?: error("Runtime target is unavailable")
                require(fresh.id == chain.id && !fresh.isEthereumChain &&
                    (fresh.ecosystem == Ecosystem.Substrate || fresh.ecosystem == Ecosystem.EthereumBased)) {
                    "Runtime target no longer uses the Substrate transport"
                }
                check(desired[chain.id] === command && chainDao.endpointMutationLane.isCurrent(read.version)) {
                    "Runtime configuration was superseded"
                }
                val connection = poolFlow.value[chain.id] ?: ChainConnection(
                    chain = fresh,
                    socketService = socketServiceProvider.get(),
                    transportUrl = { fillDwellirApiKey(it.url) },
                    currentRequirement = { externalRequirementFlow.value }
                ).also { created ->
                    poolFlow.value = poolFlow.value + (chain.id to created)
                    observers[chain.id] = created.runtime.onEach { status ->
                        val logical = created.selectedNode
                        if (poolFlow.value[chain.id] !== created || logical == null || !status.isCurrentFor(logical)) return@onEach
                        val latest = created.chain as Chain
                        val newState = when (status.phase) {
                            EndpointRuntimeStatus.Phase.CONNECTED -> ChainState.ConnectionStatus.Connected(logical.url)
                            EndpointRuntimeStatus.Phase.PAUSED -> ChainState.ConnectionStatus.Paused(logical.url)
                            EndpointRuntimeStatus.Phase.CONNECTING -> ChainState.ConnectionStatus.Connecting(logical.url)
                            else -> ChainState.ConnectionStatus.Disconnected
                        }
                        ChainsStateTracker.updateState(latest) { it.copy(chain = latest, connectionStatus = newState) }
                        if (status.phase == EndpointRuntimeStatus.Phase.CONNECTED) networkStateService.notifyConnectionSuccess(chain.id)
                        else if (created.state.value is SocketStateMachine.State.WaitingForReconnect) {
                            networkStateService.notifyConnectionProblem(chain.id)
                        }
                        if (status.phase == EndpointRuntimeStatus.Phase.CONNECTING &&
                            status.failedAttempts >= NODE_SWITCH_FAILED_ATTEMPTS) {
                            switchUnreachableNode(chain.id, created, logical, status)
                        }
                    }.launchIn(scope)
                }
                configured[chain.id] = command
                // Before the binding exists, so none of its statuses can be paired with an older read.
                configuredReads[chain.id] = read.version
                connection.reconcile(fresh, externalRequirementFlow.value) {
                    desired[chain.id] === command && poolFlow.value[chain.id] === connection &&
                        chainDao.endpointMutationLane.isCurrent(read.version)
                }
                currentCoroutineContext().ensureActive()
                connection
            }
        } catch (error: Exception) {
            // Cancellation of a queued Mutex waiter still retires its own superseded connection.
            // A newer command wins before cleanup can acquire the pool and must remain untouched.
            scope.launch {
                serial.withLock {
                    if (desired[chain.id] === command) {
                        retireConnection(chain.id)
                        desired.remove(chain.id, command)
                    }
                }
            }
            throw error
        }
    }

    /** Called under the pool serial lane; database callbacks never call back into this pool. */
    private suspend fun refreshRuntime(chainId: ChainId, command: Any, connection: ChainConnection) {
        if (desired[chainId] !== command || poolFlow.value[chainId] !== connection) return
        try {
            val read = chainDao.readEndpointRuntime(chainId)
            currentCoroutineContext().ensureActive()
            if (desired[chainId] !== command || poolFlow.value[chainId] !== connection ||
                !chainDao.endpointMutationLane.isSettled(read.version)) return
            val fresh = read.value?.let(::mapChainLocalToChainOrNull)
            if (fresh == null || fresh.id != chainId || fresh.isEthereumChain ||
                (fresh.ecosystem != Ecosystem.Substrate && fresh.ecosystem != Ecosystem.EthereumBased)) {
                connection.markRuntimeUnavailable()
                return
            }
            configuredReads[chainId] = read.version
            connection.reconcile(fresh, externalRequirementFlow.value) {
                desired[chainId] === command && poolFlow.value[chainId] === connection &&
                    chainDao.endpointMutationLane.isCurrent(read.version)
            }
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (_: jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane.RuntimeReadSuperseded) {
            // The newer lane signal schedules a fresh read; keep the current peer until that decision.
        } catch (_: Exception) {
            if (desired[chainId] === command && poolFlow.value[chainId] === connection) {
                connection.markRuntimeUnavailable()
            }
        }
    }

    /**
     * Auto select moves a chain off a catalog node that keeps failing, to the next catalog node. The intent is
     * admitted only from the settled read that configured the failing binding, so it never retires a pending
     * explicit selection or catalog replacement, and any such intent admitted later supersedes it before it
     * commits. A custom node is the user's own choice and is never replaced.
     */
    private fun switchUnreachableNode(chainId: ChainId, connection: ChainConnection, node: ChainNode,
        status: EndpointRuntimeStatus) {
        if (!node.isDefault || connection.nodes.count { it.isDefault } < 2) return
        if (!nodesSettingsStorage.getIsAutoSelectNodes(chainId)) return
        val observed = configuredReads[chainId] ?: return
        // Still current after that read: no newer binding (and so no newer read) has replaced the failing one.
        if (!status.isCurrentFor(node)) return
        val lane = chainDao.endpointMutationLane
        // A newer intent between this check and admission leaves the read stale, so admission then fails too.
        if (!lane.isSettled(observed)) return
        val operation = lane.admitObserved(observed) ?: return
        scope.launch {
            try {
                chainDao.selectNextCatalogNode(operation, chainId, node.url) {
                    nodesSettingsStorage.getIsAutoSelectNodes(chainId)
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                // A superseded or failed switch changes nothing; the refreshed binding counts failures again.
            }
        }.invokeOnCompletion {
            // Also runs if the job is cancelled before it starts, so the intent never stays pending.
            lane.abandon(operation)
        }
    }

    private fun retireConnection(chainId: ChainId) {
        configured.remove(chainId)
        configuredReads.remove(chainId)
        val connection = poolFlow.value[chainId]
        poolFlow.value = poolFlow.value - chainId
        observers.remove(chainId)?.cancel()
        connection?.finish()
    }

    fun removeConnection(chainId: ChainId) {
        val removal = Any()
        desired[chainId] = removal
        scope.launch {
            serial.withLock {
                if (desired[chainId] !== removal) return@withLock
                retireConnection(chainId)
                desired.remove(chainId, removal)
                // Existing API clears a no-longer-demanded connection problem; this is not a Connected observation.
                networkStateService.notifyConnectionSuccess(chainId)
            }
        }
    }
}

fun ChainNode.fillDwellirApiKey(): ChainNode {
    return copy(url = fillDwellirApiKey(url))
}

/** Remove only the configured credential from an admitted Dwellir endpoint, preserving other URLs. */
fun clearDwellirApiKey(url: String): String = DwellirEndpointCredentials.clear(url, BuildConfig.FL_DWELLIR_API_KEY)

/** Decorate an admitted Dwellir WSS origin; arbitrary custom URLs remain byte-exact. */
fun fillDwellirApiKey(url: String): String = DwellirEndpointCredentials.fill(url, BuildConfig.FL_DWELLIR_API_KEY)
