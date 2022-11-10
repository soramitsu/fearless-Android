package jp.co.soramitsu.runtime.multiNetwork.connection

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class ConnectionPool @Inject constructor(
    private val socketServiceProvider: Provider<SocketService>,
    private val externalRequirementFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
    private val nodesSettingsStorage: NodesSettingsStorage,
    private val networkStateMixin: NetworkStateMixin
) : NetworkStateUi by networkStateMixin, CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val pool = ConcurrentHashMap<String, ChainConnection>()
    private val connectionWatcher = MutableStateFlow(Event(Unit))

    private val connections = connectionWatcher.flatMapLatest {
        val connListFlow = pool.map {
            it.value.isConnecting.map { isConnecting ->
                it.value.chain.id to isConnecting
            }
        }
        val connChainsListFlow = combine(connListFlow) { chains ->
            chains.toMap()
        }
        connChainsListFlow
    }

    private val connectionIssues = connectionWatcher.flatMapLatest {
        val connListFlow = pool.map {
            it.value.isConnecting.map { isConnecting ->
                it.value.chain to isConnecting
            }
        }
        val connectionIssues = combine(connListFlow) { chains ->
            val issues = chains.filter { (_, isConnecting) -> isConnecting }.map { (chain, _) ->
                NetworkIssueItemState(
                    iconUrl = chain.icon,
                    title = chain.name,
                    type = when {
                        chain.nodes.size > 1 -> NetworkIssueType.Node
                        else -> NetworkIssueType.Network
                    },
                    chainId = chain.id,
                    chainName = chain.name,
                    assetId = chain.utilityAsset.id,
                    priceId = chain.utilityAsset.priceId
                )
            }
            issues
        }

        connectionIssues
    }

    private val showConnecting = connectionWatcher.flatMapLatest {
        val isConnectedListFlow = pool.map { it.value.isConnected }
        val hasConnectionsFlow = combine(isConnectedListFlow) { it.any { it } }

        val isConnectingListFlow = pool.map { it.value.isConnecting }
        val hasConnectingFlow = combine(isConnectingListFlow) { it.any { it } }
        val showConnecting = combine(hasConnectionsFlow, hasConnectingFlow) { connected, connecting ->
            !connected && connecting
        }
        showConnecting
    }
        .distinctUntilChanged()

    init {
        connections.onEach {
            networkStateMixin.updateChainConnection(it)
        }.launchIn(scope = this)

        connectionIssues.onEach {
            networkStateMixin.updateNetworkIssues(it)
        }.launchIn(this)

        showConnecting.onEach {
            networkStateMixin.updateShowConnecting(it)
        }.launchIn(this)
    }

    fun getConnection(chainId: ChainId): ChainConnection = pool.getValue(chainId)

    fun getConnectionOrNull(chainId: ChainId): ChainConnection? = pool.getOrDefault(chainId, null)

    fun setupConnection(chain: Chain, onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit): ChainConnection {
        var isNew = false
        val connection = pool.getOrPut(chain.id) {
            isNew = true
            ChainConnection(
                chain = chain,
                socketService = socketServiceProvider.get(),
                initialNodes = chain.nodes,
                externalRequirementFlow = externalRequirementFlow,
                onSelectedNodeChange = { onSelectedNodeChange(chain.id, it) },
                isAutoBalanceEnabled = { nodesSettingsStorage.getIsAutoSelectNodes(chain.id) }
            )
        }

        if (isNew) {
            connectionWatcher.tryEmit(Event(Unit))
        }

        connection.considerUpdateNodes(chain.nodes)

        return connection
    }

    fun removeConnection(chainId: ChainId) {
        pool.remove(chainId)?.apply { finish() }
        connectionWatcher.tryEmit(Event(Unit))
    }
}
