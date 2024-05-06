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

private const val ConnectingStatusDebounce = 750L

class ConnectionPool @Inject constructor(
    private val socketServiceProvider: Provider<SocketService>,
    private val externalRequirementFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
    private val nodesSettingsStorage: NodesSettingsStorage,
    private val networkStateService: NetworkStateService
) :  CoroutineScope by CoroutineScope(Dispatchers.Default) {

//    private val pool = ConcurrentHashMap<String, ChainConnection>()
    private val poolFlow = MutableStateFlow<Map<String, ChainConnection>>(emptyMap())
    private val connectionWatcher = MutableStateFlow(Event(Unit))

//    private val connectionIssues = connectionWatcher.flatMapLatest {
//        val connListFlow = pool.map {
//
//            it.value.isConnecting.map { isConnecting ->
//                it.value.chain to isConnecting
//            }
//        }
//        val connectionIssues = combine(connListFlow) { chains ->
//            val issues =
//                chains.filter { (_, isConnecting) -> isConnecting }.mapNotNull { (iChain, _) ->
//                    val chain = iChain as? Chain ?: return@mapNotNull null
//                    NetworkIssueItemState(
//                        iconUrl = chain.icon,
//                        title = chain.name,
//                        type = when {
//                            chain.nodes.size > 1 -> NetworkIssueType.Node
//                            else -> NetworkIssueType.Network
//                        },
//                        chainId = chain.id,
//                        chainName = chain.name,
//                        assetId = chain.utilityAsset?.id.orEmpty()
//                    )
//                }
//            issues
//        }
//
//        connectionIssues
//    }

//    private val showConnecting = connectionWatcher.flatMapLatest {
//        val isConnectedListFlow = pool.map { it.value.isConnected }
//        val hasConnectionsFlow = combine(isConnectedListFlow) { it.any { it } }
//
//        val isPausedListFlow = pool.map { it.value.isPaused }
//        val hasPausesFlow = combine(isPausedListFlow) { it.any { it } }
//
//        val isConnectingListFlow = pool.map { it.value.isConnecting }
//        val hasConnectingFlow = combine(isConnectingListFlow) { it.any { it } }
//            .filter { connecting -> connecting }
//        val showConnecting = combine(
//            hasConnectionsFlow,
//            hasConnectingFlow,
//            hasPausesFlow
//        ) { connected, connecting, paused ->
//            !(connected || paused) && connecting
//        }
//        showConnecting
//    }
//        .distinctUntilChanged()
//        .debounce(ConnectingStatusDebounce)


    suspend fun awaitConnection(chainId: ChainId): ChainConnection {
        return poolFlow.map { it[chainId] }.filterNotNull().first()
    }

    fun getConnectionOrNull(chainId: ChainId): ChainConnection? = poolFlow.value.getOrDefault(chainId, null)
    fun getConnectionOrThrow(chainId: ChainId): ChainConnection = poolFlow.value.getValue(chainId)

    fun setupConnection(
        chain: Chain,
        onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
    ): ChainConnection {
        var isNew = false

        val connection = poolFlow.value[chain.id] ?: run {
            isNew = true

            val nodes = chain.nodes.map {
                it.fillDwellirApiKey()
            }

            ChainConnection(
                chain = chain,
                socketService = socketServiceProvider.get(),
                initialNodes = nodes,
                externalRequirementFlow = externalRequirementFlow,
                onSelectedNodeChange = { onSelectedNodeChange(chain.id, clearDwellirApiKey(it)) },
                isAutoBalanceEnabled = { nodesSettingsStorage.getIsAutoSelectNodes(chain.id) }
            ).also {  connection ->
                poolFlow.update { pool ->
                    pool + (chain.id to connection)
                }
                connection.state.onEach {connectionState ->
                    val newState = when(connectionState) {
                        is SocketStateMachine.State.Connected -> ChainState.ConnectionStatus.Connected(connectionState.url)
                        is SocketStateMachine.State.Connecting -> ChainState.ConnectionStatus.Connecting(connectionState.url)
                        is SocketStateMachine.State.Disconnected -> ChainState.ConnectionStatus.Disconnected
                        is SocketStateMachine.State.Paused -> ChainState.ConnectionStatus.Paused(connectionState.url)
                        is SocketStateMachine.State.WaitingForReconnect -> ChainState.ConnectionStatus.Connecting(connectionState.url)
                    }

                    ChainsStateTracker.updateState(chain) { it.copy(connectionStatus = newState) }

                    when(connectionState) {
                        is SocketStateMachine.State.Connected -> networkStateService.notifyConnectionSuccess(chain.id)
                        is SocketStateMachine.State.WaitingForReconnect -> networkStateService.notifyConnectionProblem(chain.id)
                        else -> Unit
                    }
                }.launchIn(this)
            }
        }

        if (isNew) {
            connectionWatcher.tryEmit(Event(Unit))
        }

        connection.considerUpdateNodes(chain.nodes)

        return connection
    }

    fun removeConnection(chainId: ChainId) {
        val connection = getConnectionOrNull(chainId)
        poolFlow.update {

            it.minus(chainId)
        }
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