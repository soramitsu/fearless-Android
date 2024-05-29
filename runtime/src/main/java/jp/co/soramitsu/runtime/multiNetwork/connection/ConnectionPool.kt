package jp.co.soramitsu.runtime.multiNetwork.connection

import javax.inject.Inject
import javax.inject.Provider
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.runtime.multiNetwork.ChainState
import jp.co.soramitsu.runtime.multiNetwork.ChainsStateTracker
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet


class ConnectionPool @Inject constructor(
    private val socketServiceProvider: Provider<SocketService>,
    private val externalRequirementFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
    private val nodesSettingsStorage: NodesSettingsStorage,
    private val networkStateService: NetworkStateService
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val poolFlow = MutableStateFlow<Map<String, ChainConnection>>(emptyMap())
    private val connectionWatcher = MutableStateFlow(Event(Unit))

    suspend fun awaitConnection(chainId: ChainId): ChainConnection {
        return poolFlow.map { it[chainId] }.filterNotNull().first()
    }

    fun getConnectionOrNull(chainId: ChainId): ChainConnection? = poolFlow.value[chainId]
    fun getConnectionOrThrow(chainId: ChainId): ChainConnection = poolFlow.value.getValue(chainId)

    fun setupConnection(
        chain: Chain,
        onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
    ): ChainConnection {
        var isNew = false

        val connection = poolFlow.updateAndGet { currentPool ->
            if (currentPool.containsKey(chain.id)) {
                currentPool
            } else {
                isNew = true

                val nodes = chain.nodes.map {
                    it.fillDwellirApiKey()
                }

                val newConnection = ChainConnection(
                    chain = chain,
                    socketService = socketServiceProvider.get(),
                    initialNodes = nodes,
                    externalRequirementFlow = externalRequirementFlow,
                    onSelectedNodeChange = { onSelectedNodeChange(chain.id, clearDwellirApiKey(it)) },
                    isAutoBalanceEnabled = { nodesSettingsStorage.getIsAutoSelectNodes(chain.id) }
                ).also { connection ->
                    connection.state.onEach { connectionState ->
                        val newState = when (connectionState) {
                            is SocketStateMachine.State.Connected -> ChainState.ConnectionStatus.Connected(connectionState.url)
                            is SocketStateMachine.State.Connecting -> ChainState.ConnectionStatus.Connecting(connectionState.url)
                            is SocketStateMachine.State.Disconnected -> ChainState.ConnectionStatus.Disconnected
                            is SocketStateMachine.State.Paused -> ChainState.ConnectionStatus.Paused(connectionState.url)
                            is SocketStateMachine.State.WaitingForReconnect -> ChainState.ConnectionStatus.Connecting(connectionState.url)
                        }

                        ChainsStateTracker.updateState(chain) { it.copy(connectionStatus = newState) }

                        when (connectionState) {
                            is SocketStateMachine.State.Connected -> networkStateService.notifyConnectionSuccess(chain.id)
                            is SocketStateMachine.State.WaitingForReconnect -> networkStateService.notifyConnectionProblem(chain.id)
                            else -> Unit
                        }
                    }.launchIn(this@ConnectionPool)
                }

                currentPool + (chain.id to newConnection)
            }
        }[chain.id]!!

        if (isNew) {
            connectionWatcher.tryEmit(Event(Unit))
        }

        if (connection.chain.nodes != chain.nodes) {
            connection.considerUpdateNodes(chain.nodes)
        }

        return connection
    }

    fun removeConnection(chainId: ChainId) {
        val connection = getConnectionOrNull(chainId)
        poolFlow.update { it - chainId }
        connection?.finish()
        connectionWatcher.tryEmit(Event(Unit))
        networkStateService.notifyConnectionSuccess(chainId)
    }
}
fun ChainNode.fillDwellirApiKey(): ChainNode {
    return copy(url = fillDwellirApiKey(url))
}

fun clearDwellirApiKey(url: String): String {
    val key = BuildConfig.FL_DWELLIR_API_KEY
    return if(url.lowercase().contains(key)){
        url.removeSuffix("/$key")
    } else {
        url
    }
}

fun fillDwellirApiKey(url: String): String {
    return if(url.lowercase().contains("dwellir")){
        "${url}/${BuildConfig.FL_DWELLIR_API_KEY}"
    } else {
        url
    }
}