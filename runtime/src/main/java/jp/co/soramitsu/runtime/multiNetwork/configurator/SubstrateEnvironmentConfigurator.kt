package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.shared_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubstrateEnvironmentConfigurator(
    private val connectionPool: ConnectionPool,
    private val runtimeProviderPool: RuntimeProviderPool,
    private val runtimeSyncService: RuntimeSyncService,
    private val runtimeSubscriptionPool: RuntimeSubscriptionPool,
    private val chainsRepository: ChainsRepository
) : ChainEnvironmentConfigurator {

    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun configure(chain: Chain) {
        require(chain.ecosystem == Ecosystem.Substrate || chain.ecosystem == Ecosystem.EthereumBased)

        val connection = connectionPool.getConnectionOrNull(chain.id)?.let {
            if (it.state.value is SocketStateMachine.State.Paused) {
                it.socketService.resume()
            }
            it
        } ?: connectionPool.setupConnection(chain) { chainId, newNodeUrl ->
            scope.launch {
                chainsRepository.notifyNodeSwitched(chainId, newNodeUrl)
            }
        }

        if (runtimeProviderPool.getRuntimeProviderOrNull(chain.id)?.getOrNull() != null) return

        if (connection.state.value !is SocketStateMachine.State.Connected) {
            connection.socketService.start(chain.nodes.first().url)
        }

        runtimeSubscriptionPool.setupRuntimeSubscription(chain, connection)
        runtimeSyncService.registerChain(chain)
        runtimeProviderPool.setupRuntimeProvider(chain)
    }
}

