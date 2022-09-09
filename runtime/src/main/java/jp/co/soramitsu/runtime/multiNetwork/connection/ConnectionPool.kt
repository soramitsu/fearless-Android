package jp.co.soramitsu.runtime.multiNetwork.connection

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.switchMap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Provider
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ConnectionPool @Inject constructor(
    private val socketServiceProvider: Provider<SocketService>,
    private val externalRequirementFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
    private val nodesSettingsStorage: NodesSettingsStorage,
    private val networkStateMixin: NetworkStateMixin
) : NetworkStateUi by networkStateMixin, CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val pool = ConcurrentHashMap<String, ChainConnection>()
    private val connectionWatcher = MutableLiveData<Unit>()

    init {
        connectionWatcher.switchMap {
            val isConnectedListFlow = pool.map { it.value.isConnected }
            val hasConnectionsFlow = combine(isConnectedListFlow) { it.any { it } }

            val isConnectingListFlow = pool.map { it.value.isConnecting }
            val hasConnectingFlow = combine(isConnectingListFlow) { it.any { it } }
            val showConnecting = combine(hasConnectionsFlow, hasConnectingFlow) { connected, connecting ->
                !connected && connecting
            }
            showConnecting.asLiveData(this)
        }
            .distinctUntilChanged()
            .observeForever {
                networkStateMixin.updateShowConnecting(it)
            }
    }

    fun getConnection(chainId: ChainId): ChainConnection = pool.getValue(chainId)

    fun getConnectionOrNull(chainId: ChainId): ChainConnection? = pool.getOrDefault(chainId, null)

    fun setupConnection(chain: Chain, onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit): ChainConnection {
        var isNew = false
        val connection = pool.getOrPut(chain.id) {
            isNew = true
            ChainConnection(
                socketService = socketServiceProvider.get(),
                initialNodes = chain.nodes,
                externalRequirementFlow = externalRequirementFlow,
                onSelectedNodeChange = { onSelectedNodeChange(chain.id, it) },
                isAutoBalanceEnabled = { nodesSettingsStorage.getIsAutoSelectNodes(chain.id) }
            )
        }

        if (isNew) {
            connectionWatcher.postValue(Unit)
        }

        connection.considerUpdateNodes(chain.nodes)

        return connection
    }

    fun removeConnection(chainId: ChainId) {
        pool.remove(chainId)?.apply { finish() }
        connectionWatcher.postValue(Unit)
    }
}
